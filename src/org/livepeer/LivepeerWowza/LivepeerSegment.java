package org.livepeer.LivepeerWowza;

import com.wowza.util.PacketFragmentList;
import com.wowza.wms.httpstreamer.cupertinostreaming.livestreampacketizer.LiveStreamPacketizerCupertinoChunk;
import com.wowza.wms.logging.WMSLogger;
import com.wowza.wms.manifest.model.m3u8.MediaSegmentModel;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.util.EntityUtils;

/**
 * Represents a single Livepeer segment being pushed and all of its transcoded renditions. Takes care of the pushing
 * and pulling associated with that.
 */
public class LivepeerSegment implements Comparable<LivepeerSegment> {
  protected MediaSegmentModel mediaSegment;
  protected LivepeerStream livepeerStream;
  private int sequenceNumber;
  protected WMSLogger logger;
  private int connectionTimeout = 5000;
  private int uploadAttempts = 3;
  private HttpClient httpClient;
  private PacketFragmentList list;
  private LivepeerSegmentEntity entity;
  private LivepeerAPIResourceBroadcaster livepeerBroadcaster;

  public LivepeerSegment(MediaSegmentModel mediaSegment, LivepeerStream livepeerStream) {
    this.mediaSegment = mediaSegment;
    this.livepeerStream = livepeerStream;
    this.logger = livepeerStream.getLogger();
    String uri = this.getSegmentUri();
    String[] parts = uri.split("\\/");
    this.sequenceNumber = Integer.parseInt(parts[parts.length - 1].replace(".ts", ""));
    logger.info("SEQUENCE NUMBER +=+++ " + this.sequenceNumber);
    httpClient = livepeerStream.getHttpClient();
    list = mediaSegment.getFragmentList();
    entity = new LivepeerSegmentEntity(list);
    livepeerBroadcaster = livepeerStream.getBroadcaster();
  }

  /**
   * Start attempting to upload this segment
   * @param attempt What attempt is this?
   */
  public void uploadSegment(final Integer attempt) {
    String segmentUri = getSegmentUri();
    if (attempt >= uploadAttempts) {
      logger.error("canonical-log-line function=uploadSegment phase=giveup uri=" + segmentUri);
      return;
    }
    LiveStreamPacketizerCupertinoChunk chunkInfo = (LiveStreamPacketizerCupertinoChunk) mediaSegment.getChunkInfoCupertino();
    livepeerStream.getExecutorService().execute(() -> {
      try {
        if (chunkInfo == null || list == null || list.size() == 0) {
          System.out.println("chunkInfo null segmentUri=" + segmentUri);
          return;
        }
        else {
          System.out.println("chunkInfo non-null segmentUri=" + segmentUri);
        }
        String id = livepeerStream.getLivepeerId();
        String url = livepeerBroadcaster.getAddress() + "/live/" + id + "/" + segmentUri;
        // We're not expecting anything until we send the full segment, so:
        int socketTimeout = Math.toIntExact(chunkInfo.getDuration()) * 3;
        RequestConfig requestConfig = RequestConfig.custom()
          .setSocketTimeout(socketTimeout)
          .setConnectTimeout(connectionTimeout)
          .setConnectionRequestTimeout(connectionTimeout)
          .build();
        HttpPut req = new HttpPut(url);
        req.setConfig(requestConfig);
        req.setEntity(entity);
        int width = chunkInfo.getCodecInfoVideo().getVideoWidth();
        int height = chunkInfo.getCodecInfoVideo().getVideoHeight();
        String resolution = width + "x" + height;
        req.setHeader("Content-Duration", "" + chunkInfo.getDuration());
        req.setHeader("Content-Resolution", resolution);
        long start = System.currentTimeMillis();
        // some operations
        HttpResponse res = httpClient.execute(req);
        double elapsed = (System.currentTimeMillis() - start) / (double) 1000;
        // Consume the response entity to free the thread
        HttpEntity responseEntity = res.getEntity();
        EntityUtils.consume(responseEntity);
        int status = res.getStatusLine().getStatusCode();
        logger.info("canonical-log-line function=uploadSegment phase=end elapsed=" + elapsed + " url=" + url + " status=" + status + " duration=" + (chunkInfo.getDuration() / (double) 1000) + " resolution=" + resolution + " size=" + entity.getSize());
      } catch (Exception e) {
        e.printStackTrace();
        logger.error("canonical-log-line function=uploadSegment phase=error uri=" + segmentUri + " error=" + e);
        livepeerStream.notifyBroadcasterProblem(livepeerBroadcaster);
        this.uploadSegment(attempt + 1);
      }
    });
  }

  /**
   * Livepeer wants "0.ts" instead of "media_0.ts", so
   */
  public String getSegmentUri() {
    return mediaSegment.getUri().toString().replace("media_", "");
  }

  public int getSequenceNumber() {
    return this.sequenceNumber;
  }

  @Override
  public int compareTo(LivepeerSegment that) {
    return this.getSequenceNumber() - that.getSequenceNumber();
  }
}
