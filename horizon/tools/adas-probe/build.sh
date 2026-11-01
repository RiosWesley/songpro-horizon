#!/bin/sh
# Build adas-probe.dex (offline, nothing touches the car).
#   ANDROID_SDK (default ~/Android/Sdk), PLATFORM (default android-36), BUILD_TOOLS (default 36.0.0)
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
SDK=${ANDROID_SDK:-$HOME/Android/Sdk}
JAR=$SDK/platforms/${PLATFORM:-android-36}/android.jar
D8=$SDK/build-tools/${BUILD_TOOLS:-36.0.0}/d8
OUT=${OUT:-$HERE/build}
rm -rf "$OUT"; mkdir -p "$OUT/classes"
javac -source 8 -target 8 -Xlint:-options -bootclasspath "$JAR" -d "$OUT/classes" "$HERE"/src/*.java
(cd "$OUT/classes" && "$D8" --lib "$JAR" --min-api 29 --output "$OUT" $(find . -name '*.class'))
mv "$OUT/classes.dex" "$OUT/adas-probe.dex"
rm -rf "$OUT/classes"
sha256sum "$OUT/adas-probe.dex"
