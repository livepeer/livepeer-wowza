#!/bin/bash

set -o pipefail
set -o nounset
set -e

DEV_USER="$1"
VERSION="$(git describe --always --tags --dirty)"
DEPLOYMENT="deployment/$DEV_USER-wowza"
TAG="$DEV_USER/livepeer-wowza:dev"

if ! kubectl get -o name $DEPLOYMENT ; then
  echo "Deployment $DEV_USER-wowza not found. Do you have a Kubernetes Wowza dev server?"
  exit 1
fi

docker build --target server -t $TAG --build-arg=version=${VERSION} .
docker push $TAG
API_REF=$(docker inspect $TAG -f '{{index .RepoDigests 0}}')
kubectl set image $DEPLOYMENT wowza=$API_REF
kubectl rollout status $DEPLOYMENT
kubectl port-forward $DEPLOYMENT 1935:1935 8086:8086 8087:8087 8088:8088 8089:8089 &
kubectl logs -f -c wowza $DEPLOYMENT
