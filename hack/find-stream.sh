#!/bin/bash

set -x

broadcasters=$(curl https://livepeer.live/api/broadcaster | jq -r '.[].address')
for broadcaster in $broadcasters; do
  url="$broadcaster/stream/$1.m3u8"
  curl $url
done