package org.livepeer.LivepeerWowza;

import com.wowza.wms.amf.AMFPacket;
import com.wowza.wms.application.IApplicationInstance;
import com.wowza.wms.logging.WMSLogger;
import com.wowza.wms.manifest.model.m3u8.MediaSegmentModel;
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
import com.wowza.wms.stream.publish.Publisher;
import com.wowza.wms.vhost.IVHost;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

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
public class LivepeerStream {
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
    public final static long START_STREAM_RETRY_INTERVAL = 3000;
    public final static int HLS_BUFFER_SIZE = 5;

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
    private MediaCodecInfoVideo codecInfoVideo;
    private LivepeerAPIResourceStream livepeerStream;
    private PushPublishHTTPCupertinoLivepeerHandler hlsPush;
    private Set<String> activeStreamFiles = new HashSet<>();
    private Map<String, StreamFileInfo> streamFileInfos = new HashMap<>();
    private Map<String, Publisher> duplicateStreamPublishers = new HashMap<String, Publisher>();
    private ScheduledExecutorService executorService = Executors.newScheduledThreadPool(10);
    private boolean shouldDuplicateStreams;
    // Best concurrent-with-sorted-keys map I could find
    private Map<Integer, LivepeerSegment> segments = new ConcurrentSkipListMap<>();
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
     * Retrieve a LivepeerStream by its Livepeer API ID (not to be confused with Wowza stream name)
     *
     * @param id id
     * @return LivepeerStream if one exists
     */
    public static LivepeerStream getFromId(String id) {
        return livepeerStreams.get(id);
    }

  /**
   * Get ExecutorService used for thread pool
   * @return
   */
    public ScheduledExecutorService getExecutorService() {
        return executorService;
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
        this.shouldDuplicateStreams = this.livepeer.getProps().getDuplicateStreams();
    }

    /**
     * External signal that we should start streaming
     */
    public synchronized void startStreamRetry() {
        logger.info("canonical-log-line function=startStreamRetry worker=true phase=start");
        this._startStreamRetry();
        logger.info("canonical-log-line function=startStreamRetry worker=true phase=end");
    }

    /**
     * Function called to start trying to stream into Liveper forever.
     */
    private synchronized void _startStreamRetry() {
        // Note: this check might be unnecessary, but I'm being extra-cautious regarding relying on
        // ScheduledExecutorService's shutdown behavior. Just in case jobs didn't all get insta-terminated
        // in stopStream, we include checks for async actions to do nothing if we're shutting down.
        if (isShuttingDown) {
            return;
        }
        try {
            startStream();
        } catch (Exception e) {
            logger.info("LivepeerStream crashed during startStream(), retrying in " + START_STREAM_RETRY_INTERVAL + "ms.");
            logger.error(e);
            e.printStackTrace();
            this.getExecutorService().schedule(this::_startStreamRetry, START_STREAM_RETRY_INTERVAL, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * This stream has started. Try and fire it up!
     */
    public synchronized void startStream() throws IOException, LicensingException, ConfigBase.ConfigBaseException {
        logger.info(streamName + "canonical-log-line function=startStream phase=start");

        IApplicationInstance appInstance = livepeer.getAppInstance();
        appInstanceName = appInstance.getName();
        vHostName = appInstance.getVHost().getName();
        applicationName = appInstance.getApplication().getName();

        // Create /api/stream
        if (this.livepeerStream == null) {
            livepeerStream = this.createStream();
        }
        this.id = livepeerStream.getId();
        LivepeerStream.livepeerStreams.put(this.id, this);
        logger.info("created livepeerStreamId=" + livepeerStream.getId());

        // Pick broadcaster from the list at /api/broadcaster
        if (this.broadcaster == null) {
            this.pickBroadcaster();
        }

        // Start HLS pushing
        hlsPush = new PushPublishHTTPCupertinoLivepeerHandler(appInstance, this);
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
        this.startDuplicateStreams();

        logger.info(streamName + "canonical-log-line function=startStream phase=end");
    }

    public byte[] getSegment(int mediaNum, String renditionName) {
        LivepeerSegment segment = this.segments.get(mediaNum);
        if (segment == null) {
          return null;
        }
        return segment.getRendition(renditionName);
    }

    /**
     * Try to create a stream forever
     */
    private LivepeerAPIResourceStream createStream() throws IOException {
        return livepeer.createStreamFromApplication(vHostName, applicationName, streamName, codecInfoVideo);
    }

    /**
     * Try to select a random broadcaster from the API server
     */
    private void pickBroadcaster() throws IOException {
        LivepeerAPIResourceBroadcaster broadcaster = livepeer.getRandomBroadcaster();
        if (broadcaster != null) {
            this.broadcaster = broadcaster;
        } else {
            throw new RuntimeException("no broadcasters found");
        }
    }

    /**
     * Keep trying to pick a broadcaster forever
     */
    private void pickBroadcasterRetry() {
        // If we already have a thread picking a new broadcaster, don't schedule another one
        if (this.isAcquiringBroadcaster) {
            return;
        }
        this.isAcquiringBroadcaster = true;
        this.getExecutorService().execute(this::_pickBroadcasterRetry);
    }

    /**
     * Async retry loop for pickBroadcasterRetry()
     */
    private void _pickBroadcasterRetry() {
        logger.info("canonical-log-line function=pickBroadcasterRetry() worker=true phase=start");
        if (isShuttingDown) {
            return;
        }
        try {
            this.pickBroadcaster();
            this.isAcquiringBroadcaster = false;
            logger.info("canonical-log-line function=pickBroadcasterRetry() worker=true phase=end");
        }
        catch (Exception e) {
            logger.error(streamName + "canonical-log-line function=pickBroadcasterRetry worker=true phase=error err=" + e.getMessage());
            this.getExecutorService().schedule(this::_pickBroadcasterRetry, START_STREAM_RETRY_INTERVAL, TimeUnit.MILLISECONDS);
        }
    }

    public LivepeerAPIResourceBroadcaster getBroadcaster() {
        return this.broadcaster;
    }

    /**
     * Notify the LivepeerStream thread that there's a problem with a broadcaster and we should try and find a new one.
     *
     * @param problematicBroadcaster problematic broadcaster
     */
    public synchronized void notifyBroadcasterProblem(LivepeerAPIResourceBroadcaster problematicBroadcaster) {
        if (isShuttingDown) {
            // We're on our way out. Nothing to do.
            return;
        }
        if (this.broadcaster != problematicBroadcaster) {
            // We already replaced them; we're good to go.
            return;
        }
        // Okay, let the stream know it needs to find a new B.
        this.pickBroadcasterRetry();
    }

    /**
     * This stream has ended. Clean up everything that needs cleaning up.
     */
    public synchronized void stopStream() {
        this.isShuttingDown = true;
        // Disconnect HLS push
        hlsPush.disconnect();
        this.stopStreamFiles();
        this.stopSmilFile();
        this.stopStreamNameGroups();
        this.stopDuplicateStreams();
        livepeerStreams.remove(this.id);
        // This flag being false implies this was an external call and we need to shut down the thread.
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(1000, TimeUnit.MILLISECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
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
        String streamName = newStream.getName();
        // We only care about the direct results from the Livepeer API for these, not the dupes
        if (!streamName.endsWith(".stream")) {
            return;
        }
        streamFileInfos.put(streamName, new StreamFileInfo(newStream, newInfo));
        this.updateSmilFile();
    }

    public void onPacket(IMediaStream stream, AMFPacket packet) {
        if (!shouldDuplicateStreams) {
            return;
        }
        String streamName = stream.getName();
        if (!streamName.endsWith(".stream")) {
            logger.error("LIVEPEER onPacket called for non-streamfile " + streamName);
            return;
        }
        streamName = streamName.substring(0, streamName.length() - 7);
        Publisher publisher = duplicateStreamPublishers.get(streamName);
        if (publisher == null) {
            logger.error("LIVEPEER couldn't find publisher for " + streamName);
            return;
        }
        switch (packet.getType())
        {
            case IVHost.CONTENTTYPE_AUDIO:
                publisher.addAudioData(packet.getData(), packet.getAbsTimecode());
                break;

            case IVHost.CONTENTTYPE_VIDEO:
                publisher.addVideoData(packet.getData(), packet.getAbsTimecode());
                break;

            case IVHost.CONTENTTYPE_DATA:
            case IVHost.CONTENTTYPE_DATA3:
                publisher.addDataData(packet.getData(), packet.getAbsTimecode());
        }
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
        for (StreamFileInfo info : streamFileInfos.values()) {
            IMediaStream stream = info.getStream();
            MediaCodecInfoVideo codecInfoVideo = info.getCodecInfoVideo();
            if (activeSources.contains(stream.getName())) {
                // It's already in the SMIL file, great!
                continue;
            }
            if (stream.getPublishBitrateVideo() == -1 || stream.getPublishBitrateAudio() == -1) {
                // We don't yet have a bitrate for this stream, wait till we do
                this.triggerSmilTimer();
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
     * Idempotent function to kick off the timer that waits until we have bitrate information
     */
    protected void triggerSmilTimer() {
        if (smilTimer != null) {
            return;
        }
        smilTimer = new Timer();
        smilTimer.schedule(new UpdateSmilTask(), SMIL_CHECK_INTERVAL);
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
                    this.triggerSmilTimer();
                    continue;
                }
                // Check to see if our stream name group is supposed to contain this rendition
                boolean found = false;
                for (String rendition : streamNameGroup.getRenditions()) {
                    if (stream.getName().equals(rendition + ".stream")) {
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
            streamFilesMustExist.put(renditionName, livepeer.getLivepeerHost() + livepeerStream.getRenditions().get(renditionName));
        }
        logger.debug("LIVEPEER ensuring these renditions exist: " + streamFilesMustExist);
        this.activeStreamFiles = streamFilesMustExist.keySet();
        for (ShortObject streamFileListItem : streamFiles.getStreamFiles()) {
            String id = streamFileListItem.getId();
            StreamFileAppConfig streamFile = new StreamFileAppConfig(vHostName, applicationName, id);
            streamFile.loadObject();
            String streamFileName = streamFile.getStreamfileName();
            if (streamFilesMustExist.containsKey(streamFileName)) {
                if (streamFilesMustExist.get(streamFile.getStreamfileName()) == streamFile.getUri()) {
                    logger.debug("LIVEPEER found good existing streamFile: " + streamFile.getStreamfileName());
                    streamFilesMustExist.remove(streamFileName);
                } else {
                    logger.debug("LIVEPEER found stale streamFile, deleting: " + streamFile.getStreamfileName());
                    streamFile.deleteObject();
                }
            }

        }
        logger.debug("LIVEPEER creating stream files for renditions: " + streamFilesMustExist);
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
            logger.debug("LIVEPEER created streamFile foo " + renditionName);

            StreamFileAppConfigAdv streamFileAdv = new StreamFileAppConfigAdv(vHostName, applicationName, renditionName);
            streamFileAdv.loadObject();
            AdvancedSetting s = streamFileAdv.getAdvSetting("CupertinoHLS", "cupertinoManifestMaxBufferBlockCount");
            s.setEnabled(true);
            s.setValue("50");
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

    /**
     * Start all duplicated streams; these exist for the purpose of Stream Targets and whatnot finding
     * the names that they expect
     */
    protected void startDuplicateStreams() {
        if (!shouldDuplicateStreams) {
            return;
        }
        IApplicationInstance appInstance = livepeer.getAppInstance();
        for (String renditionName : livepeerStream.getRenditions().keySet()) {
            Publisher publisher = Publisher.createInstance(appInstance.getVHost(), applicationName, appInstanceName);
            duplicateStreamPublishers.put(renditionName, publisher);
            publisher.setStreamType(stream.getStreamType());
            publisher.publish(renditionName);
            logger.info("LIVEPEER published duplicate stream: " + renditionName);
        }
    }

    protected void stopDuplicateStreams() {
        if (!shouldDuplicateStreams) {
            return;
        }
        for (String renditionName : livepeerStream.getRenditions().keySet()) {
            Publisher publisher = duplicateStreamPublishers.get(renditionName);
            if (publisher == null) {
                continue;
            }
            publisher.unpublish();
            logger.info("LIVEPEER unpublished duplicate stream: " + renditionName);
            duplicateStreamPublishers.remove(renditionName);
        }
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

    public MediaCodecInfoVideo getCodecInfoVideo() {
        return codecInfoVideo;
    }

    public void setCodecInfoVideo(MediaCodecInfoVideo codecInfoVideo) {
        this.codecInfoVideo = codecInfoVideo;
    }

    /**
     * Called by PushPublishHTTPCupertinoLivepeerHandler to handle new segments
     */
    public void newSegment(MediaSegmentModel mediaSegment) {
        LivepeerSegment livepeerSegment = new LivepeerSegment(mediaSegment, this);
        segments.put(livepeerSegment.getSequenceNumber(), livepeerSegment);
        livepeerSegment.uploadSegment();
    }

    public WMSLogger getLogger() {
        return logger;
    }

    public void setLogger(WMSLogger logger) {
        this.logger = logger;
    }

    public String getLivepeerId() {
        return this.livepeerStream.getId();
    }

    public HttpClient getHttpClient() {
        return livepeer.getHttpClient();
    }

    public List<LivepeerAPIResourceStream.Profile> getProfiles() {
        return this.livepeerStream.getProfiles();
    }

    public String getStreamId() {
        return this.id;
    }

    public String getManifest(String renditionName) {
        List<String> lines = new ArrayList();
        lines.add("#EXTM3U");
        lines.add("#EXT-X-VERSION:3");
        LivepeerSegment earliest = null;
        List<String> segmentLines = new ArrayList();
        for (LivepeerSegment segment : segments.values()) {
            if (!segment.isReady()) {
                break;
            }
            if (earliest == null) {
                earliest = segment;
            }
            long duration = segment.getDuration();
            int seq = segment.getSequenceNumber();
            String livepeerId = this.getLivepeerId();
            String durationStr = String.format("%.3f", (double) segment.getDuration() / 1000);
            segmentLines.add("#EXTINF:" + durationStr + ",");
            segmentLines.add("https://example.com/stream/" + livepeerId + "/" + renditionName + "/" + seq + ".ts");
        }
        int earliestSeq = 0;
        if (earliest != null) {
            earliestSeq = earliest.getSequenceNumber();
        }
        lines.add("#EXT-X-MEDIA-SEQUENCE:" + earliestSeq);
        lines.add("#EXT-X-TARGETDURATION:2");
        lines.addAll(segmentLines);
        return String.join("\n", lines);
    }

    /**
     * Called after an upload has succeeded; prunes old segments so we keep only 5, avoids
     * buffering all video forever
     */
    public void pruneSegments() {
        int earliest = -1;
        int latest = -1;
        for (LivepeerSegment segment : segments.values()) {
            if (!segment.isReady()) {
                break;
            }
            if (earliest == -1) {
                earliest = segment.getSequenceNumber();
            }
            latest = segment.getSequenceNumber();
        }
        // No content
        if (earliest == -1 || latest == -1) {
            return;
        }
        if (latest - earliest > HLS_BUFFER_SIZE) {
            int cutoff = latest - HLS_BUFFER_SIZE;
            for (int i = earliest; i < cutoff; i += 1) {
                segments.remove(i);
            }
        }
    }
}
