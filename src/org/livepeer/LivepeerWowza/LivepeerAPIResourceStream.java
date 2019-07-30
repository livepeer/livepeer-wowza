package org.livepeer.LivepeerWowza;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wowza.wms.rest.ShortObject;
import com.wowza.wms.rest.vhosts.applications.transcoder.TranscoderAppConfig;
import com.wowza.wms.rest.vhosts.applications.transcoder.TranscoderTemplateAppConfig;

import java.util.HashMap;
import java.util.Map;

/**
 * Representation of LivepeerAPI's "/stream" object
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class LivepeerAPIResourceStream {
  LivepeerAPIResourceStreamWowza wowza = new LivepeerAPIResourceStreamWowza();
  private String id;


  /**
   * Create an empty API Resource. Mostly used by the JSON serializer when GETing objects
   */
  public LivepeerAPIResourceStream() {

  }

  /**
   * Create a Wowza resource from internal configuration, suitable for POSTing
   *
   * @param vhost
   * @param application
   */
  public LivepeerAPIResourceStream(String vhost, String application) {
    id = null;

    TranscoderAppConfig transcoderAppConfig = new TranscoderAppConfig(vhost, application);
    transcoderAppConfig.loadObject();

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

  @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
  public class LivepeerAPIResourceStreamWowza {
    private Map<String, TranscoderTemplateAppConfig> transcoderTemplateAppConfig = new HashMap<>();
    private TranscoderAppConfig transcoderAppConfig;

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
}
