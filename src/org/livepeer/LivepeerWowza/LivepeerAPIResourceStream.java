package org.livepeer.LivepeerWowza;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
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
  private String id;
  private List<String> presets = new ArrayList<String>();
  private Map<String, String> renditions = new HashMap<String, String>();
  @JsonIgnore
  private LivepeerAPI api;

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

  public void setApi(LivepeerAPI api) {
    this.api = api;
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
   * Sync this server's Stream Files to match what the server says
   */
  public void ensureStreamFiles(String streamName, String broadcaster, String vhost, String application, String appInstance) {
    StreamFilesAppConfig streamFiles = new StreamFilesAppConfig(vhost, application);
    streamFiles.loadObject();
    Map<String, String> streamFilesMustExist = new HashMap<>();
    for (String renditionName : this.getRenditions().keySet()) {
      streamFilesMustExist.put(streamName + "_" + renditionName, broadcaster + this.getRenditions().get(renditionName));
    }
    System.out.println("LIVEPEER ensuring these renditions exist: " + streamFilesMustExist);
    for (ShortObject streamFileListItem : streamFiles.getStreamFiles()) {
      String id = streamFileListItem.getId();
      StreamFileAppConfig streamFile = new StreamFileAppConfig(vhost, application, id);
      streamFile.loadObject();
      String streamFileName = streamFile.getStreamfileName();
      if (streamFilesMustExist.containsKey(streamFileName)) {
        if (streamFilesMustExist.get(streamFile.getStreamfileName()) == streamFile.getUri()) {
          System.out.println("LIVEPEER found good existing streamFile: " + streamFile.getStreamfileName());
          streamFilesMustExist.remove(streamFileName);
        }
        else {
          System.out.println("LIVEPEER found stale streamFile, deleting: " + streamFile.getStreamfileName());
          streamFile.deleteObject();
        }
      }

    }
    System.out.println("LIVEPEER creating stream files for renditions: " + streamFilesMustExist);
    for (String renditionName : streamFilesMustExist.keySet()) {
      StreamFileAppConfig streamFile = new StreamFileAppConfig();
      streamFile.setVhostName(vhost);
      streamFile.setAppName(application);
      streamFile.setStreamfileName(renditionName);
      streamFile.setUri(streamFilesMustExist.get(renditionName));
      streamFile.addToStringKeyMap("streamfileName", renditionName);
      streamFile.addToStringKeyMap("appName", application);
      streamFile.addToStringKeyMap("vhostName", vhost);
      try {
        streamFile.saveNewObject();
      } catch (ConfigBase.ConfigBaseException e) {
        e.printStackTrace();
        throw new RuntimeException(e);
      }
      System.out.println("LIVEPEER created streamFile foo " + renditionName);
      // This API takes a query string! Fun!
      List<NameValuePair> params = new ArrayList<>();
      params.add(new BasicNameValuePair("vhost", vhost));
      params.add(new BasicNameValuePair("appName", application));
      params.add(new BasicNameValuePair("appInstance", appInstance));
      params.add(new BasicNameValuePair("connectAppName", application));
      params.add(new BasicNameValuePair("connectAppInstance", appInstance));
      params.add(new BasicNameValuePair("streamfileName", renditionName));
      params.add(new BasicNameValuePair("mediaCasterType", "applehls"));
      // This is the only one I'm unsure of...
      params.add(new BasicNameValuePair("appType", "live"));
      String queryParam = URLEncodedUtils.format(params, "UTF-8");
      try {
        api.addRunningStreamFile(renditionName + ".stream");
        streamFile.connectAction(queryParam);
      }
      catch (Exception e) {
        System.out.println("LIVEPEER error starting streamfile");
        e.printStackTrace();
        System.out.println("LIVEPEER query param map: " + streamFile.getQueryParamMap());
      }
    }
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
