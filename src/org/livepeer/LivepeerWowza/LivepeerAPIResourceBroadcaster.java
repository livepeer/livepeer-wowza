package org.livepeer.LivepeerWowza;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.wowza.wms.rest.vhosts.applications.transcoder.TranscoderAppConfig;
import com.wowza.wms.rest.vhosts.applications.transcoder.TranscoderTemplateAppConfig;

import java.util.Map;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class LivepeerAPIResourceBroadcaster {
    private String address;
    private TranscoderAppConfig transcoderAppConfig;
    private Map<String, TranscoderTemplateAppConfig> transcoderTemplateAppConfig;

    public LivepeerAPIResourceBroadcaster() {

    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String _address) {
        address = _address;
    }

}