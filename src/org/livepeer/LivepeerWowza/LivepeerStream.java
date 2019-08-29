package org.livepeer.LivepeerWowza;

import com.wowza.wms.application.IApplicationInstance;
import com.wowza.wms.logging.WMSLogger;
import com.wowza.wms.media.model.MediaCodecInfoVideo;
import com.wowza.wms.medialist.MediaList;
import com.wowza.wms.medialist.MediaListRendition;
import com.wowza.wms.medialist.MediaListSegment;
import com.wowza.wms.rest.AdvancedSetting;
import com.wowza.wms.rest.ConfigBase;
import com.wowza.wms.rest.ShortObject;
import com.wowza.wms.rest.WMSResponse;
import com.wowza.wms.rest.vhosts.applications.instances.incomingstreams.IncomingStreamConfig3;
import com.wowza.wms.rest.vhosts.applications.smilfiles.SMILFileAppConfig;
import com.wowza.wms.rest.vhosts.applications.streamfiles.StreamFileAppConfig;
import com.wowza.wms.rest.vhosts.applications.streamfiles.StreamFileAppConfigAdv;
import com.wowza.wms.rest.vhosts.applications.streamfiles.StreamFilesAppConfig;
import com.wowza.wms.rest.vhosts.smilfiles.SMILFileStreamConfig;
import com.wowza.wms.server.LicensingException;
import com.wowza.wms.stream.IMediaStream;
import com.wowza.wms.stream.MediaStreamMap;
import com.wowza.wms.stream.MediaStreamMapGroup;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Class for managing the lifecycle of a Livepeer stream. For example:
 * <p>
 * 1. Upon its construction, it creates a /api/stream object
 * 2. Once it gets that, it retrieves a /api/broadcaster and starts pushing with
 * PushPublishHTTPCupertinoLivepeerHandler
 * 3. It also handles creation of Stream Files for HLS pulls and SMIL files for ABR playlist creation
 * <p>
 * Upon the stream ending, it cleans up all of those things.
 */
public class LivepeerStream extends Thread {

    /**
     * Class for tracking the IMediaStream and MediaCodecInfoVideo of all of our streamFiles
     */
    class StreamFileInfo {
        IMediaStream stream;
        MediaCodecInfoVideo codecInfoVideo;

        StreamFileInfo(IMediaStream stream, MediaCodecInfoVideo codecInfoVideo) {
            this.stream = stream;
            this.codecInfoVideo = codecInfoVideo;
        }

        IMediaStream getStream() {
            return stream;
        }

        MediaCodecInfoVideo getCodecInfoVideo() {
            return codecInfoVideo;
        }
    }

    public final static String LIVEPEER_SUFFIX = "_livepeer";
    public final static int SMIL_CHECK_INTERVAL = 3000;
    public final static int START_STREAM_RETRY_INTERVAL = 3000;

    private static ConcurrentHashMap<String, LivepeerStream> livepeerStreams = new ConcurrentHashMap<>();

    private String id;
    private LivepeerAPI livepeer;
    private IMediaStream stream;
    private String streamName;
    private WMSLogger logger;
    private LivepeerAPIResourceBroadcaster broadcaster;
    private String vHostName;
    private String applicationName;
    private String appInstanceName;
    private String smilFileName;
    private LivepeerAPIResourceStream livepeerStream;
    private PushPublishHTTPCupertinoLivepeerHandler hlsPush;
    private Set<String> activeStreamFiles = new HashSet<>();
    private Map<String, StreamFileInfo> streamFileInfos = new HashMap<>();

    private Timer smilTimer;

    // These two flags control how we handle interrupts
    boolean isAcquiringBroadcaster = false;
    boolean isShuttingDown = false;

    /**
     * Given a URL, return the associated LivepeerStream instance. Returns null if this isn't a Livepeer URL.
     *
     * @param url url
     * @return LivepeerStream if one exists
     */
    public static LivepeerStream getFromUrl(String url) {
        String id = url.split("/")[0];
        return livepeerStreams.get(id);
    }

    /**
     * Given a `livepeer-${uuid}` string, return an actual HTTP url of a broadcaster. If it's passed
     * a non-Livepeer URL, it returns the string unchanged;
     *
     * @param url
     * @return
     */
    public static String rewriteUrl(String url) {
        LivepeerStream livepeerStream = LivepeerStream.getFromUrl(url);
        if (livepeerStream == null) {
            return url;
        }
        return livepeerStream.rewriteIdToUrl(url);
    }

    /**
     * Create a Livepeer stream. We'll need the stream, its name, and a LivepeerAPI instance
     *
     * @param stream     the stream
     * @param streamName its name
     * @param livepeer   LivepeerAPI instance
     */
    public LivepeerStream(IMediaStream stream, String streamName, LivepeerAPI livepeer) {
        this.stream = stream;
        this.streamName = streamName;
        this.livepeer = livepeer;
        this.logger = livepeer.getLogger();
    }

    @Override
    public void run() {
        startStreamRetry();
    }

    public synchronized void startStreamRetry() {
        while (true) {
            try {
                startStream();
                break;
            } catch (Exception e) {
                logger.info("LivepeerStream crashed during startStream(), retrying in " + START_STREAM_RETRY_INTERVAL + "ms.");
                logger.error(e);
                e.printStackTrace();
                this.fatalWait(START_STREAM_RETRY_INTERVAL);
            }
        }
    }

    /**
     * This stream has started. Try and fire it up!
     */
    public synchronized void startStream() throws IOException, LicensingException, ConfigBase.ConfigBaseException {
        logger.info(streamName + " start() started");

        IApplicationInstance appInstance = livepeer.getAppInstance();
        appInstanceName = appInstance.getName();
        vHostName = appInstance.getVHost().getName();
        applicationName = appInstance.getApplication().getName();

        // Create /api/stream
        livepeerStream = this.createStreamRetry();
        this.id = "livepeer-" + livepeerStream.getId();
        LivepeerStream.livepeerStreams.put(this.id, this);
        logger.info("created livepeerStreamId=" + livepeerStream.getId());

        // Pick broadcaster from the list at /api/broadcaster
        broadcaster = this.pickBroadcasterRetry();
        String ingestPath = this.id + "/live/" + livepeerStream.getId();

        // Start HLS pushing
        hlsPush = new PushPublishHTTPCupertinoLivepeerHandler(ingestPath, appInstance, this);
        hlsPush.setHttpClient(livepeer.getHttpClient());
        hlsPush.setAppInstance(appInstance);
        hlsPush.setSrcStreamName(streamName);
        hlsPush.setDstStreamName(streamName);
        hlsPush.init(appInstance, streamName, stream, new HashMap<String, String>(),
                new HashMap<String, String>(), null, true);
        hlsPush.connect();

        // Start HLS pulling via Stream Files
        this.startStreamFiles();
        this.startSmilFile();

        logger.info(streamName + " start() succeeded");

        // "Main Loop". Do nothing until there is a problem, then select a new broadcaster.
        while (true) {
            try {
                logger.info("LivepeerStream " + this.id + " before wait ");
                this.wait();
                logger.info("LivepeerStream " + this.id + " woke up!");
                if (isShuttingDown) {
                    // It's to shut down the stream. Cool.
                    isShuttingDown = true;
                    this.stopStream();
                    return;
                } else if (isAcquiringBroadcaster) {
                    // It's to get a new broadcaster. Cool.
                    broadcaster = this.pickBroadcasterRetry();
                    isAcquiringBroadcaster = false;
                }
            } catch (InterruptedException e) {
                logger.error("InterruptedException in main LivepeerStream");
                // Somebody woke us up. Why?
                this.stopStream();
                return;
            }
        }
    }

    /**
     * Try to create a stream forever
     */
    private LivepeerAPIResourceStream createStreamRetry() {
        while (true) {
            try {
                LivepeerAPIResourceStream livepeerStream = livepeer.createStreamFromApplication(vHostName, applicationName, streamName);
                logger.info("LIVEPEER: created stream " + livepeerStream.getId());
                return livepeerStream;
            } catch (Exception e) {
                logger.error("LivepeerStream crashed during createStreamRetry(), retrying in " + START_STREAM_RETRY_INTERVAL + "ms.");
                logger.error(e);
                this.fatalWait(START_STREAM_RETRY_INTERVAL);
            }
        }
    }

    /**
     * Try to select a random broadcaster from the server forever
     */
    private LivepeerAPIResourceBroadcaster pickBroadcasterRetry() {
        while (true) {
            try {
                LivepeerAPIResourceBroadcaster broadcaster = livepeer.getRandomBroadcaster();
                if (broadcaster != null) {
                    logger.info("LIVEPEER: picked broadcaster " + broadcaster.getAddress());
                    return broadcaster;
                } else {
                    logger.info("LIVEPEER: no broadcasters found");
                }
            } catch (Exception e) {
                logger.info("LivepeerStream errored during getRandomBroadcaster(), retrying in " + START_STREAM_RETRY_INTERVAL + "ms.");
                logger.error(e);
            }
            this.fatalWait(START_STREAM_RETRY_INTERVAL);
        }
    }

    public LivepeerAPIResourceBroadcaster getBroadcaster() {
        return this.broadcaster;
    }

    /**
     * Helper function for cases where the thread is sleeping and an interrupt means we should just self-destruct
     */
    private void fatalWait(long ms) {
        try {
            this.wait(ms);
        } catch (InterruptedException e) {
            this.stopStream();
        }
    }

    /**
     * Notify the LivepeerStream thread that there's a problem with a broadcaster and we should try and find a new one.
     *
     * @param problematicBroadcaster problematic broadcaster
     */
    public synchronized void notifyBroadcasterProblem(LivepeerAPIResourceBroadcaster problematicBroadcaster) {
        if (this.broadcaster != problematicBroadcaster) {
            // We already replaced them; we're good to go.
            return;
        }
        if (isAcquiringBroadcaster || isShuttingDown) {
            // We're either on our way out or already working to get a broadcaster. Either way, nothing to do.
            return;
        }
        // Okay, let the thread know it needs to find a new B.
        isAcquiringBroadcaster = true;
        this.notify();
    }

    /**
     * This stream has ended. Clean up everything that needs cleaning up.
     */
    public synchronized void stopStream() {
        // Disconnect HLS push
        hlsPush.disconnect();
        this.stopStreamFiles();
        this.stopSmilFile();
        this.stopStreamNameGroups();
        livepeerStreams.remove(this.id);
        // This flag being false implies this was an external call and we need to shut down the thread.
        if (!isShuttingDown) {
            isShuttingDown = true;
            this.notify();
        }
    }

    /**
     * Given a stream file name, return true if it's one of our transcoded renditions
     *
     * @param streamFileName stream file name
     * @return is this one of our transcoded renditions?
     */
    public boolean managesStreamFile(String streamFileName) {
        return this.activeStreamFiles.contains(streamFileName);
    }

    /**
     * Notify this LivepeerStream about a new rendition for it to handle
     * <p>
     * We don't know what kind of threads are going to trigger this event, so let's play
     * it safe with synchronized
     *
     * @param newStream new stream
     * @param newInfo   codec information
     */
    public synchronized void onStreamFileCodecInfoVideo(IMediaStream newStream, MediaCodecInfoVideo newInfo) {
        streamFileInfos.put(newStream.getName(), new StreamFileInfo(newStream, newInfo));
        this.updateSmilFile();
    }

    /**
     * Idempotent function to update our SMIL file from the streams we know about. If it detects any streams
     * that don't yet have bitrate data (they're reporting their bitrate as -1) it sets a timer to check
     * on them until we do.
     */
    public synchronized void updateSmilFile() {
        // Stream name groups and smilFiles have the same lifecycle, so we use this function as an entry point
        // and update both
        updateStreamNameGroups();
        SMILFileAppConfig smilFile = new SMILFileAppConfig(vHostName, applicationName, smilFileName);
        smilFile.loadObject();
        Set<String> activeSources = new HashSet<>();
        for (SMILFileStreamConfig streamConfig : smilFile.getStreams()) {
            activeSources.add(streamConfig.getSrc());
        }
        // Do we need to update the SMILFile?
        boolean needsUpdate = false;
        // Do we need to set a timer to wait until we have bitrate data?
        boolean needsTimer = false;
        for (StreamFileInfo info : streamFileInfos.values()) {
            IMediaStream stream = info.getStream();
            MediaCodecInfoVideo codecInfoVideo = info.getCodecInfoVideo();
            if (activeSources.contains(stream.getName())) {
                // It's already in the SMIL file, great!
                continue;
            }
            if (stream.getPublishBitrateVideo() == -1 || stream.getPublishBitrateAudio() == -1) {
                // We don't yet have a bitrate for this stream, wait till we do
                needsTimer = true;
                continue;
            }
            needsUpdate = true;
            SMILFileStreamConfig streamConfig = new SMILFileStreamConfig();
            streamConfig.setIdx(smilFile.getStreams().size());
            streamConfig.setSrc(stream.getName());
            streamConfig.setVideoBitrate("" + stream.getPublishBitrateVideo());
            streamConfig.setAudioBitrate("" + stream.getPublishBitrateAudio());
            // todo fixme xxx these are wrong, we should figure out how to get the right ones:
            // streamConfig.setVideoCodecId("" + stream.getPublishVideoCodecId());
            // streamConfig.setAudioCodecId("" + stream.getPublishAudioCodecId());
            streamConfig.setWidth("" + codecInfoVideo.getVideoWidth());
            streamConfig.setHeight("" + codecInfoVideo.getVideoHeight());
            streamConfig.setType("video");
            streamConfig.setSmilfileName(smilFileName);
            smilFile.getStreams().add(streamConfig);
        }
        // Only set a timer if one isn't already set, you dingus
        if (needsTimer && this.smilTimer == null) {
            smilTimer = new Timer();
            smilTimer.schedule(new UpdateSmilTask(), SMIL_CHECK_INTERVAL);
        }
        if (needsUpdate) {
            try {
                smilFile.saveObject();
                logger.info("Updated SMIL file " + smilFileName + " with renditions " + streamFileInfos.keySet());
            } catch (ConfigBase.ConfigBaseException e) {
                logger.info("Error updating SMIL file " + smilFileName + " with renditions " + streamFileInfos.keySet());
                e.printStackTrace();
            }
        }
    }

    /**
     * Task to update the SMIL timer
     */
    class UpdateSmilTask extends TimerTask {
        public void run() {
            logger.info("SMIL timer invoked");
            smilTimer.cancel();
            smilTimer = null;
            updateSmilFile();
        }
    }

    /**
     * Update our appropriate stream name groups from the relevant field in our stream object
     */
    public synchronized void updateStreamNameGroups() {
        for (LivepeerAPIResourceStream.LivepeerAPIResourceStreamWowzaStreamNameGroup streamNameGroup : livepeerStream.getWowza().getStreamNameGroups()) {
            String streamGroupName = streamNameGroup.getName();
            IApplicationInstance requiredAppInstance = livepeer.getAppInstance();
            MediaStreamMap streams = requiredAppInstance.getStreams();

            // Create a mapGroup object
            MediaStreamMapGroup thisMapGroup = streams.getNameGroupByGroupName(streamGroupName);
            if (thisMapGroup == null) {
                thisMapGroup = new MediaStreamMapGroup();
                thisMapGroup.setName(streamGroupName);
                streams.addNameGroup(thisMapGroup);
                logger.info("LIVEPEER Created stream name group: " + streamGroupName);
            }

            // Create a MediaList
            MediaList newList = new MediaList();

            // Create a segment list
            MediaListSegment newSegment = new MediaListSegment();

            List<String> addedNames = new ArrayList<>();

            for (StreamFileInfo info : streamFileInfos.values()) {
                IMediaStream stream = info.getStream();
                MediaCodecInfoVideo codecInfoVideo = info.getCodecInfoVideo();
                if (stream.getPublishBitrateVideo() == -1 || stream.getPublishBitrateAudio() == -1) {
                    // We don't yet have a bitrate for this stream, wait till we do
                    continue;
                }
                // Check to see if our stream name group is supposed to contain this rendition
                boolean found = false;
                for (String rendition : streamNameGroup.getRenditions()) {
                    // xxx todo this is fragile; not every Wowza stream necessarily follows this naming convention
                    if (stream.getName().equals(streamName + "_" + rendition + ".stream")) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    // This rendition isn't in our playlist.
                    continue;
                }
                // Create a Rendition entry - you will need multiple of these for each rendition you are adding
                MediaListRendition newRendition = new MediaListRendition();
                newRendition.setBitrateAudio(stream.getPublishBitrateAudio());
                newRendition.setBitrateVideo(stream.getPublishBitrateVideo());
                newRendition.setWidth(codecInfoVideo.getVideoWidth());
                newRendition.setHeight(codecInfoVideo.getVideoHeight());
                newRendition.setName(stream.getName());
                addedNames.add(stream.getName());

                // Add the rendtion(s) to the segment
                newSegment.addRendition(newRendition);
            }

            // Add the segment to the MediaList
            newList.addSegment(newSegment);

            logger.info("Updated stream name group " + streamGroupName + " with renditions " + addedNames);
            // Add the medialist to the mapgroup
            thisMapGroup.setMediaList(newList);
        }
    }

    /**
     * Sync this server's Stream Files to match what the server says
     */
    protected void startStreamFiles() {
        StreamFilesAppConfig streamFiles = new StreamFilesAppConfig(vHostName, applicationName);
        streamFiles.loadObject();
        Map<String, String> streamFilesMustExist = new HashMap<>();
        for (String renditionName : livepeerStream.getRenditions().keySet()) {
            streamFilesMustExist.put(streamName + "_" + renditionName, this.id + livepeerStream.getRenditions().get(renditionName));
        }
        logger.info("LIVEPEER ensuring these renditions exist: " + streamFilesMustExist);
        this.activeStreamFiles = streamFilesMustExist.keySet();
        for (ShortObject streamFileListItem : streamFiles.getStreamFiles()) {
            String id = streamFileListItem.getId();
            StreamFileAppConfig streamFile = new StreamFileAppConfig(vHostName, applicationName, id);
            streamFile.loadObject();
            String streamFileName = streamFile.getStreamfileName();
            if (streamFilesMustExist.containsKey(streamFileName)) {
                if (streamFilesMustExist.get(streamFile.getStreamfileName()) == streamFile.getUri()) {
                    logger.info("LIVEPEER found good existing streamFile: " + streamFile.getStreamfileName());
                    streamFilesMustExist.remove(streamFileName);
                } else {
                    logger.info("LIVEPEER found stale streamFile, deleting: " + streamFile.getStreamfileName());
                    streamFile.deleteObject();
                }
            }

        }
        logger.info("LIVEPEER creating stream files for renditions: " + streamFilesMustExist);
        for (String renditionName : streamFilesMustExist.keySet()) {
            // Create the streamfile
            StreamFileAppConfig streamFile = new StreamFileAppConfig();
            streamFile.setVhostName(vHostName);
            streamFile.setAppName(applicationName);
            streamFile.setStreamfileName(renditionName);
            streamFile.setUri(streamFilesMustExist.get(renditionName));
            streamFile.addToStringKeyMap("streamfileName", renditionName);
            streamFile.addToStringKeyMap("appName", applicationName);
            streamFile.addToStringKeyMap("vhostName", vHostName);
            try {
                streamFile.saveNewObject();
            } catch (ConfigBase.ConfigBaseException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
            logger.info("LIVEPEER created streamFile foo " + renditionName);

            StreamFileAppConfigAdv streamFileAdv = new StreamFileAppConfigAdv(vHostName, applicationName, renditionName);
            streamFileAdv.loadObject();
            AdvancedSetting s = streamFileAdv.getAdvSetting("CupertinoHLS", "cupertinoManifestMaxBufferBlockCount");
            s.setEnabled(true);
            s.setValue("35");
            s = streamFileAdv.getAdvSetting("CupertinoHLS", "cupertinoManifestBufferBlockCount");
            s.setEnabled(true);
            s.setValue("4");
            s = streamFileAdv.getAdvSetting("CupertinoHLS", "cupertinoAutoSegmentBuffer");
            s.setEnabled(true);
            s.setValue("false");
            try {
                streamFileAdv.saveObject();
            } catch (ConfigBase.ConfigBaseException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }

            // Connect the streamfile
            // This API takes a query string! Fun!
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("vhost", vHostName));
            params.add(new BasicNameValuePair("appName", applicationName));
            params.add(new BasicNameValuePair("appInstance", appInstanceName));
            params.add(new BasicNameValuePair("connectAppName", applicationName));
            params.add(new BasicNameValuePair("connectAppInstance", appInstanceName));
            params.add(new BasicNameValuePair("streamfileName", renditionName));
            params.add(new BasicNameValuePair("mediaCasterType", "applehls"));
            // This is the only one I'm unsure of...
            params.add(new BasicNameValuePair("appType", "live"));
            String queryParam = URLEncodedUtils.format(params, "UTF-8");
            WMSResponse response = streamFile.connectAction(queryParam);
            logger.info("LIVEPEER Message=" + response.getMessage());
            logger.info("LIVEPEER Data=" + response.getData());
            logger.info("LIVEPEER query param map: " + streamFile.getQueryParamMap());
        }
    }

    protected void stopStreamFiles() {
        // Clean up all stream files and currently-active streams
        for (String streamFileName : this.activeStreamFiles) {
            try {
                String incomingStreamName = streamFileName + ".stream";
                IncomingStreamConfig3 incomingStream = new IncomingStreamConfig3(vHostName, applicationName, appInstanceName, incomingStreamName);
                incomingStream.loadObject();
                incomingStream.disconnectStreamAction("");
                logger.info("LIVEPEER disconnected incoming stream: " + incomingStreamName);
            } catch (Exception e) {
                e.printStackTrace();
                logger.error("Error disconnecting incoming stream " + streamFileName + ": " + e.getMessage());
            }
            try {
                StreamFileAppConfig streamFile = new StreamFileAppConfig(vHostName, applicationName, streamFileName);
                streamFile.loadObject();
                streamFile.deleteObject();
                logger.info("LIVEPEER deleted stream file: " + streamFileName);
            } catch (Exception e) {
                e.printStackTrace();
                logger.error("Error deleting streamFile " + streamFileName + ": " + e.getMessage());
            }


        }
    }

    protected void startSmilFile() throws ConfigBase.ConfigBaseException {
        smilFileName = streamName + LIVEPEER_SUFFIX;
        SMILFileAppConfig smilFile = new SMILFileAppConfig();
        smilFile.setVhostName(vHostName);
        smilFile.setAppName(applicationName);
        smilFile.setSmilfileName(smilFileName);
        smilFile.addToStringKeyMap("smilfileName", smilFileName);
        smilFile.addToStringKeyMap("appName", applicationName);
        smilFile.addToStringKeyMap("vhostName", vHostName);
        smilFile.saveNewObject();
    }

    protected void stopSmilFile() {
        if (smilTimer != null) {
            smilTimer.cancel();
            smilTimer = null;
        }
        SMILFileAppConfig smilFile = new SMILFileAppConfig(vHostName, applicationName, smilFileName);
        smilFile.loadObject();
        smilFile.deleteObject();
    }

    protected void stopStreamNameGroups() {
        MediaStreamMap streams = livepeer.getAppInstance().getStreams();
        for (LivepeerAPIResourceStream.LivepeerAPIResourceStreamWowzaStreamNameGroup streamNameGroup : livepeerStream.getWowza().getStreamNameGroups()) {
            MediaStreamMapGroup thisMapGroup = streams.getNameGroupByGroupName(streamNameGroup.getName());
            if (thisMapGroup != null)
            {
                logger.info("LIVEPEER removing stream name group " + streamNameGroup.getName());
                streams.removeNameGroup(thisMapGroup);
            }
        }
    }

    /**
     * Given a `livepeer-${uuid} string, rewrite it to actually reflect the broadcaster address
     *
     * @param url input url
     * @return rewritten url
     */
    public String rewriteIdToUrl(String url) {
        return url.replaceFirst(this.id, broadcaster.getAddress());
    }

}
