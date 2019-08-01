package org.livepeer.LivepeerWowza;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wowza.wms.rest.ShortObject;
import com.wowza.wms.rest.vhosts.applications.transcoder.TranscoderAppConfig;
import com.wowza.wms.rest.vhosts.applications.transcoder.TranscoderTemplateAppConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Representation of LivepeerAPI's "/stream" object
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class LivepeerAPIResourceStream {
  LivepeerAPIResourceStreamWowza wowza = new LivepeerAPIResourceStreamWowza();
  private String id;
  private List<String> presets = new ArrayList<String>();
  private Map<String, String> renditions = new HashMap<String, String>();

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
    wowza.setTranscoderAppConfig(transcoderAppConfig);

    for (ShortObject t : transcoderAppConfig.getTemplates().getTemplates()) {
      TranscoderTemplateAppConfig ttac = new TranscoderTemplateAppConfig(vhost, application, t.getId());
      ttac.loadObject();
      wowza.getTranscoderTemplateAppConfig().put(t.getId(), ttac);
    }
  }

  /**
   * Get the id of this stream
   * @return id of the stream
   */
  public String getId() {
    return id;
  }

  /**
   * Set the id of the stream
   * @param _id
   */
  public void setId(String _id) {
    id = _id;
  }

  public List<String> getPresets() {
    return presets;
  }

  public void setPresets(List<String> presets) {
    this.presets = presets;
  }

  public Map<String, String> getRenditions() {
    return renditions;
  }

  public void setRenditions(Map<String, String> renditions) {
    this.renditions = renditions;
  }

  /**
   * Class represending the "wowza" subfield. This probably could have been a ShortObject or something
   * but this works fine.
   */
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
