# Livepeer Wowza Module

## How it works

### Start

* Input stream to be transcoded arrives in Wowza.
* LivepeerWowza creates a stream, sending a request to the API containing its bitrate ladder and transcoding configuration: `POST https://livepeer.live/api/stream`. Recieves back a Stream ID, e.g. `7abb1446-aca2-4220-b9f0-c7ac35cdcbee`.

### Wowza --> Livepeer loop

* LivepeerWowza requests a broadcaster from `https://livepeer.live/api/broadcaster`. This endpoint and others are running on Cloudflare Workers with geolocation logic to send them to the nearest Livepeer production cluster. It recieves back a broadcaster address, e.g. `https://aks-default-16253462-vmss000002.livepeer.live`.
* LivepeerWowza starts segmenting the input stream into MPEG-TS segments and uploading them to the broadcaster, e.g. `PUT https://aks-default-16253462-vmss000002.livepeer.live/live/7abb1446-aca2-4220-b9f0-c7ac35cdcbee/1.ts`.
* If there is a problem with the upload, LivepeerWowza requests another stream from `https://livepeer.live/api/broadcaster` and retries.

### Livepeer --> Wowza loop

* Based on the transcoding configuration, LivepeerWowza determines the URL of the HLS playlist for each transcoded rendition. Stream Files are created for each one, at URLs like `https://livepeer.live/stream/7abb1446-aca2-4220-b9f0-c7ac35cdcbee/P144p30fps16x9.m3u8`.
* These playlists are running on Cloudlfare Workers that are aware of all the broadcasters and amalgamate the `m3u8` playlists to include all available segments. It then serves out media playlists that include the full broadcaster URLs of the segments,such as:

```m3u8
#EXTM3U
#EXT-X-VERSION:3
#EXT-X-MEDIA-SEQUENCE:232212
#EXT-X-TARGETDURATION:2
#EXTINF:1.667,
https://aks-default-16253462-vmss000002.livepeer.live/live/7abb1446-aca2-4220-b9f0-c7ac35cdcbee/1.ts
#EXTINF:1.666,
https://aks-default-16253462-vmss000001.livepeer.live/live/7abb1446-aca2-4220-b9f0-c7ac35cdcbee/2.ts
#EXTINF:2.000,
https://aks-default-16253462-vmss000000.livepeer.live/live/7abb1446-aca2-4220-b9f0-c7ac35cdcbee/3.ts
```

* (Note that the broadcaster servers can change from segment to segment, providing one coherent stream even if LivepeerWowza switches broadcasters.)
* These HLS streams are stitched back together and are accessible for any functions of Wowza.
