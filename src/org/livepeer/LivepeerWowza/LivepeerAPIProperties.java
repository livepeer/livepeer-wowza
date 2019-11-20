package org.livepeer.LivepeerWowza;

import com.wowza.wms.application.IApplicationInstance;
import com.wowza.wms.application.WMSProperties;
import com.wowza.wms.server.Server;

import java.util.Arrays;
import java.util.List;


/**
 * Helper class for Livepeer settings. All of them are of the form
 */
public class LivepeerAPIProperties {

  public static final String PROP_API_SERVER_URL = "livepeer.org/api-server-url";
  private static final String DEFAULT_API_SERVER_URL = "https://livepeer.live/api";

  public String getApiServerUrl() {
    return this.getString(PROP_API_SERVER_URL, DEFAULT_API_SERVER_URL);
  }

  public static final String PROP_BROADCASTER_URL = "livepeer.org/broadcaster-url";
  private static final String DEFAULT_BROADCASTER_URL = null;

  /**
   * If set, this overrides the default behavior to retrieve a broadcaster from API_SERVER_URL/api/broadcaster,
   * and instead just always uses this URL. Useful for testing new cloud environments.
   */
  public String getBroadcasterUrl() {
    return this.getString(PROP_BROADCASTER_URL, DEFAULT_BROADCASTER_URL);
  }

  public static final String PROP_API_KEY = "livepeer.org/api-key";
  private static final String DEFAULT_API_KEY = "no_api_key";

  public String getApiKey() {
    return this.getString(PROP_API_KEY, DEFAULT_API_KEY);
  }

  public static final String PROP_DUPLICATE_STREAMS = "livepeer.org/duplicate-streams";
  private static final boolean DEFAULT_DUPLICATE_STREAMS = false;

  public boolean getDuplicateStreams() {
    return this.getBoolean(PROP_DUPLICATE_STREAMS, DEFAULT_DUPLICATE_STREAMS);
  }

  private IApplicationInstance appInstance;

  public LivepeerAPIProperties(IApplicationInstance appInstance) {
    this.appInstance = appInstance;
  }



  /**
   * Get a boolean property
   * @param key
   * @param defaultValue
   * @return
   */
  protected boolean getBoolean(String key, boolean defaultValue) {
    for (WMSProperties location : getPropertyLocations()) {
      if (location.getProperty(key) != null) {
        return location.getPropertyBoolean(key, defaultValue);
      }
    }
    return defaultValue;
  }

  /**
   * Get a string property
   * @param key
   * @param defaultValue
   * @return
   */
  protected String getString(String key, String defaultValue) {
    for (WMSProperties location : getPropertyLocations()) {
      if (location.getProperty(key) != null) {
        return location.getPropertyStr(key, defaultValue);
      }
    }
    return defaultValue;
  }
  /**
   * Get all the places where Livepeer properties can be defined.
   * @return
   */
  protected List<WMSProperties> getPropertyLocations() {
    WMSProperties serverProps = Server.getInstance().getProperties();
    WMSProperties vHostProps = appInstance.getVHost().getProperties();
    WMSProperties applicationProps = appInstance.getProperties();
    return Arrays.asList(applicationProps, vHostProps, serverProps);
  }
}
