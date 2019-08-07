package org.livepeer.LivepeerWowza;

import com.wowza.wms.logging.WMSLogger;
import com.wowza.wms.stream.*;
import com.wowza.wms.stream.livetranscoder.*;
import com.wowza.wms.module.*;

import java.util.*; 

import com.wowza.wms.amf.AMFPacket;
import com.wowza.wms.application.*;
import com.wowza.wms.media.model.MediaCodecInfoAudio;
import com.wowza.wms.media.model.MediaCodecInfoVideo;

/**
 * Livepeer Wowza module. It's designed to be a drop-in replacement for Wowza's
 * transcoding services, offloading everything to a hosted Livepeer API network.
 */
public class ModuleLivepeerWowza extends ModuleBase {
	class StreamListener implements IMediaStreamActionNotify3 {
		public void onMetaData(IMediaStream stream, AMFPacket metaDataPacket) {
			System.out.println("onMetaData[" + stream.getContextStr() + "]: " + metaDataPacket.toString());
		}

		public void onPauseRaw(IMediaStream stream, boolean isPause, double location) {
			System.out.println("onPauseRaw[" + stream.getContextStr() + "]: isPause:" + isPause + " location:" + location);
		}

		public void onPause(IMediaStream stream, boolean isPause, double location) {
			System.out.println("onPause[" + stream.getContextStr() + "]: isPause:" + isPause + " location:" + location);
		}

		public void onPlay(IMediaStream stream, String streamName, double playStart, double playLen, int playReset) {
			System.out.println("onPlay[" + stream.getContextStr() + "]: playStart:" + playStart + " playLen:" + playLen
					+ " playReset:" + playReset);
		}

		public void onPublish(IMediaStream stream, String streamName, boolean isRecord, boolean isAppend) {
			try {
				WMSLogger logger = getLogger();
				logger.info("LivepeerWowza initalizing stream " + streamName);
				String vHostName = _appInstance.getVHost().getName();
				String applicationName = _appInstance.getApplication().getName();
				LivepeerAPIResourceStream livepeerStream = livepeer.createStreamFromApplication(vHostName, applicationName);
				System.out.println("livepeerStreamId="+livepeerStream.getId());
				List<LivepeerAPI.LivepeerAPIResourceBroadcaster> broadcasters = livepeer.getBroadcasters();
				Random rand = new Random();
				LivepeerAPI.LivepeerAPIResourceBroadcaster broadcaster = broadcasters.get(rand.nextInt(broadcasters.size()));
				System.out.println("LIVEPEER: picked broadcaster " + broadcaster.getAddress());

				String ingestPath = broadcaster.getAddress() + "/live/" + livepeerStream.getId();
				logger.info("livepeer ingest path: " + ingestPath);

				PushPublishHTTPCupertinoLivepeerHandler http = new PushPublishHTTPCupertinoLivepeerHandler(ingestPath);

				http.setHttpClient(livepeer.getHttpClient());
				http.setAppInstance(_appInstance);
				http.setSrcStreamName(streamName);
				http.setDstStreamName(streamName);

				http.init(_appInstance, streamName, stream, new HashMap<String, String>(),
						new HashMap<String, String>(), null, true);
				http.connect();

				livepeerStream.ensureStreamFiles(streamName, broadcaster.getAddress(), vHostName, applicationName, _appInstance.getName());

				System.out.println("LIVEPEER onPublish end");

			} catch (Exception e) {
				e.printStackTrace();
				System.out.println("LIVEPEER HTTP: " + e);
			}
		}

		public void onSeek(IMediaStream stream, double location) {
			System.out.println("onSeek[" + stream.getContextStr() + "]: location:" + location);
		}

		public void onStop(IMediaStream stream) {
			System.out.println("onStop[" + stream.getContextStr() + "]: ");
		}

		public void onUnPublish(IMediaStream stream, String streamName, boolean isRecord, boolean isAppend) {
			System.out.println("onUnPublish[" + stream.getContextStr() + "]: streamName:" + streamName + " isRecord:"
					+ isRecord + " isAppend:" + isAppend);
		}

		public void onCodecInfoAudio(IMediaStream stream, MediaCodecInfoAudio codecInfoAudio) {
			System.out.println(
					"onCodecInfoAudio[" + stream.getContextStr() + " Audio Codec" + codecInfoAudio.toCodecsStr() + "]: ");
		}

		public void onCodecInfoVideo(IMediaStream stream, MediaCodecInfoVideo codecInfoVideo) {
			System.out.println(
					"onCodecInfoVideo[" + stream.getContextStr() + " Video Codec" + codecInfoVideo.toCodecsStr() + "]: ");
		}
	}

	private IApplicationInstance _appInstance;
	private LivepeerAPI livepeer;

	class TranscoderControl implements ILiveStreamTranscoderControl {
		public boolean isLiveStreamTranscode(String transcoder, IMediaStream stream) {
			// No transcoding, Livepeer is gonna take care of it
			return false;
		}
	}

	public void onAppStart(IApplicationInstance appInstance) {
		System.out.println("LIVEPEER onAppStart");
		_appInstance = appInstance;
		livepeer = new LivepeerAPI(appInstance);
		livepeer.setLogger(getLogger());
		appInstance.setLiveStreamTranscoderControl(new TranscoderControl());
	}

	public void onStreamCreate(IMediaStream stream) {
		// TODO FIXME this was intended to ignore our transcoded streams to avoid an
		// infinite loop. instead, it ignores all local streams, including stream files
		// and such. need to be more careful and only ignore transcoded renditions.
		if (stream.getClientId() == -1) {
			getLogger().info("Ignoring local stream");
			return;
		}

		IMediaStreamActionNotify2 actionNotify = new StreamListener();

		WMSProperties props = stream.getProperties();
		synchronized (props) {
			props.put("streamActionNotifier", actionNotify);
		}
		_appInstance.getMediaCasterProperties().setProperty("cupertinoChunkFetchClass", "org.livepeer.LivepeerWowza.LivepeerCupertinoMediaCasterChunkFetch");

		stream.addClientListener(actionNotify);
		getLogger().info("LIVEPEER onStreamCreate[" + stream + "]: clientId:" + stream.getClientId());
		getLogger().info("LIVEPEER onStreamCreate stream=" + stream.isPublisherStream());
	}

}
