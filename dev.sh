#!/bin/bash

# set -o errexit
# set -o pipefail
# set -o nounset
set -e

DEV_USER="$1"

docker build -t iameli/livepeer-wowza:$DEV_USER .
docker push iameli/livepeer-wowza:$DEV_USER
kubectl delete pod -l app=$DEV_USER --wait
until kubectl logs --tail=0 -c wowza deployment/$DEV_USER; do
  echo "Waiting for pod to deploy..."
  sleep 1
done
kubectl port-forward deployment/$DEV_USER 1935:1935 8086:8086 8087:8087 8088:8088 8089:8089 &
kubectl logs -f -c wowza deployment/$DEV_USER
