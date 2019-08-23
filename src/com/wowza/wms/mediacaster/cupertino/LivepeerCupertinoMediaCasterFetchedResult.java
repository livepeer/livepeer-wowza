package com.wowza.wms.mediacaster.cupertino;

/**
 * Class for representing the HTTP responses from the Livepeer API, both manifests and video segments.
 *
 * This class is provided by LivepeerWowza but requires access to fields that are package-private
 * and minified. This is relatively fragile and could easily break with new Wowza versions; it
 * will require careful automated testing whenever Wowza bumps to a new version.
 */
public class LivepeerCupertinoMediaCasterFetchedResult extends CupertinoMediaCasterFetchedResult {
  public void setResultType(int resultType) {
    a = resultType;
  }

  public void setResultCode(int resultCode) {
    b = resultCode;
  }

  public void setResultString(String resultString) {
    f = resultString;
  }

  public void setTimedOut(boolean timedOut) {
    c = timedOut;
  }

  public void setDataBlock(byte[] dataBlock) {
    h = dataBlock;
  }
}
