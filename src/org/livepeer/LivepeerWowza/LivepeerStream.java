package org.livepeer.LivepeerWowza;

import com.wowza.wms.application.IApplicationInstance;
import com.wowza.wms.logging.WMSLogger;
import com.wowza.wms.media.model.MediaCodecInfoVideo;
import com.wowza.wms.rest.ConfigBase;
import com.wowza.wms.rest.ShortObject;
import com.wowza.wms.rest.WMSResponse;
import com.wowza.wms.rest.vhosts.applications.instances.incomingstreams.IncomingStreamConfig3;
import com.wowza.wms.rest.vhosts.applications.smilfiles.SMILFileAppConfig;
import com.wowza.wms.rest.vhosts.applications.streamfiles.StreamFileAppConfig;
import com.wowza.wms.rest.vhosts.applications.streamfiles.StreamFilesAppConfig;
import com.wowza.wms.rest.vhosts.smilfiles.SMILFileStreamConfig;
import com.wowza.wms.server.LicensingException;
import com.wowza.wms.stream.IMediaStream;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;

import java.io.IOException;
import java.util.*;

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

    public LivepeerStream(IMediaStream stream, String streamName, LivepeerAPI livepeer) {
        this.stream = stream;
        this.streamName = streamName;
        this.livepeer = livepeer;
        this.logger = livepeer.getLogger();
    }

    /**
     * This stream has started. Try and fire it up!
     */
    public void start() throws IOException, LicensingException, ConfigBase.ConfigBaseException {
        logger.info(streamName + " start() started");

        IApplicationInstance appInstance = livepeer.getAppInstance();
        appInstanceName = appInstance.getName();
        vHostName = appInstance.getVHost().getName();
        applicationName = appInstance.getApplication().getName();

        // Create /api/stream
        livepeerStream = livepeer.createStreamFromApplication(vHostName, applicationName);
        logger.info("created livepeerStreamId=" + livepeerStream.getId());

        // Pick broadcaster from the list at /api/broadcaster
        broadcaster = livepeer.getRandomBroadcaster();
        logger.info("LIVEPEER: picked broadcaster " + broadcaster.getAddress());
        String ingestPath = broadcaster.getAddress() + "/live/" + livepeerStream.getId();
        logger.info("livepeer ingest path: " + ingestPath);

        // Start HLS pushing
        hlsPush = new PushPublishHTTPCupertinoLivepeerHandler(ingestPath, appInstance);

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
    }

    /**
     * This stream has ended. Clean up everything that needs cleaning up.
     */
    public void stop() {
        // Disconnect HLS push
        hlsPush.disconnect();
        this.stopStreamFiles();
        this.stopSmilFile();
    }

    /**
     * Given a stream file name, return true if it's one of our transcoded renditions
     * @param streamFileName stream file name
     * @return is this one of our transcoded renditions?
     */
    public boolean managesStreamFile(String streamFileName) {
        return this.activeStreamFiles.contains(streamFileName);
    }

    /**
     * Notify this LivepeerStream about a new rendition for it to handle
     *
     * We don't know what kind of threads are going to trigger this event, so let's play
     * it safe with synchronized
     * @param newStream new stream
     * @param newInfo codec information
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
            streamConfig.setVideoCodecId("" + stream.getPublishVideoCodecId());
            streamConfig.setAudioCodecId("" + stream.getPublishAudioCodecId());
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
     * Sync this server's Stream Files to match what the server says
     */
    protected void startStreamFiles() {
        StreamFilesAppConfig streamFiles = new StreamFilesAppConfig(vHostName, applicationName);
        streamFiles.loadObject();
        Map<String, String> streamFilesMustExist = new HashMap<>();
        for (String renditionName : livepeerStream.getRenditions().keySet()) {
            streamFilesMustExist.put(streamName + "_" + renditionName, broadcaster.getAddress() + livepeerStream.getRenditions().get(renditionName));
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
}
