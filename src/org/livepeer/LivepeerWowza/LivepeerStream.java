package org.livepeer.LivepeerWowza;

import com.wowza.wms.application.IApplicationInstance;
import com.wowza.wms.logging.WMSLogger;
import com.wowza.wms.rest.ConfigBase;
import com.wowza.wms.rest.ShortObject;
import com.wowza.wms.rest.WMSResponse;
import com.wowza.wms.rest.vhosts.applications.streamfiles.StreamFileAppConfig;
import com.wowza.wms.rest.vhosts.applications.streamfiles.StreamFilesAppConfig;
import com.wowza.wms.server.LicensingException;
import com.wowza.wms.stream.IMediaStream;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;

import java.io.IOException;
import java.util.*;

/**
 * Class for managing the lifecycle of a Livepeer stream. For example:
 *
 * 1. Upon its construction, it creates a /api/stream object
 * 2. Once it gets that, it retrieves a /api/broadcaster and starts pushing with
 * PushPublishHTTPCupertinoLivepeerHandler
 * 3. It also handles creation of Stream Files for HLS pulls and SMIL files for ABR playlist creation
 *
 * Upon the stream ending, it cleans up all of those things.
 */
public class LivepeerStream {
    private LivepeerAPI livepeer;
    private IMediaStream stream;
    private String streamName;
    private WMSLogger logger;
    private LivepeerAPIResourceBroadcaster broadcaster;
    private String vHostName;
    private String applicationName;
    private String appInstanceName;
    private LivepeerAPIResourceStream livepeerStream;

    public LivepeerStream(IMediaStream stream, String streamName, LivepeerAPI livepeer) {
        this.stream = stream;
        this.streamName = streamName;
        this.livepeer = livepeer;
        this.logger = livepeer.getLogger();
    }

    /**
     * This stream has started. Try and fire it up!
     */
    public void start() throws IOException, LicensingException {
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
        PushPublishHTTPCupertinoLivepeerHandler hlsPush = new PushPublishHTTPCupertinoLivepeerHandler(ingestPath, appInstance);

        hlsPush.setHttpClient(livepeer.getHttpClient());
        hlsPush.setAppInstance(appInstance);
        hlsPush.setSrcStreamName(streamName);
        hlsPush.setDstStreamName(streamName);
        hlsPush.init(appInstance, streamName, stream, new HashMap<String, String>(),
                new HashMap<String, String>(), null, true);
        hlsPush.connect();

        // Start HLS pulling via Stream Files
        this.ensureStreamFiles();

        logger.info(streamName + " start() succeeded");
    }

    /**
     * This stream has ended. Clean up everything that needs cleaning up.
     */
    public void stop() {

    }

    /**
     * Sync this server's Stream Files to match what the server says
     */
    protected void ensureStreamFiles() {
        StreamFilesAppConfig streamFiles = new StreamFilesAppConfig(vHostName, applicationName);
        streamFiles.loadObject();
        Map<String, String> streamFilesMustExist = new HashMap<>();
        for (String renditionName : livepeerStream.getRenditions().keySet()) {
            streamFilesMustExist.put(streamName + "_" + renditionName, broadcaster.getAddress() + livepeerStream.getRenditions().get(renditionName));
        }
        logger.info("LIVEPEER ensuring these renditions exist: " + streamFilesMustExist);
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
}
