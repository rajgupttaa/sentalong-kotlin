#!/bin/bash
# Build the Maven Central Portal upload bundle for com.sentalong:sdk.
#
# Prereqs (all true on this machine): artifacts in ~/.m2 via
#   JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
#     gradle :sdk:publishToMavenLocal
# and a GPG signing key (pinentry will prompt for its passphrase).
#
# Output: sentalong-sdk-0.1.0-bundle.zip — upload it at
# https://central.sonatype.com → Publish → Upload Component.
set -euo pipefail

VERSION="0.1.0"
SRC="$HOME/.m2/repository/com/sentalong/sdk/$VERSION"
WORK="$(mktemp -d)"
DEST="$WORK/com/sentalong/sdk/$VERSION"
OUT="$(cd "$(dirname "$0")" && pwd)/sentalong-sdk-$VERSION-bundle.zip"

mkdir -p "$DEST"
for f in "sdk-$VERSION.aar" "sdk-$VERSION.pom" "sdk-$VERSION-sources.jar" \
         "sdk-$VERSION-javadoc.jar" "sdk-$VERSION.module"; do
  [ -f "$SRC/$f" ] || { echo "missing $SRC/$f — run publishToMavenLocal first"; exit 1; }
  cp "$SRC/$f" "$DEST/"
done

cd "$DEST"
for f in *; do
  gpg --armor --detach-sign --yes "$f"
  # Central requires md5 + sha1 alongside every artifact.
  md5 -q "$f" > "$f.md5"
  shasum -a 1 "$f" | awk '{print $1}' > "$f.sha1"
done

cd "$WORK"
rm -f "$OUT"
zip -qr "$OUT" com
echo "Bundle ready: $OUT"
echo "Upload at https://central.sonatype.com → Publish → Upload Component."
