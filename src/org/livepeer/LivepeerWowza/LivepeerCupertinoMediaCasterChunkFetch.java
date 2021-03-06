package org.livepeer.LivepeerWowza;

import com.wowza.wms.application.IApplicationInstance;
import com.wowza.wms.mediacaster.cupertino.CupertinoMediaCasterContext;
import com.wowza.wms.mediacaster.cupertino.CupertinoMediaCasterFetchedResult;
import com.wowza.wms.mediacaster.cupertino.ICupertinoMediaCasterChunkFetch;
import com.wowza.wms.mediacaster.cupertino.LivepeerCupertinoMediaCasterFetchedResult;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

public class LivepeerCupertinoMediaCasterChunkFetch implements ICupertinoMediaCasterChunkFetch {
  private HttpClient httpClient;
  private LivepeerAPI livepeer;

  @Override
  public void init(IApplicationInstance applicationInstance, CupertinoMediaCasterContext cupertinoMediaCasterContext) {
    livepeer = LivepeerAPI.getApiInstance(applicationInstance);
    httpClient = livepeer.getHttpClient();
  }

  /**
   * Fetch a Livepeer manifest
   * @param path path to manifest
   * @param timeout timeout in ms
   * @param retries number of times to retry
   * @return result manifest
   */
  @Override
  public CupertinoMediaCasterFetchedResult fetchManifest(String path, int timeout, int retries) {
    LivepeerCupertinoMediaCasterFetchedResult httpResult = new LivepeerCupertinoMediaCasterFetchedResult();
    httpResult.setResultType(CupertinoMediaCasterFetchedResult.textType);
    String manifest = livepeer.getManifest(path);
    if (manifest != null) {
      httpResult.setResultString(manifest);
      httpResult.setTimedOut(false);
      httpResult.setResultCode(200);
      return httpResult;
    }
    if (true) {
      throw new RuntimeException("got HLS request for livepeer stream, should be cached instead");
    }
    for (int i = 0; i < retries; i += 1) {
      try {
        HttpGet req = new HttpGet(path);
        RequestConfig requestConfig = RequestConfig.custom()
          .setSocketTimeout(timeout)
          .setConnectTimeout(timeout)
          .setConnectionRequestTimeout(timeout)
          .build();
        req.setConfig(requestConfig);
        long start = System.currentTimeMillis();
        HttpResponse res = httpClient.execute(req);
        double elapsed = (System.currentTimeMillis() - start) / (double) 1000;
        String responseString = new BasicResponseHandler().handleResponse(res);
        String outputString = fixAbsoluteUrls(responseString, path);
        httpResult.setResultString(outputString);
        httpResult.setTimedOut(false);
        httpResult.setResultCode(200);
        livepeer.getLogger().info("canonical-log-line function=fetchManifest elapsed=" + elapsed + " url=" + path + " status=" + res.getStatusLine());
        return httpResult;
      } catch (IOException e) {
        livepeer.getLogger().error("GET " + path + " failed, retry #" + i + ": "+ e.getMessage());
      }
    }
    httpResult.setResultCode(404);
    httpResult.setTimedOut(true);
    return httpResult;
  }

  /**
   * Wowza's HLS parser can't handle leading slashes in HLS urls. This takes in an HLS manifest with /foo/bar/segment.ts
   * urls and replaces them with full http://example.com/foo/bar/segment.ts urls.
   * @param responseString HLS manifest
   * @param path https://example.com/foo/bar
   * @return New manifest with full http://example.com/foo/bar/segment.ts URLs
   */
  protected String fixAbsoluteUrls(String responseString, String path) {
    URL url = null;
    try {
      url = new URL(path);
    } catch (MalformedURLException e) {
      // Uhhhh we just successfully downloaded this URL over HTTP, so this really shouldn't happen.
      e.printStackTrace();
      throw new RuntimeException(e);
    }
    String hostPrefix = url.getProtocol() + "://" + url.getAuthority();
    String outputString = "";
    for (String line : responseString.split("\n")) {
      if (line.charAt(0) == '/') {
        outputString += hostPrefix;
      }
      outputString += line + "\n";
    }
    return outputString;
  }

  @Override
  public CupertinoMediaCasterFetchedResult fetchBlock(String path, int timeout, int retries) {
    LivepeerCupertinoMediaCasterFetchedResult httpResult = new LivepeerCupertinoMediaCasterFetchedResult();
    httpResult.setResultType(CupertinoMediaCasterFetchedResult.dataType);
    byte[] data = livepeer.getCachedSegment(path);
    if (data != null) {
      httpResult.setDataBlock(data);
      httpResult.setTimedOut(false);
      httpResult.setResultCode(200);
      return httpResult;
    }
    if (true) {
      throw new RuntimeException("got HLS request for livepeer stream, should be cached instead");
    }
    for (int i = 0; i < retries; i += 1) {
      try {
        HttpGet req = new HttpGet(path);
        RequestConfig requestConfig = RequestConfig.custom()
          .setSocketTimeout(timeout)
          .setConnectTimeout(timeout)
          .setConnectionRequestTimeout(timeout)
          .build();
        req.setConfig(requestConfig);
        long start = System.currentTimeMillis();
        HttpResponse res = httpClient.execute(req);
        double elapsed = (System.currentTimeMillis() - start) / (double) 1000;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        res.getEntity().writeTo(baos);
        httpResult.setDataBlock(baos.toByteArray());
        httpResult.setTimedOut(false);
        httpResult.setResultCode(200);
        livepeer.getLogger().info("canonical-log-line function=fetchBlock elapsed=" + elapsed + " url=" + path + " status=" + res.getStatusLine());
        return httpResult;
      } catch (IOException e) {
        livepeer.getLogger().error("GET " + path + " failed, retry #" + i + ": "+ e.getMessage());
      }
    }
    httpResult.setResultCode(404);
    httpResult.setTimedOut(true);
    return httpResult;
  }

}
