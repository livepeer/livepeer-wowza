package org.livepeer.LivepeerWowza;

import com.wowza.util.IPacketFragment;
import com.wowza.util.PacketFragmentList;
import com.wowza.wms.httpstreamer.cupertinostreaming.livestreampacketizer.LiveStreamPacketizerCupertinoChunk;
import com.wowza.wms.logging.WMSLogger;
import com.wowza.wms.manifest.model.m3u8.MediaSegmentModel;
import org.apache.commons.fileupload.FileUploadBase;
import org.apache.commons.fileupload.MultipartStream;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.message.BasicLineParser;
import org.apache.http.message.LineParser;
import org.apache.http.util.EntityUtils;
import org.apache.james.mime4j.dom.Message;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a single Livepeer segment being pushed and all of its transcoded renditions. Takes care of the pushing
 * and pulling associated with that.
 */
public class LivepeerSegment implements Comparable<LivepeerSegment> {
  public static final int UPLOAD_RETRIES = 3;
  public static final String SOURCE = "source";
  public static final String MIME_HEADER_RENDITION_NAME = "Rendition-Name";

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
  private boolean ready = false;

  public LivepeerSegment(MediaSegmentModel mediaSegment, LivepeerStream livepeerStream) {
    this.mediaSegment = mediaSegment;
    this.livepeerStream = livepeerStream;
    this.logger = livepeerStream.getLogger();
    this.segmentUri = mediaSegment.getUri().toString().replace("media_", "");
    String[] parts = this.segmentUri.split("\\/");
    this.sequenceNumber = Integer.parseInt(parts[parts.length - 1].replace(".ts", ""));
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
      logger.info("canonical-log-line function=uploadSegment id=" + id + " phase=start uri=" + segmentUri);
      try {

        // Initialize PUT request
        String url = livepeerBroadcaster.getAddress() + "/live/" + id + "/" + segmentUri;
        HttpPut req = new HttpPut(url);
        req.setConfig(requestConfig);
        byte[] data = buffers.get(SOURCE);
        req.setEntity(new ByteArrayEntity(data));
        req.setHeader("Content-Duration", "" + this.duration);
        req.setHeader("Content-Resolution", this.resolution);
        req.setHeader("Accept", "multipart/mixed");
        long start = System.currentTimeMillis();

        // Execute request
        HttpResponse res = httpClient.execute(req);

        // Upload complete; initialize multipart parsing
        double elapsed = (System.currentTimeMillis() - start) / (double) 1000;
        HttpEntity responseEntity = res.getEntity();
        int status = res.getStatusLine().getStatusCode();
        logger.info("canonical-log-line function=uploadSegment id=" + id + " phase=uploaded responseLength=" + " elapsed=" + elapsed + " url=" + url + " status=" + status + " resolution=" + resolution + " size=" + data.length);
        ContentType contentType = ContentType.get(responseEntity);
        String boundaryText = contentType.getParameter("boundary");
        MultipartStream multipartStream = new MultipartStream(
                responseEntity.getContent(),
                boundaryText.getBytes());
        boolean nextPart = multipartStream.skipPreamble();
        int i = 0;
        List<LivepeerAPIResourceStream.Profile> profiles = livepeerStream.getProfiles();

        // Save each transcoded renditions locally by their Rendition-Name
        while(nextPart) {
          // Find Rendition-Name
          String headersStr = multipartStream.readHeaders();
          String renditionName = null;
          for (String line : headersStr.split("\r\n")) {
            Header header = BasicLineParser.parseHeader(line, null);
            if (header.getName().equals(MIME_HEADER_RENDITION_NAME)) {
              renditionName = header.getValue();
            }
          }

          // Fallback for go-livepeer < v0.5.4 which lacks Rendition-Name
          if (renditionName == null) {
            renditionName = profiles.get(i).getName();
            logger.info("Couldn't find " + MIME_HEADER_RENDITION_NAME + ", falling back to positional profile: " + renditionName);
          }

          // Dump rendition into a byte[]
          ByteArrayOutputStream baos = new ByteArrayOutputStream();
          multipartStream.readBodyData(baos);
          buffers.put(renditionName, baos.toByteArray());
          nextPart = multipartStream.readBoundary();
          i += 1;
        }

        // todo: is this still necessary, or has the response now been fully read?
        EntityUtils.consumeQuietly(responseEntity);

        elapsed = (System.currentTimeMillis() - start) / (double) 1000;
        this.ready = true;
        livepeerStream.pruneSegments();
        logger.info("canonical-log-line function=uploadSegment phase=end elapsed=" + elapsed + " url=" + url + " status=" + status + " duration=" + (duration / (double) 1000) + " resolution=" + resolution + " responseSize=REDACTED");
      } catch (Exception e) {
        e.printStackTrace();
        logger.error("canonical-log-line function=uploadSegment phase=error uri=" + segmentUri + " error=" + e);
        livepeerStream.notifyBroadcasterProblem(livepeerBroadcaster);
        this.uploadSegment();
      }
    });
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

  public byte[] getRendition(String rendition) {
    if (!this.ready) {
      return null;
    }
    return this.buffers.get(rendition);
  }

  public long getDuration() {
      return this.duration;
  }

  public boolean isReady() {
      return this.ready;
  }

  @Override
  public int compareTo(LivepeerSegment that) {
    return this.getSequenceNumber() - that.getSequenceNumber();
  }
}
