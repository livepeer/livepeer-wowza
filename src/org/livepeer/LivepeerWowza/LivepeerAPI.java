package org.livepeer.LivepeerWowza;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wowza.wms.application.IApplicationInstance;
import com.wowza.wms.logging.WMSLogger;
import com.wowza.wms.rest.vhosts.applications.transcoder.TranscoderAppConfig;
import com.wowza.wms.rest.vhosts.applications.transcoder.TranscoderTemplateAppConfig;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LivepeerAPI {

  private static String LIVEPEER_API_URL = "https://livepeer.live/api";
  private static LivepeerAPI _instance;
  private static ConcurrentHashMap<IApplicationInstance, LivepeerAPI> apiInstances = new ConcurrentHashMap<>();
  private ConcurrentHashMap<String, Boolean> runningStreamFiles = new ConcurrentHashMap<>();
  private CloseableHttpClient httpClient;
  private ObjectMapper mapper;
  private WMSLogger logger;

  public static LivepeerAPI getApiInstance(IApplicationInstance appInstance) {
    return apiInstances.get(appInstance);
  }

  public LivepeerAPI(IApplicationInstance appInstance) {
    apiInstances.put(appInstance, this);
    httpClient = HttpClients.createDefault();
    // Trust own CA and all self-signed certs
    SSLContext sslcontext = null;
    try {
      sslcontext = SSLContexts.custom()
              .loadTrustMaterial(getClass().getResource("/livepeer_cacerts"), null)
              .build();
    } catch (Exception e) {
      System.out.println(e);
      throw new RuntimeException(e);
    }
    // Allow TLSv1 protocol only
    SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(
            sslcontext,
            new String[]{"TLSv1.1", "TLSv1.2"},
            null,
            SSLConnectionSocketFactory.getDefaultHostnameVerifier());
    httpClient = HttpClients.custom()
            .setSSLSocketFactory(sslsf)
            .setUserAgent("LivepeerWowza/" + LivepeerVersion.VERSION)
            .build();
    mapper = new ObjectMapper();
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  public HttpClient getHttpClient() {
    return httpClient;
  }

  public void setLogger(WMSLogger _logger) {
    logger = _logger;
  }

  protected void log(String text) {
    if (this.logger != null) {
      this.logger.info("LivepeerAPI: " + text);
    } else {
      System.out.println("LivepeerAPI: " + text);
    }
  }

  public boolean isRunningStreamFile(String name) {
    return runningStreamFiles.containsKey(name);
  }

  public void addRunningStreamFile(String name) {
    runningStreamFiles.put(name, true);
  }

  private HttpResponse _execute(HttpGet req) throws IOException {
    log("" + req.getRequestLine());
    HttpResponse res = httpClient.execute(req);
    log("" + res.getStatusLine());
    return res;
  }

  private HttpResponse _get(String path) throws IOException {
    log("GET " + LIVEPEER_API_URL + path);
    HttpGet getMethod = new HttpGet(LIVEPEER_API_URL + path);
    return httpClient.execute(getMethod);
  }

  private HttpResponse _post(String path, Object body) throws IOException {
    log("POST " + LIVEPEER_API_URL + path);
    String json = mapper.writeValueAsString(body);
    StringEntity requestEntity = new StringEntity(json, ContentType.APPLICATION_JSON);
    HttpPost postMethod = new HttpPost(LIVEPEER_API_URL + path);
    postMethod.setEntity(requestEntity);
    return httpClient.execute(postMethod);
  }

  /**
   * Given a vhost and application, create a Livepeer Wowza resource
   *
   * @param vhost       vhost of the application
   * @param application application name
   * @return wowza resource
   * @throws IOException something went wrong talking to the Livepeer API
   */
  public LivepeerAPIResourceStream createStreamFromApplication(String vhost, String application) throws IOException {
    LivepeerAPIResourceStream body = new LivepeerAPIResourceStream(vhost, application);
    body.setApi(this);
    HttpResponse response = _post("/stream", body);
    LivepeerAPIResourceStream info = mapper.readValue(response.getEntity().getContent(), LivepeerAPIResourceStream.class);
    info.setApi(this);
    return info;
  }

  /**
   * Get a list of usable broadcasters
   *
   * @return broadcasters
   * @throws IOException something went wrong talking to the Livepeer API
   */
  public List<LivepeerAPIResourceBroadcaster> getBroadcasters() throws IOException {
    HttpResponse response = _get("/broadcaster");
    TypeReference typeRef = new TypeReference<List<LivepeerAPIResourceBroadcaster>>() {};
    List<LivepeerAPIResourceBroadcaster> list = mapper.readValue(response.getEntity().getContent(), typeRef);
    return list;
  }

  @JsonAutoDetect(fieldVisibility = Visibility.ANY)
  public static class LivepeerAPIResourceBroadcaster {
    private String address;
    private TranscoderAppConfig transcoderAppConfig;
    private Map<String, TranscoderTemplateAppConfig> transcoderTemplateAppConfig;

    public LivepeerAPIResourceBroadcaster() {

    }

    public String getAddress() {
      return address;
    }

    public void setAddress(String _address) {
      address = _address;
    }

  }
}
