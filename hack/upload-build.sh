#!/bin/bash

# CI script for uploading builds.

set -e
set -o nounset

# Required variables:
# $GCLOUD_KEY
# $GCLOUD_SECRET
# $DISCORD_URL
# $BRANCH

version="$(git describe --tags --always --dirty)"
bucket="build.livepeer.live"
set -x

# Adapted from https://stackoverflow.com/a/44751929/990590
function doUpload() {
  FILE="$1"
  UPLOAD_PATH="$2"
  CONTENT_TYPE="$3"
  resource="/${bucket}/LivepeerWowza/${UPLOAD_PATH}"
  dateValue=`date -R`
  stringToSign="PUT\n\n${CONTENT_TYPE}\n${dateValue}\n${resource}"
  signature=`echo -en ${stringToSign} | openssl sha1 -hmac ${GCLOUD_SECRET} -binary | base64`
  curl --fail -X PUT -T "${FILE}" \
    -H "Host: storage.googleapis.com" \
    -H "Date: ${dateValue}" \
    -H "Content-Type: ${CONTENT_TYPE}" \
    -H "Authorization: AWS ${GCLOUD_KEY}:${signature}" \
    https://storage.googleapis.com${resource}
}

echo "
{
  \"version\": \"${version}\",
  \"url\": \"https://$bucket/LivepeerWowza/$BRANCH/$version/LivepeerWowza.jar\"
}
" > uploadVersion.json 

doUpload LivepeerWowza.jar "$BRANCH/$version/LivepeerWowza.jar" "application/x-compressed-tar"
doUpload uploadVersion.json "$BRANCH.json" "application/json"

curl --fail -s -H "Content-Type: application/json" -X POST -d "{\"content\": \"Build succeeded ✅\nBranch: $BRANCH\nLast commit: $(git log -1 --pretty=format:'%s by %an')\nhttps://build.livepeer.live/LivepeerWowza/$BRANCH/$version/LivepeerWowza.jar\"}" $DISCORD_URL 2>/dev/null
echo "done"
