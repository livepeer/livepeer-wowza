#!/bin/bash

# set -x

broadcasters=$(curl https://livepeer.live/api/broadcaster | jq -r '.[].address')
for broadcaster in $broadcasters; do
  url="$broadcaster/stream/$1.m3u8"
  if curl --fail --silent $url > /dev/null; then
    echo "$broadcaster/stream/$1.m3u8"
    echo "$broadcaster/stream/$1/P360p30fps16x9.m3u8"
    echo "$broadcaster/stream/$1/P240p30fps16x9.m3u8"
  fi
done