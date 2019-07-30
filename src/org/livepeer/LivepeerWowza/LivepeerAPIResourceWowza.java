package org.livepeer.LivepeerWowza;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wowza.wms.rest.ShortObject;
import com.wowza.wms.rest.vhosts.applications.transcoder.TranscoderAppConfig;
import com.wowza.wms.rest.vhosts.applications.transcoder.TranscoderTemplateAppConfig;

import java.util.HashMap;
import java.util.Map;

/**
 * Representation of LivepeerAPI's "/wowza" object
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class LivepeerAPIResourceWowza {
  private TranscoderAppConfig transcoderAppConfig;
  private String id;
  private Map<String, TranscoderTemplateAppConfig> transcoderTemplateAppConfig;

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
}
