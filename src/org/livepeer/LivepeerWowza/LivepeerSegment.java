package org.livepeer.LivepeerWowza;

import com.wowza.util.IPacketFragment;
import com.wowza.util.PacketFragmentList;
import com.wowza.wms.httpstreamer.cupertinostreaming.livestreampacketizer.LiveStreamPacketizerCupertinoChunk;
import com.wowza.wms.logging.WMSLogger;
import com.wowza.wms.manifest.model.m3u8.MediaSegmentModel;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.util.EntityUtils;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a single Livepeer segment being pushed and all of its transcoded renditions. Takes care of the pushing
 * and pulling associated with that.
 */
public class LivepeerSegment implements Comparable<LivepeerSegment> {
  public static final int UPLOAD_RETRIES = 3;
  public static final String SOURCE = "source";

  protected MediaSegmentModel mediaSegment;
  protected LivepeerStream livepeerStream;
  private int sequenceNumber;
  protected WMSLogger logger;
  private int connectionTimeout = 5000;
  private int attempt = 0;
  private int currentAttempt = 0;
  private HttpClient httpClient;
  private LivepeerAPIResourceBroadcaster livepeerBroadcaster;
  private long duration;
  private String resolution;
  private String segmentUri;
  private RequestConfig requestConfig;
  private Map<String, byte[]> buffers = new ConcurrentHashMap<String, byte[]>();

  public LivepeerSegment(MediaSegmentModel mediaSegment, LivepeerStream livepeerStream) {
    this.mediaSegment = mediaSegment;
    this.livepeerStream = livepeerStream;
    this.logger = livepeerStream.getLogger();
    this.segmentUri = mediaSegment.getUri().toString().replace("media_", "");
    String[] parts = this.segmentUri.split("\\/");
    this.sequenceNumber = Integer.parseInt(parts[parts.length - 1].replace(".ts", ""));
    logger.info("SEQUENCE NUMBER +=+++ " + this.sequenceNumber);
    httpClient = livepeerStream.getHttpClient();
    PacketFragmentList list = mediaSegment.getFragmentList();
    livepeerBroadcaster = livepeerStream.getBroadcaster();

    LiveStreamPacketizerCupertinoChunk chunkInfo = (LiveStreamPacketizerCupertinoChunk) mediaSegment.getChunkInfoCupertino();
    if (chunkInfo == null || list == null || list.size() == 0) {
      throw new RuntimeException("chunkInfo=null attempt="+attempt);
    }

    // Populate the byte[] array for the source segments
    buffers.put(SOURCE, getByteArray(list));

    // Gather metadata; we're not holding onto the MediaSegmentModel so this is our last chance
    int width = chunkInfo.getCodecInfoVideo().getVideoWidth();
    int height = chunkInfo.getCodecInfoVideo().getVideoHeight();
    this.resolution = width + "x" + height;
    this.duration = chunkInfo.getDuration();

    // Build our HTTP request config
    int socketTimeout = Math.toIntExact(this.duration) * 3;
    this.requestConfig = RequestConfig.custom()
      .setSocketTimeout(socketTimeout)
      .setConnectTimeout(connectionTimeout)
      .setConnectionRequestTimeout(connectionTimeout)
      .build();
  }

  private byte[] getByteArray(PacketFragmentList list) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    Iterator<IPacketFragment> itr = list.getFragments().iterator();
    while (itr.hasNext()) {
      IPacketFragment fragment = itr.next();
      if (fragment.getLen() <= 0)
        continue;
      byte[] data = fragment.getBuffer();
      try {
        baos.write(data);
      } catch (IOException e) {
        e.printStackTrace();
        throw new RuntimeException("Failed to write byte array");
      }
    }

    return baos.toByteArray();
  }

  /**
   * Start attempting to upload this segment
   */
  public void uploadSegment() {
    String segmentUri = getSegmentUri();
    String id = livepeerStream.getLivepeerId();
    if (attempt >= UPLOAD_RETRIES) {
      logger.error("canonical-log-line function=uploadSegment id=" + id + " phase=giveup uri=" + segmentUri);
      return;
    }
    attempt += 1;

    livepeerStream.getExecutorService().execute(() -> {
      logger.error("canonical-log-line function=uploadSegment id=" + id + " phase=start uri=" + segmentUri);
      try {
        String url = livepeerBroadcaster.getAddress() + "/live/" + id + "/" + segmentUri;
        // We're not expecting anything until we send the full segment, so:
        HttpPut req = new HttpPut(url);
        req.setConfig(requestConfig);
        byte[] data = buffers.get(SOURCE);
        req.setEntity(new ByteArrayEntity(data));
        req.setHeader("Content-Duration", "" + this.duration);
        req.setHeader("Content-Resolution", this.resolution);
        long start = System.currentTimeMillis();
        HttpResponse res = httpClient.execute(req);
        double elapsed = (System.currentTimeMillis() - start) / (double) 1000;
        // Consume the response entity to free the thread
        HttpEntity responseEntity = res.getEntity();
        EntityUtils.consume(responseEntity);
        int status = res.getStatusLine().getStatusCode();
        logger.info("canonical-log-line function=uploadSegment phase=end elapsed=" + elapsed + " url=" + url + " status=" + status + " duration=" + (duration / (double) 1000) + " resolution=" + resolution + " size=" + data.length);
        this.downloadSegments();
      } catch (Exception e) {
        e.printStackTrace();
        logger.error("canonical-log-line function=uploadSegment phase=error uri=" + segmentUri + " error=" + e);
        livepeerStream.notifyBroadcasterProblem(livepeerBroadcaster);
        this.uploadSegment();
      }
    });
  }

  public void downloadSegments() {
    List<LivepeerAPIResourceStream.Profile> profiles = livepeerStream.getProfiles();
    final String id = livepeerStream.getLivepeerId();
    final String filename = getSequenceNumber() + ".ts";
    LiveStreamPacketizerCupertinoChunk chunkInfo = (LiveStreamPacketizerCupertinoChunk) mediaSegment.getChunkInfoCupertino();
    for (final LivepeerAPIResourceStream.Profile profile : profiles) {
      String name = profile.getName();
      String url = livepeerBroadcaster.getAddress() + "/stream/" + id + "/" + name + "/" + filename;
      livepeerStream.getExecutorService().execute(() -> {
        try {
          logger.info("canonical-log-line function=downloadSegment phase=start url=" + url);
          HttpGet req = new HttpGet(url);
          req.setConfig(requestConfig);
          long start = System.currentTimeMillis();
          HttpResponse res = httpClient.execute(req);
          double elapsed = (System.currentTimeMillis() - start) / (double) 1000;
          // Consume the response entity to free the thread
          HttpEntity responseEntity = res.getEntity();
          ByteArrayOutputStream baos = new ByteArrayOutputStream();
          responseEntity.writeTo(baos);
          int status = res.getStatusLine().getStatusCode();
          byte[] data = baos.toByteArray();
          buffers.put(name, data);
          logger.info("canonical-log-line function=downloadSegment phase=end elapsed=" + elapsed + " url=" + url + " status=" + status + " size=" + data.length);
        } catch (Exception e) {
          e.printStackTrace();
          logger.error("canonical-log-line function=downloadSegment phase=error url=" + url + " error=" + e);
          livepeerStream.notifyBroadcasterProblem(livepeerBroadcaster);
          this.uploadSegment();
        }
      });
    }
  }

  /**
   * Livepeer wants "0.ts" instead of "media_0.ts", so
   */
  public String getSegmentUri() {
    return segmentUri;
  }

  public int getSequenceNumber() {
    return this.sequenceNumber;
  }

  @Override
  public int compareTo(LivepeerSegment that) {
    return this.getSequenceNumber() - that.getSequenceNumber();
  }
}
