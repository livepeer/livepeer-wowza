#!/bin/bash

set -e

/sbin/entrypoint.sh | tee ./wowza.log &
wowzaPid=$!

function bigText() {
  echo "========================"
  echo ""
  echo "$1"
  echo ""
  echo "========================"
}

while ! cat ./wowza.log | grep "Wowza Streaming Engine is started"; do
  sleep 2
done

bigText "Starting tests"

ffmpeg \
  -re -i ./official_test_source_2s_keys_24pfs.mp4 \
  -c:v copy -c:a copy \
  -f flv rtmp://localhost:1935/live/bbb &
ffmpegPid=$!

startTime="$(date +%s)"
chunklists="0"

set +x
while [ "$chunklists" -lt "3" ]; do
  sleep 1
  chunklists="$(curl --silent http://localhost:1935/live/smil:bbb_livepeer/manifest.m3u8 | grep chunklist | wc -l)"
done
readyTime="$(date +%s)"
startupTime="$(echo "$readyTime - $startTime" | bc)"

kill $ffmpegPid
kill -s SIGTERM $wowzaPid
pkill supervisord

wait

bigText "Start-up time: $(echo $startupTime)"