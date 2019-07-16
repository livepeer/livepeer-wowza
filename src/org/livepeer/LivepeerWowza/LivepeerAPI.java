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
	public class TranscoderInformation {
		private TranscoderAppConfig transcoderAppConfig;
		private Map<String, TranscoderTemplateAppConfig> transcoderTemplateAppConfig;
		
		public TranscoderInformation(String vhost, String application) {
			transcoderAppConfig = new TranscoderAppConfig(vhost, application);
			transcoderAppConfig.loadObject();
			transcoderTemplateAppConfig = new HashMap<>();
			for (ShortObject t : transcoderAppConfig.getTemplates().getTemplates()) {
				TranscoderTemplateAppConfig ttac = new TranscoderTemplateAppConfig(vhost, application, t.getId());
				ttac.loadObject();
				transcoderTemplateAppConfig.put(t.getId(), ttac);
			}
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
	
	public void pushTranscodeInformation(String vhost, String application) throws ClientProtocolException, IOException {
		TranscoderInformation body = new TranscoderInformation(vhost, application);
		
		

		ObjectMapper mapper = new ObjectMapper();
//			Staff staff = createStaff();
		String json = mapper.writeValueAsString(body);
		System.out.println(json);
		StringEntity requestEntity = new StringEntity(json, ContentType.APPLICATION_JSON);

		HttpPost postMethod = new HttpPost("https://3ff2c6e8.ngrok.io/api/wowza");
		postMethod.setEntity(requestEntity);

		HttpResponse rawResponse = httpClient.execute(postMethod);
		System.out.println(rawResponse.getStatusLine());
	}
}
