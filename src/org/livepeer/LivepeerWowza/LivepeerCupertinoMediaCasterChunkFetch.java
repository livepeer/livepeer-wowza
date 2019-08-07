package org.livepeer.LivepeerWowza;

import com.wowza.wms.application.IApplicationInstance;
import com.wowza.wms.mediacaster.cupertino.CupertinoMediaCasterContext;
import com.wowza.wms.mediacaster.cupertino.CupertinoMediaCasterFetchedResult;
import com.wowza.wms.mediacaster.cupertino.ICupertinoMediaCasterChunkFetch;
import com.wowza.wms.mediacaster.cupertino.LivepeerCupertinoMediaCasterFetchedResult;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

public class LivepeerCupertinoMediaCasterChunkFetch implements ICupertinoMediaCasterChunkFetch {
  private HttpClient httpClient;

  @Override
  public void init(IApplicationInstance applicationInstance, CupertinoMediaCasterContext cupertinoMediaCasterContext) {
    System.out.println("LIVEPEER LivepeerCupertinoMediaCasterChunkFetch init()");
    LivepeerAPI livepeer = LivepeerAPI.getApiInstance(applicationInstance);
    httpClient = livepeer.getHttpClient();
  }

  @Override
  public CupertinoMediaCasterFetchedResult fetchManifest(String path, int timeout, int retries) {
    System.out.println("LIVEPEER LivepeerCupertinoMediaCasterChunkFetch fetchManifest(" + path + ", " + timeout + ", " + retries + ")");
    LivepeerCupertinoMediaCasterFetchedResult httpResult = new LivepeerCupertinoMediaCasterFetchedResult();
    HttpGet req = new HttpGet(path);
    String responseString = null;
    try {
      HttpResponse res = httpClient.execute(req);
      responseString = new BasicResponseHandler().handleResponse(res);
    } catch (IOException e) {
      e.printStackTrace();
    }
    // Wowza's HLS parser can't handle leading slashes - replace them with full URLs please
    URL url = null;
    try {
      url = new URL(path);
    } catch (MalformedURLException e) {
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
    System.out.println(outputString);
    httpResult.setResultType(CupertinoMediaCasterFetchedResult.textType);
    httpResult.setResultString(outputString);
    httpResult.setTimedOut(false);
    httpResult.setResultCode(200);
    return httpResult;
  }

  @Override
  public CupertinoMediaCasterFetchedResult fetchBlock(String path, int timeout, int retries) {
    System.out.println("LIVEPEER LivepeerCupertinoMediaCasterChunkFetch fetchBlock(" + path + ", " + timeout + ", " + retries + ")");
    //if not success set to 404
    HttpGet req = new HttpGet(path);
    LivepeerCupertinoMediaCasterFetchedResult httpResult = new LivepeerCupertinoMediaCasterFetchedResult();
    String responseString = null;
    HttpResponse res = null;
    try {
      res = httpClient.execute(req);
    } catch (IOException e) {
      e.printStackTrace();
    }
    httpResult.setResultType(CupertinoMediaCasterFetchedResult.dataType);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try {
      res.getEntity().writeTo(baos);
    } catch (IOException e) {
      e.printStackTrace();
    }
    httpResult.setDataBlock(baos.toByteArray());
    httpResult.setTimedOut(false);
    httpResult.setResultCode(200);
    return httpResult;
//
//  data result
//
//  httpResult.resultType = CupertinoMediaCasterFetchedResult.dataType;
//  httpResult.dataBlock = baos.toByteArray();
//  httpResult.timedOut = false;
//  httpResult.resultCode = 200;
  }

}
