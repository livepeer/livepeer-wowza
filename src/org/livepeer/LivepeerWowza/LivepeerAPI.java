package org.livepeer.LivepeerWowza;

import java.io.IOException;
import java.util.*;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wowza.wms.rest.ShortObject;
import com.wowza.wms.rest.vhosts.applications.transcoder.TranscoderAppConfig;
import com.wowza.wms.rest.vhosts.applications.transcoder.TranscoderTemplateAppConfig;
import com.wowza.wms.rest.vhosts.transcoder.TranscoderTemplatesConfig;

public class LivepeerAPI {
	
	@JsonAutoDetect(fieldVisibility = Visibility.ANY)
	public static class TranscoderInformation {
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
		
		public TranscoderInformation() {
			
		}
		
		public TranscoderInformation(String vhost, String application) {
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
	
	public static LivepeerAPI getInstance() {
		if (_instance == null) {
			_instance = new LivepeerAPI();
		}
		return _instance;
	}
	
	private CloseableHttpClient httpClient;
	
	public LivepeerAPI() {
		httpClient = HttpClients.createDefault();
	}
	
	public String pushTranscodeInformation(String vhost, String application) throws ClientProtocolException, IOException {
		TranscoderInformation body = new TranscoderInformation(vhost, application);

		
		ObjectMapper mapper = new ObjectMapper();
		String json = mapper.writeValueAsString(body);
		StringEntity requestEntity = new StringEntity(json, ContentType.APPLICATION_JSON);

		HttpPost postMethod = new HttpPost("https://05cbc366.ngrok.io/api/wowza");
		postMethod.setEntity(requestEntity);

		HttpResponse rawResponse = httpClient.execute(postMethod);
		TranscoderInformation info = mapper.readValue(rawResponse.getEntity().getContent(), TranscoderInformation.class);
		return info.getId();

	}
}
