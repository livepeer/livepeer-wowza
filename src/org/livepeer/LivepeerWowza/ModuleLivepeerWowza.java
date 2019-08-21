package org.livepeer.LivepeerWowza;

import com.wowza.wms.server.LicensingException;
import com.wowza.wms.stream.*;
import com.wowza.wms.stream.livetranscoder.*;
import com.wowza.wms.module.*;

import com.wowza.wms.amf.AMFPacket;
import com.wowza.wms.application.*;
import com.wowza.wms.media.model.MediaCodecInfoAudio;
import com.wowza.wms.media.model.MediaCodecInfoVideo;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Livepeer Wowza module. It's designed to be a drop-in replacement for Wowza's
 * transcoding services, offloading everything to a hosted Livepeer API network.
 */
public class ModuleLivepeerWowza extends ModuleBase {
	private IApplicationInstance appInstance;
	private LivepeerAPI livepeer;
	private ConcurrentHashMap<String, LivepeerStream> livepeerStreams = new ConcurrentHashMap<>();

	class StreamListener implements IMediaStreamActionNotify3 {
		public void onPublish(IMediaStream stream, String streamName, boolean isRecord, boolean isAppend) {
			System.out.println("checking streamName="+streamName);
			// Avoid an infinite loop - if this new stream is a transcoded rendition, don't transcode again
			if (streamName.endsWith(".stream")) {
				String streamFileName = stream.getName().substring(0, streamName.length() - 7);
				System.out.println("streamfilename="+streamFileName);
				for (LivepeerStream livepeerStream : livepeerStreams.values()) {
					if (livepeerStream.managesStreamFile(streamFileName)) {
						getLogger().info("LIVEPEER ignoring transcoded rendition " + stream.getName());
						return;
					}
				}
			}
			else {
				System.out.println("doesnt end with stream streamName="+streamName);
			}

			LivepeerStream livepeerStream = new LivepeerStream(stream, streamName, livepeer);
			livepeerStreams.put(streamName, livepeerStream);
			// To-do: retry logic
			try {
				livepeerStream.start();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (LicensingException e) {
				e.printStackTrace();
			}
		}

		public void onStop(IMediaStream stream) {
			LivepeerStream livepeerStream = livepeerStreams.get(stream.getName());
			if (livepeerStream != null) {
				livepeerStream.stop();
				livepeerStreams.remove(stream.getName());
			}
		}

		public void onUnPublish(IMediaStream stream, String streamName, boolean isRecord, boolean isAppend) {
			LivepeerStream livepeerStream = livepeerStreams.get(stream.getName());
			if (livepeerStream != null) {
				livepeerStream.stop();
				livepeerStreams.remove(stream.getName());
			}
		}

		public void onMetaData(IMediaStream stream, AMFPacket metaDataPacket) {}

		public void onPauseRaw(IMediaStream stream, boolean isPause, double location) {}

		public void onPause(IMediaStream stream, boolean isPause, double location) {}

		public void onPlay(IMediaStream stream, String streamName, double playStart, double playLen, int playReset) {}

		public void onSeek(IMediaStream stream, double location) {}

		public void onCodecInfoAudio(IMediaStream stream, MediaCodecInfoAudio codecInfoAudio) {}

		public void onCodecInfoVideo(IMediaStream stream, MediaCodecInfoVideo codecInfoVideo) {}
	}

	class TranscoderControl implements ILiveStreamTranscoderControl {
		public boolean isLiveStreamTranscode(String transcoder, IMediaStream stream) {
			// No transcoding, Livepeer is gonna take care of it
			return false;
		}
	}

	public void onAppStart(IApplicationInstance appInstance) {
		this.appInstance = appInstance;
		this.livepeer = new LivepeerAPI(appInstance, getLogger());
		appInstance.setLiveStreamTranscoderControl(new TranscoderControl());
	}

	public void onStreamCreate(IMediaStream stream) {
		IMediaStreamActionNotify2 actionNotify = new StreamListener();

		WMSProperties props = stream.getProperties();
		synchronized (props) {
			props.put("streamActionNotifier", actionNotify);
		}
		appInstance.getMediaCasterProperties().setProperty("cupertinoChunkFetchClass", "org.livepeer.LivepeerWowza.LivepeerCupertinoMediaCasterChunkFetch");

		stream.addClientListener(actionNotify);
		getLogger().info("LIVEPEER onStreamCreate[" + stream + "]: clientId:" + stream.getClientId());
		getLogger().info("LIVEPEER onStreamCreate stream=" + stream.isPublisherStream());
	}

}
