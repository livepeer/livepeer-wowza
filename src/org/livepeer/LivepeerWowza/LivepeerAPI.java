package org.livepeer.LivepeerWowza;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wowza.wms.application.IApplicationInstance;
import com.wowza.wms.application.WMSProperties;
import com.wowza.wms.logging.WMSLogger;
import com.wowza.wms.media.model.MediaCodecInfoVideo;
import com.wowza.wms.rest.vhosts.applications.transcoder.TranscoderAppConfig;
import com.wowza.wms.rest.vhosts.applications.transcoder.TranscoderTemplateAppConfig;
import com.wowza.wms.server.Server;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHeader;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.EntityUtils;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public class LivepeerAPI {

  private static LivepeerAPI _instance;
  private IApplicationInstance appInstance;
  private static ConcurrentHashMap<IApplicationInstance, LivepeerAPI> apiInstances = new ConcurrentHashMap<>();
  private String livepeerApiUrl;
  private String livepeerHost;
  private String livepeerApiKey;
  private CloseableHttpClient httpClient;
  private ObjectMapper mapper;
  private WMSLogger logger;

  private LivepeerAPIProperties props;

  public static LivepeerAPI getApiInstance(IApplicationInstance appInstance) {
    return apiInstances.get(appInstance);
  }

  public LivepeerAPI(IApplicationInstance appInstance, WMSLogger logger) {
    this.logger = logger;
    this.appInstance = appInstance;
    this.props = new LivepeerAPIProperties(appInstance);
    apiInstances.put(appInstance, this);

    livepeerApiUrl = props.getApiServerUrl();
    livepeerApiKey = props.getApiKey();

    // Get our configuration options. They may be specified in the server, vhost, or application, and they override
    // in that order.


    // API locations are specified at https://livepeer.live/api, but we need https://livepeer.live/api/stream
    // for some applications.

    try {
      URI apiUrl = new URI(livepeerApiUrl);
      URIBuilder streamUriBuilder = new URIBuilder(apiUrl);
      streamUriBuilder.setPath("/");
      this.livepeerHost = streamUriBuilder.toString();
      // Remove trailing slash
      this.livepeerHost = this.livepeerHost.substring(0, this.livepeerHost.length() - 1);
    } catch (URISyntaxException e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }

    logger.info("Livepeer API server URL: " + livepeerApiUrl);

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
    // Trust Let's Encrypt certs
    SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(
            sslcontext,
            new String[]{"TLSv1.1", "TLSv1.2"},
            null,
            SSLConnectionSocketFactory.getDefaultHostnameVerifier());
    Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory> create()
            .register("https", sslsf)
            .register("http", new PlainConnectionSocketFactory())
            .build();
    // Increase maximum number of connections to a given host
    PoolingHttpClientConnectionManager poolingConnManager
            = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
    poolingConnManager.setMaxTotal(100);
    poolingConnManager.setDefaultMaxPerRoute(10);
    poolingConnManager.setDefaultSocketConfig(SocketConfig.custom().
            setSoTimeout(5000).build());
    httpClient = HttpClients.custom()
            .setConnectionManager(poolingConnManager)
            .setSSLSocketFactory(sslsf)
            .setUserAgent("LivepeerWowza/" + LivepeerVersion.VERSION)
            .build();
    mapper = new ObjectMapper();
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }
  
  public WMSLogger getLogger() {
    return this.logger;
  }

  public HttpClient getHttpClient() {
    return httpClient;
  }

  public IApplicationInstance getAppInstance() {
    return appInstance;
  }

  public String getLivepeerHost() {
    String hardcodedBroadcaster = props.getBroadcasterUrl();
    if (hardcodedBroadcaster != null) {
      return hardcodedBroadcaster;
    }
    return livepeerHost;
  }

  public LivepeerAPIProperties getProps() {
    return props;
  }

  protected void log(String text) {
    if (this.logger != null) {
      this.logger.info("LivepeerAPI: " + text);
    } else {
      System.out.println("LivepeerAPI: " + text);
    }
  }

  private HttpResponse _get(String path) throws IOException {
    log("GET " + livepeerApiUrl + path);
    HttpGet getMethod = new HttpGet(livepeerApiUrl + path);
    return httpClient.execute(getMethod);
  }

  private HttpResponse _post(String path, Object body) throws IOException {
    log("POST " + livepeerApiUrl + path);
    String json = mapper.writeValueAsString(body);
    StringEntity requestEntity = new StringEntity(json, ContentType.APPLICATION_JSON);
    HttpPost postMethod = new HttpPost(livepeerApiUrl + path);
    postMethod.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + livepeerApiKey);
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
  public LivepeerAPIResourceStream createStreamFromApplication(String vhost, String application, String streamName, MediaCodecInfoVideo mediaCodecInfoVideo) throws IOException {
    LivepeerAPIResourceStream body = new LivepeerAPIResourceStream(vhost, application, mediaCodecInfoVideo);
    body.setName(streamName);
    HttpResponse response = _post("/stream", body);
    int status = response.getStatusLine().getStatusCode();
    logger.info("canonical-log-line function=createStreamFromApplication " + " status=" + status);
    LivepeerAPIResourceStream info = mapper.readValue(response.getEntity().getContent(), LivepeerAPIResourceStream.class);
    if (status != 201) {
      String message = "unknown error";
      if (info.getErrors() != null && info.getErrors().size() > 0) {
        message = "";
        for (String error : info.getErrors()) {
          message += error + " ";
        }
      }
      throw new LivepeerAPIException(message);
    }
    return info;
  }

  /**
   * Get a list of usable broadcasters
   *
   * @return broadcasters
   * @throws IOException something went wrong talking to the Livepeer API
   */
  public List<LivepeerAPIResourceBroadcaster> getBroadcasters() throws IOException {
    String hardcodedBroadcaster = props.getBroadcasterUrl();
    if (hardcodedBroadcaster != null) {
      logger.info("Using hardcoded broadcaster " + hardcodedBroadcaster);
      LivepeerAPIResourceBroadcaster broadcaster = new LivepeerAPIResourceBroadcaster();
      broadcaster.setAddress(hardcodedBroadcaster);
      return Arrays.asList(broadcaster);
    }
    HttpResponse response = _get("/broadcaster");
    TypeReference typeRef = new TypeReference<List<LivepeerAPIResourceBroadcaster>>() {};
    List<LivepeerAPIResourceBroadcaster> list = mapper.readValue(response.getEntity().getContent(), typeRef);
    return list;
  }

  public LivepeerAPIResourceBroadcaster getRandomBroadcaster() throws IOException {
    Random rand = new Random();
    List<LivepeerAPIResourceBroadcaster> broadcasters = this.getBroadcasters();
    return broadcasters.get(rand.nextInt(broadcasters.size()));
  }
}
