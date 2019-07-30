package org.livepeer.LivepeerWowza;
/*
 * This code and all components (c) Copyright 2018, Wowza Media Systems, LLC. All rights reserved.
 * This code is licensed pursuant to the BSD 3-Clause License.
 */


import java.io.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.UUID;

import com.wowza.util.IPacketFragment;
import com.wowza.util.PacketFragmentList;
import com.wowza.wms.manifest.model.m3u8.MediaSegmentModel;
import com.wowza.wms.manifest.model.m3u8.PlaylistModel;
import com.wowza.wms.pushpublish.protocol.cupertino.PushPublishHTTPCupertino;
import com.wowza.wms.server.LicensingException;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.AbstractHttpEntity;

public class PushPublishHTTPCupertinoLivepeerHandler extends PushPublishHTTPCupertino {

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

  protected String basePath = "live/";
  protected String httpAddress;
  protected HttpClient httpClient;

  boolean backup = false;
  String groupName = null;
  private int connectionTimeout = 5000;
  private int readTimeout = 5000;

  public PushPublishHTTPCupertinoLivepeerHandler(String broadcaster) throws LicensingException {
    super();
    httpAddress = broadcaster;
    System.out.println("LIVEPEER PushPublishHTTPCupertinoLivepeerHandler constructor");
    basePath += UUID.randomUUID() + "/";
  }

  public void setHttpClient(HttpClient client) {
    httpClient = client;
  }

  @Override
  public void load(HashMap<String, String> dataMap) {
    System.out.println("LIVEPEER PushPublishHTTPCupertinoLivepeerHandler load " + dataMap);
    super.load(dataMap);
  }

  /**
   * No-op in the context of Livepeer. We get all necessary data from the segments themselves.
   *
   * @param groupName      not used
   * @param masterPlaylist not used
   * @return true
   */
  @Override
  public boolean updateGroupMasterPlaylistPlaybackURI(String groupName, PlaylistModel masterPlaylist) {
    return true;
  }

  /**
   * No-op in the context of Livepeer.
   *
   * @param playlist not used
   * @return true
   */
  @Override
  public boolean updateMasterPlaylistPlaybackURI(PlaylistModel playlist) {
    return true;
  }

  /**
   * No-op in the context of Livepeer.
   *
   * @param playlist not used
   * @return true
   */
  @Override
  public boolean updateMediaPlaylistPlaybackURI(PlaylistModel playlist) {
    return true;
  }

  /**
   * No-op in the context of Livepeer.
   *
   * @param mediaSegment not used
   * @return true
   */
  @Override
  public boolean updateMediaSegmentPlaybackURI(MediaSegmentModel mediaSegment) {
    return true;
  }

  /**
   * No-op in the context of Livepeer.
   *
   * @param groupName not used
   * @param playlist  not used
   * @return 1
   */
  @Override
  public int sendGroupMasterPlaylist(String groupName, PlaylistModel playlist) {
    return 1;
  }

  /**
   * No-op in the context of Livepeer.
   *
   * @param playlist not used
   * @return 1
   */
  @Override
  public int sendMasterPlaylist(PlaylistModel playlist) {
    return 1;
  }

  /**
   * No-op in the context of Livepeer.
   *
   * @param playlist not used
   * @return 1
   */
  @Override
  public int sendMediaPlaylist(PlaylistModel playlist) {
    return 1;
  }

  /**
   * Livepeer wants "0.ts" instead of "media_0.ts", so
   */
  public String getSegmentUri(MediaSegmentModel mediaSegment) {
    return mediaSegment.getUri().toString().replace("media_", "");
  }

  /**
   * This is the important function that takes care of sending a media segment to
   * the Livepeer API.
   *
   * @param mediaSegment
   * @return
   */
  @Override
  public int sendMediaSegment(MediaSegmentModel mediaSegment) {
    String url = null;
    int size = 0;
    try {
      PacketFragmentList list = mediaSegment.getFragmentList();
      if (list != null && list.size() != 0) {
        url = httpAddress + "/" + getDestinationPath() + "/" + getSegmentUri(mediaSegment);
        LivepeerSegmentEntity entity = new LivepeerSegmentEntity(list);
        HttpPut req = new HttpPut(url);
        req.setEntity(entity);
        HttpResponse res = httpClient.execute(req);


        int status = res.getStatusLine().getStatusCode();
        size = entity.getSize();
        if (status < 200 || status >= 300)
          size = 0;
      } else
        size = 1;  // empty fragment list.
    } catch (Exception e) {
      logError("sendMediaSegment", "Failed to send media segment data to " + url.toString(), e);
      size = 0;
    }
    return size;
  }

  /**
   * No-op in the context of Livepeer. Our segments are deleted automatically after a timeout by
   * the go-livepeer server.
   *
   * @param mediaSegment media segment
   * @return 1
   */
  @Override
  public int deleteMediaSegment(MediaSegmentModel mediaSegment) {
    return 1;
  }

  /**
   * Currently a no-op in the context of the Livepeer API. Maybe
   * eventually will allow for a second API destination.
   *
   * @param backup should backup this stream? does nothing.
   */
  @Override
  public void setSendToBackupServer(boolean backup) {
    this.backup = backup;
  }

  /**
   * Currently a no-op in the context of the Livepeer API. Maybe
   * eventually will allow for a second API destination.
   *
   * @return is backup?
   */
  @Override
  public boolean isSendToBackupServer() {
    return backup;
  }

  /**
   * Currently a no-op in the context of LivepeerWowza. Potentially
   * worth implementing if it usefully reports status in the dashboard
   * or some such.
   *
   * @return true
   */
  @Override
  public boolean outputOpen() {
    return true;
  }

  /**
   * Currently a no-op in the context of LivepeerWowza. Potentially
   * worth implementing if it usefully reports status in the dashboard
   * or some such.
   *
   * @return true
   */
  @Override
  public boolean outputClose() {
    return true;
  }

  @Override
  public String getDestionationLogData() {
    return "{\"" + httpAddress + "/" + getDestinationPath() + "\"}";
  }

  private String getDestinationPath() {
    if (!backup)
      return basePath + getDstStreamName();
    return basePath + getDstStreamName() + "-b";
  }

}
