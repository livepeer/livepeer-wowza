#!/bin/bash

# set -o errexit
# set -o pipefail
# set -o nounset
set -e

DEV_USER="$1"

gsutil cp /Library/WowzaStreamingEngine/lib/LivepeerWowza.jar gs://livepeerjs-public/$DEV_USER/LivepeerWowza.jar
kubectl delete pod -l app=$DEV_USER --wait
until kubectl logs --tail=0 deployment/$DEV_USER; do
  echo "Waiting for pod to deploy..."
  sleep 1
done
kubectl port-forward deployment/$DEV_USER 1935:1935 8086:8086 8087:8087 8088:8088 8089:8089 &
kubectl logs -f deployment/$DEV_USER
