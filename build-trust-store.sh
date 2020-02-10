#!/bin/bash
set -e

# Wowza's Java 8 doesn't support Let's Encrypt certs by default, so we ship our own keystore that
# trusts it.

JAVA_HOME=${1-text}
[ $# -eq 0 ] && { echo "Usage: sudo $0 \$(/usr/libexec/java_home -v '1.8*')" ; exit 1; }

KEYSTORE=$JAVA_HOME/lib/security/cacerts

curl -O https://letsencrypt.org/certs/isrgrootx1.der
curl -O https://letsencrypt.org/certs/lets-encrypt-x3-cross-signed.der
curl -O https://letsencrypt.org/certs/lets-encrypt-x4-cross-signed.der

# to be idempotent
keytool -delete -alias isrgrootx1 -keystore $KEYSTORE -storepass changeit 2> /dev/null || true
keytool -delete -alias letsencryptauthorityx3 -keystore $KEYSTORE -storepass changeit 2> /dev/null || true
keytool -delete -alias letsencryptauthorityx4 -keystore $KEYSTORE -storepass changeit 2> /dev/null || true

keytool -trustcacerts -keystore $KEYSTORE -storepass changeit -noprompt -importcert -alias isrgrootx1 -file isrgrootx1.der
keytool -trustcacerts -keystore $KEYSTORE -storepass changeit -noprompt -importcert -alias letsencryptauthorityx3 -file lets-encrypt-x3-cross-signed.der
keytool -trustcacerts -keystore $KEYSTORE -storepass changeit -noprompt -importcert -alias letsencryptauthorityx4 -file lets-encrypt-x4-cross-signed.der

rm -f isrgrootx1.der lets-encrypt-x3-cross-signed.der lets-encrypt-x4-cross-signed.der