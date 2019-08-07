package com.wowza.wms.mediacaster.cupertino;

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
