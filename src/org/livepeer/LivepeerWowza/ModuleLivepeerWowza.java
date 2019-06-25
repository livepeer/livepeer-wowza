package org.livepeer.LivepeerWowza;

import com.wowza.wms.stream.*;
import com.wowza.wms.stream.livetranscoder.*;
import com.wowza.wms.module.*;
import com.wowza.wms.application.*;

public class ModuleLivepeerWowza extends ModuleBase
{
	public ModuleLivepeerWowza() {
		super();
		System.out.println("constructor");
	}
	
	class TranscoderControl implements ILiveStreamTranscoderControl
	{
		public boolean isLiveStreamTranscode(String transcoder, IMediaStream stream)
		{
			// No transcoding, Livepeer is gonna take care of it
			return false;
		}
	}
	
	public void onAppStart(IApplicationInstance appInstance)
	{
		System.out.println("onAppStart?");
		getLogger().info("LIVEPEER onAppStart");
		appInstance.setLiveStreamTranscoderControl(new TranscoderControl());
		System.out.println("LIVEPEER " + appInstance.getTranscoderProperties());
	}
}