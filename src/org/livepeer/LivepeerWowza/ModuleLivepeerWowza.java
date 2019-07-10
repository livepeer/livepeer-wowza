package org.livepeer.LivepeerWowza;

import com.wowza.wms.stream.*;
import com.wowza.wms.stream.livetranscoder.*;
import com.wowza.wms.module.*;
import com.wowza.wms.pushpublish.protocol.rtmp.IPushPublishRTMPNotify;
import com.wowza.wms.pushpublish.protocol.rtmp.PushPublishRTMP;
import com.wowza.wms.pushpublish.protocol.rtmp.PushPublishRTMPNetConnectionSession;
import com.wowza.wms.request.RequestFunction;

import java.io.OutputStream;
import java.util.*;

import com.wowza.wms.amf.AMFDataList;
import com.wowza.wms.amf.AMFPacket;
import com.wowza.wms.application.*;
import com.wowza.wms.media.model.MediaCodecInfoAudio;
import com.wowza.wms.media.model.MediaCodecInfoVideo;

public class ModuleLivepeerWowza extends ModuleBase {
	private IApplicationInstance _appInstance;
	Map<IMediaStream, PushPublishRTMP> publishers = new HashMap<IMediaStream, PushPublishRTMP>();

	class RTMPListener implements IPushPublishRTMPNotify {

		@Override
		public void onAkamaiClientLogin(PushPublishRTMPNetConnectionSession pushPublisherSession,
				RequestFunction function, AMFDataList params) {
			System.out.println("LIVEPEER onAkamaiClientLogin");

		}

		@Override
		public void onAkamaiSetChallenge(PushPublishRTMPNetConnectionSession pushPublisherSession,
				RequestFunction function, AMFDataList params) {
			System.out.println("LIVEPEER onAkamaiSetChallenge");

		}

		@Override
		public void onAkamaiSetOriginConnectionInfo(PushPublishRTMPNetConnectionSession pushPublisherSession,
				RequestFunction function, AMFDataList params) {
			System.out.println("LIVEPEER onAkamaiSetOriginConnectionInfo");

		}

		@Override
		public void onConnect(PushPublishRTMPNetConnectionSession pushPublisherSession, RequestFunction function,
				AMFDataList params) {
			System.out.println("LIVEPEER onConnect");
			System.out.println(pushPublisherSession.getStreamsToPublish());

		}

		@Override
		public void onConnectFailure(PushPublishRTMPNetConnectionSession pushPublisherSession) {
			System.out.println("LIVEPEER onConnectFailure");
		}

		@Override
		public void onConnectStart(PushPublishRTMPNetConnectionSession pushPublisherSession) {
			System.out.println("LIVEPEER onConnectStart");
		}

		@Override
		public void onConnectSuccess(PushPublishRTMPNetConnectionSession pushPublisherSession) {
			System.out.println("LIVEPEER onConnectSuccess");
		}

		@Override
		public void onFCAnnounce(PushPublishRTMPNetConnectionSession pushPublisherSession, RequestFunction function,
				AMFDataList params) {
			System.out.println("LIVEPEER onFCAnnounce");

		}

		@Override
		public void onFCPublish(PushPublishRTMPNetConnectionSession pushPublisherSession, RequestFunction function,
				AMFDataList params) {
			System.out.println("LIVEPEER onFCPublish");

		}

		@Override
		public void onHandshakeResult(PushPublishRTMPNetConnectionSession pushPublisherSession,
				RequestFunction function, AMFDataList params) {
			System.out.println("LIVEPEER onHandshakeResult");

		}

		@Override
		public void onPublishHandlerPlay(PushPublishRTMPNetConnectionSession pushPublisherSession, OutputStream out,
				long[] playSizes) {
			System.out.println("LIVEPEER onPublishHandlerPlay");

		}

		@Override
		public void onPushPublisherSessionCreate(PushPublishRTMPNetConnectionSession pushPublisherSession) {
			System.out.println("LIVEPEER onPushPublisherSessionCreate");
		}

		@Override
		public void onPushPublisherSessionDestroy(PushPublishRTMPNetConnectionSession pushPublisherSession) {
			System.out.println("LIVEPEER onPushPublisherSessionDestroy");
		}

		@Override
		public void onReleaseStream(PushPublishRTMPNetConnectionSession pushPublisherSession, RequestFunction function,
				AMFDataList params) {
			System.out.println("LIVEPEER onReleaseStream");

		}

		@Override
		public void onSessionClosed(PushPublishRTMPNetConnectionSession pushPublisherSession) {
			System.out.println("LIVEPEER onSessionClosed");
		}

		@Override
		public void onSessionIdle(PushPublishRTMPNetConnectionSession pushPublisherSession) {
			System.out.println("LIVEPEER onSessionIdle");
		}

		@Override
		public void onSessionOpened(PushPublishRTMPNetConnectionSession pushPublisherSession) {
			System.out.println("LIVEPEER onSessionOpened");
		}

		@Override
		public void onStreamCreate(PushPublishRTMPNetConnectionSession pushPublisherSession, RequestFunction function,
				AMFDataList params) {
			System.out.println("LIVEPEER onStreamCreate");
		}

		@Override
		public void onStreamOnPlayStatus(PushPublishRTMPNetConnectionSession pushPublisherSession,
				RequestFunction function, AMFDataList params) {
			System.out.println("LIVEPEER onStreamOnPlayStatus");
		}

		@Override
		public void onStreamOnStatus(PushPublishRTMPNetConnectionSession pushPublisherSession, RequestFunction function,
				AMFDataList params) {
			System.out.println("LIVEPEER onStreamOnStatus");
		}

		@Override
		public void onValidateSession(PushPublishRTMPNetConnectionSession pushPublisherSession) {
			System.out.println("LIVEPEER onValidateSession");
		}

		@Override
		public void onValidateSessionResult(PushPublishRTMPNetConnectionSession pushPublisherSession, boolean result) {
			System.out.println("LIVEPEER onValidateSessionResult");
		}

	}

	class StreamListener implements IMediaStreamActionNotify3 {
		public void onMetaData(IMediaStream stream, AMFPacket metaDataPacket) {
			System.out.println("onMetaData[" + stream.getContextStr() + "]: " + metaDataPacket.toString());
		}

		public void onPauseRaw(IMediaStream stream, boolean isPause, double location) {
			System.out.println(
					"onPauseRaw[" + stream.getContextStr() + "]: isPause:" + isPause + " location:" + location);
		}

		public void onPause(IMediaStream stream, boolean isPause, double location) {
			System.out.println("onPause[" + stream.getContextStr() + "]: isPause:" + isPause + " location:" + location);
		}

		public void onPlay(IMediaStream stream, String streamName, double playStart, double playLen, int playReset) {
			System.out.println("onPlay[" + stream.getContextStr() + "]: playStart:" + playStart + " playLen:" + playLen
					+ " playReset:" + playReset);
		}

		public void onPublish(IMediaStream stream, String streamName, boolean isRecord, boolean isAppend) {
			System.out.println("onPublish[" + stream.getContextStr() + "]: isRecord:" + isRecord + " isAppend:"
					+ isAppend + " name:" + streamName);

			try {
				PushPublishHTTPCupertinoLivepeerHandler http = new PushPublishHTTPCupertinoLivepeerHandler();
				http.setAppInstance(_appInstance);
				http.setSrcStreamName(streamName);
				http.setDstStreamName(streamName);
				
				http.init(_appInstance, streamName, stream, new HashMap<String, String>(), new HashMap<String, String>(), null, true);
//				http.load(new HashMap<String, String>());
				http.connect();
//				PushPublishRTMP publisher = new PushPublishRTMP();
//				publisher.addListener(new RTMPListener());
//
//				// Source stream
//				publisher.setAppInstance(_appInstance);
////				publisher.setSrcStream(stream);
//				publisher.setSrcStreamName(streamName);
////				publisher.setWaitOnMetadataAvailable(true);
//
//				// Destination stream
//				publisher.setHostname("35.232.200.158");
//				publisher.setPort(1935);
//				publisher.setDstApplicationName("stream");
//				publisher.setDstStreamName("eli");
//				publisher.setRemoveDefaultAppInstance(true);
//				synchronized(publishers)
//				{
//					publishers.put(stream, publisher);
//				}
//
//				publisher.connect();
//				getLogger().info("LIVEPEER connected");
			} catch (Exception e) {
				getLogger().info("LIVEPEER RTMP: ", e);
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
			synchronized(publishers)
			{
				PushPublishRTMP publisher = publishers.remove(stream);
				if (publisher != null)
					publisher.disconnect();
			}
		}

		public void onCodecInfoAudio(IMediaStream stream, MediaCodecInfoAudio codecInfoAudio) {
			System.out.println("onCodecInfoAudio[" + stream.getContextStr() + " Audio Codec"
					+ codecInfoAudio.toCodecsStr() + "]: ");
		}

		public void onCodecInfoVideo(IMediaStream stream, MediaCodecInfoVideo codecInfoVideo) {
			System.out.println("onCodecInfoVideo[" + stream.getContextStr() + " Video Codec"
					+ codecInfoVideo.toCodecsStr() + "]: ");
		}
	}

	class TranscoderControl implements ILiveStreamTranscoderControl {
		public boolean isLiveStreamTranscode(String transcoder, IMediaStream stream) {
			// No transcoding, Livepeer is gonna take care of it
			return false;
		}
	}

	public void onAppStart(IApplicationInstance appInstance) {
		_appInstance = appInstance;
		System.out.println("onAppStart?");
		getLogger().info("LIVEPEER onAppStart");
		appInstance.
		System.out.println("REGRET: " + appInstance.getTranscoderApplicationContext().getProfileDir());
		System.out.println("REGRET: " + Arrays.deepToString(appInstance.getProperties().getAllAsStrings()));
		appInstance.setLiveStreamTranscoderControl(new TranscoderControl());
		appInstance.getVHost().
		System.out.println("LIVEPEER " + appInstance.getTranscoderProperties());
	}

	public void onStreamCreate(IMediaStream stream) {
		if (stream.getClientId() == -1) {
			getLogger().info("Ignoring local stream");
			return;
		}
		IMediaStreamActionNotify2 actionNotify = new StreamListener();

		WMSProperties props = stream.getProperties();
		synchronized (props) {
			props.put("streamActionNotifier", actionNotify);
		}
		stream.addClientListener(actionNotify);
		getLogger().info("LIVEPEER onStreamCreate[" + stream + "]: clientId:" + stream.getClientId());
		getLogger().info("LIVEPEER onStreamCreate stream=" + stream.isPublisherStream());

	}
}
