package org.livepeer.LivepeerWowza;

import com.wowza.wms.rest.ConfigBase;
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
			LivepeerStream manager = findStreamManager(streamName);
			if (manager != null) {
				getLogger().info("LIVEPEER ignoring transcoded rendition " + streamName);
				return;
			}

			LivepeerStream livepeerStream = new LivepeerStream(stream, streamName, livepeer);
			livepeerStreams.put(streamName, livepeerStream);
			livepeerStream.start();
		}

		public void onUnPublish(IMediaStream stream, String streamName, boolean isRecord, boolean isAppend) {
			LivepeerStream livepeerStream = livepeerStreams.get(stream.getName());
			if (livepeerStream != null) {
				livepeerStream.stopStream();
				livepeerStreams.remove(stream.getName());
			}
		}

		public void onCodecInfoVideo(IMediaStream stream, MediaCodecInfoVideo codecInfoVideo) {
			getLogger().info("CodecInfoVideo for " + stream.getName());
			LivepeerStream manager = findStreamManager(stream.getName());
			if (manager == null) {
				// Not a transcoded rendition, ignore.
				return;
			}
			manager.onStreamFileCodecInfoVideo(stream, codecInfoVideo);
		}

		public void onStop(IMediaStream stream) {}

		public void onMetaData(IMediaStream stream, AMFPacket metaDataPacket) {}

		public void onPauseRaw(IMediaStream stream, boolean isPause, double location) {}

		public void onPause(IMediaStream stream, boolean isPause, double location) {}

		public void onPlay(IMediaStream stream, String streamName, double playStart, double playLen, int playReset) {}

		public void onSeek(IMediaStream stream, double location) {}

		public void onCodecInfoAudio(IMediaStream stream, MediaCodecInfoAudio codecInfoAudio) {}


	}

	class TranscoderControl implements ILiveStreamTranscoderControl {
		public boolean isLiveStreamTranscode(String transcoder, IMediaStream stream) {
			// No transcoding, Livepeer is gonna take care of it
			return false;
		}
	}

	class PacketListener implements IMediaStreamLivePacketNotify {
		@Override
		public void onLivePacket(IMediaStream stream, AMFPacket packet) {
		    String streamName = stream.getName();
		    // We only care about copying over stream file results for this
            if (streamName == null || !streamName.endsWith(".stream")) {
                return;
            }
            LivepeerStream manager = findStreamManager(streamName);
            if (manager == null) {
                return;
            }
            manager.onPacket(stream, packet);
		}
	}

	/**
	 * Find the LivepeerStream that is handling this incoming transcoded rendition, if any
	 * @param streamName name of incoming stream
	 * @return LivepeerStream in charge of this rendition or null if not found
	 */
	public LivepeerStream findStreamManager(String streamName) {
		// Avoid an infinite loop - if this new stream is a transcoded rendition or one of our streamfiles,
        // don't transcode again
		if (streamName.endsWith(".stream")) {
            streamName = streamName.substring(0, streamName.length() - 7);
		}
        for (LivepeerStream livepeerStream : livepeerStreams.values()) {
            if (livepeerStream.managesStreamFile(streamName)) {
                return livepeerStream;
            }
        }
		return null;
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

		// recommended settings for lower-latency streaming from here:
		// https://www.wowza.com/docs/how-to-improve-playback-of-lower-latency-apple-hls-streams
		appInstance.getLiveStreamPacketizerProperties().setProperty("cupertinoChunkDurationTarget", "1000");
		appInstance.getLiveStreamPacketizerProperties().setProperty("cupertinoMaxChunkCount", "100");
		appInstance.getLiveStreamPacketizerProperties().setProperty("cupertinoPlaylistChunkCount", "12");

		stream.addClientListener(actionNotify);
		stream.addLivePacketListener(new PacketListener());
		getLogger().info("LIVEPEER onStreamCreate[" + stream + "]: clientId:" + stream.getClientId());
		getLogger().info("LIVEPEER onStreamCreate stream=" + stream.isPublisherStream());
	}

}
