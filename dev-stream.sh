#!/bin/bash

set -e 
set -o nounset

pod="$(kubectl get pods -l app=$1 -o name)"

# This one pushes an RTMP stream to Wowza.
kubectl exec -it $(kubectl get pods -l app=$1 -o name) -c ffmpeg -- ffmpeg -re -i http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4 -c:v copy -c:a copy -f flv rtmp://localhost/live/bbb

# This one sends an ffmpeg stream directly to the transcoder if you replace the URL at the end with a
# broadcaster that's up and running.
# kubectl exec -it -c ffmpeg $pod -- ffmpeg -re -i http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4 -c:v copy -c:a copy -f hls 'https://gke-eth-nodes-api-prod-375f3759-fxwn.livepeer.live/live/8dc16bee-41b8-4c5c-80ae-fe5e02de0c7d/bbb/manifest.m3u8'