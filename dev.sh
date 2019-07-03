#!/bin/bash

# set -o errexit
# set -o pipefail
# set -o nounset
set -e

gsutil cp /Library/WowzaStreamingEngine/lib/LivepeerWowza.jar gs://livepeerjs-public/eli/LivepeerWowza.jar
kubectl delete pod -l app=wowza --wait
until kubectl logs --tail=0 deployment/wowza; do
  echo "Waiting for pod to deploy..."
  sleep 1
done
kubectl port-forward deployment/wowza 1935:1935 8086:8086 8087:8087 8088:8088 8089:8089 &
kubectl logs -f deployment/wowza
