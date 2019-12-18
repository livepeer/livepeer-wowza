package org.livepeer.LivepeerWowza;


import com.wowza.util.IPacketFragment;
import com.wowza.util.PacketFragmentList;
import org.apache.http.entity.AbstractHttpEntity;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;

/**
 * Livepeer segments as an HttpEntity suitable for PUTing with Apache HttpClient
 */
public class LivepeerSegmentEntity extends AbstractHttpEntity {

  int size = 0;
  PacketFragmentList list;

  LivepeerSegmentEntity(PacketFragmentList _list) {
    this.list = _list;
  }


  public boolean isRepeatable() {
    return false;
  }

  public long getContentLength() {
    return -1;
  }

  public boolean isStreaming() {
    return false;
  }

  public int getSize() {
    return size;
  }

  public InputStream getContent() throws IOException {
    // Should be implemented as well but is irrelevant for this case
    throw new UnsupportedOperationException();
  }

  public void writeTo(final OutputStream outstream) throws IOException {
    DataOutputStream writer = new DataOutputStream(outstream);

    Iterator<IPacketFragment> itr = list.getFragments().iterator();
    while (itr.hasNext()) {
      IPacketFragment fragment = itr.next();
      if (fragment.getLen() <= 0)
        continue;
      byte[] data = fragment.getBuffer();
      size += data.length;
      writer.write(data);
    }

    writer.flush();
  }


}
