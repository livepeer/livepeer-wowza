#!/bin/bash

while true; do
  sleep 15
  wowza="$(ps aux | grep com.wowza.wms.bootstrap.Bootstrap | awk '{ print $2 }')"
  echo "canonical-log-line function=threads threads=$(ps -o nlwp= $wowza)"
done