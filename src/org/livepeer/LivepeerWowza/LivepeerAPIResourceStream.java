package org.livepeer.LivepeerWowza;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wowza.wms.rest.ConfigBase;
import com.wowza.wms.rest.ShortObject;
import com.wowza.wms.rest.WMSResponse;
import com.wowza.wms.rest.vhosts.applications.streamfiles.StreamFileAppAction;
import com.wowza.wms.rest.vhosts.applications.streamfiles.StreamFileAppConfig;
import com.wowza.wms.rest.vhosts.applications.streamfiles.StreamFileAppConfigAdv;
import com.wowza.wms.rest.vhosts.applications.streamfiles.StreamFilesAppConfig;
import com.wowza.wms.rest.vhosts.applications.transcoder.TranscoderAppConfig;
import com.wowza.wms.rest.vhosts.applications.transcoder.TranscoderTemplateAppConfig;
import com.wowza.wms.rest.vhosts.streamfiles.StreamFileAction;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;

import java.util.*;

/**
 * Representation of LivepeerAPI's "/stream" object
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class LivepeerAPIResourceStream {
  public final static String LIVEPEER_PREFIX = "livepeer_";

  LivepeerAPIResourceStreamWowza wowza = new LivepeerAPIResourceStreamWowza();
  // Don't push up a null id on the initial create
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String id;
  private String name;
  private List<String> presets = new ArrayList<String>();
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private List<String> errors;
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

  public List<String> getErrors() {
    return errors;
  }

  public void setErrors(List<String> errors) {
    this.errors = errors;
  }

  public Map<String, String> getRenditions() {
    return renditions;
  }

  public void setRenditions(Map<String, String> renditions) {
    this.renditions = renditions;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public LivepeerAPIResourceStreamWowza getWowza() {
    return wowza;
  }

  public void setWowza(LivepeerAPIResourceStreamWowza wowza) {
    this.wowza = wowza;
  }


  /**
   * Class representing the "wowza" subfield.
   */
  @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
  public static class LivepeerAPIResourceStreamWowza {
    private Map<String, TranscoderTemplateAppConfig> transcoderTemplateAppConfig = new HashMap<>();
    private TranscoderAppConfig transcoderAppConfig;

    private List<LivepeerAPIResourceStreamWowzaStreamNameGroup> streamNameGroups = new ArrayList<>();

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

    public List<LivepeerAPIResourceStreamWowzaStreamNameGroup> getStreamNameGroups() {
      return streamNameGroups;
    }

    public void setStreamNameGroups(List<LivepeerAPIResourceStreamWowzaStreamNameGroup> streamNameGroups) {
      this.streamNameGroups = streamNameGroups;
    }
  }

  /**
   * Class representing entries in the "streamNameGroups" subfield
   */
  @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
  public static class LivepeerAPIResourceStreamWowzaStreamNameGroup {
    private String name;
    private List<String> renditions;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public List<String> getRenditions() {
      return renditions;
    }

    public void setRenditions(List<String> renditions) {
      this.renditions = renditions;
    }
  }
}
