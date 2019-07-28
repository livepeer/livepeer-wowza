package org.livepeer.LivepeerWowza;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.wowza.wms.logging.WMSLogger;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wowza.wms.rest.ShortObject;
import com.wowza.wms.rest.vhosts.applications.transcoder.TranscoderAppConfig;
import com.wowza.wms.rest.vhosts.applications.transcoder.TranscoderTemplateAppConfig;
import org.apache.http.ssl.SSLContexts;

import javax.net.ssl.SSLContext;

public class LivepeerAPI {

    private static String LIVEPEER_API_URL = "https://livepeer.live/api";

    @JsonAutoDetect(fieldVisibility = Visibility.ANY)
    public static class LivepeerAPIResourceBroadcaster {
        private String address;
        private TranscoderAppConfig transcoderAppConfig;
        private Map<String, TranscoderTemplateAppConfig> transcoderTemplateAppConfig;

        public String getAddress() {
            return address;
        }

        public void setAddress(String _address) {
            address = _address;
        }

        public LivepeerAPIResourceBroadcaster() {

        }

    }

    @JsonAutoDetect(fieldVisibility = Visibility.ANY)
    public static class LivepeerAPIResourceWowza {
        private String id;
        private TranscoderAppConfig transcoderAppConfig;
        private Map<String, TranscoderTemplateAppConfig> transcoderTemplateAppConfig;

        public String getId() {
            return id;
        }

        public void setId(String _id) {
            id = _id;
        }

        public TranscoderAppConfig getTranscoderAppConfig() {
            return transcoderAppConfig;
        }

        public void setTranscoderAppConfig(TranscoderAppConfig tac) {
            transcoderAppConfig = tac;
        }

        public Map<String, TranscoderTemplateAppConfig> getTranscoderTemplateAppConfig() {
            return transcoderTemplateAppConfig;
        }

        public void setTranscoderTemplateAppConfig(Map<String, TranscoderTemplateAppConfig> ttac) {
            transcoderTemplateAppConfig = ttac;
        }

        /**
         * Create an empty API Resource. Mostly used by the JSON serializer when GETing objects
         */
        public LivepeerAPIResourceWowza() {

        }

        /**
         * Create a Wowza resource from internal configuration, suitable for POSTing
         *
         * @param vhost
         * @param application
         */
        public LivepeerAPIResourceWowza(String vhost, String application) {
            id = null;
            transcoderAppConfig = new TranscoderAppConfig(vhost, application);
            transcoderAppConfig.loadObject();
            transcoderTemplateAppConfig = new HashMap<>();
            for (ShortObject t : transcoderAppConfig.getTemplates().getTemplates()) {
                TranscoderTemplateAppConfig ttac = new TranscoderTemplateAppConfig(vhost, application, t.getId());
                ttac.loadObject();
                transcoderTemplateAppConfig.put(t.getId(), ttac);
            }
            ObjectMapper mapper = new ObjectMapper();

        }
    }

    private static LivepeerAPI _instance;

    private CloseableHttpClient httpClient;
    private ObjectMapper mapper;
    private WMSLogger logger;

    public LivepeerAPI() {
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

    private HttpResponse _execute(HttpGet req) throws IOException {
        log("" + req.getRequestLine());
        HttpResponse res = httpClient.execute(req);
        log("" + res.getStatusLine());
        return res;
    }

    private HttpResponse _get(String path) throws IOException {
        HttpGet getMethod = new HttpGet(LIVEPEER_API_URL + path);
        return httpClient.execute(getMethod);
    }

    private HttpResponse _post(String path, Object body) throws IOException {
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
    public LivepeerAPIResourceWowza createWowzaFromApplication(String vhost, String application) throws IOException {
        LivepeerAPIResourceWowza body = new LivepeerAPIResourceWowza(vhost, application);
        HttpResponse response = _post("/wowza", body);
        LivepeerAPIResourceWowza info = mapper.readValue(response.getEntity().getContent(), LivepeerAPIResourceWowza.class);
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
        TypeReference typeRef = new TypeReference<List<LivepeerAPIResourceBroadcaster>>() {
        };
        List<LivepeerAPIResourceBroadcaster> list = mapper.readValue(response.getEntity().getContent(), typeRef);
        return list;
    }
}
