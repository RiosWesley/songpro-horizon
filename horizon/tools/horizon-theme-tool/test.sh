#!/bin/sh
# Offline JVM test of the payload builder/decoder against the real flatbuffers-java (nothing touches the car).
#   FLATBUFFERS_JAR=/path/flatbuffers-java-<ver>.jar ./test.sh
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
FB=${FLATBUFFERS_JAR:-$(find "$HOME/.gradle/caches" -name 'flatbuffers-java-*.jar' ! -name '*sources*' | sort | tail -1)}
[ -f "$FB" ] || { echo "set FLATBUFFERS_JAR to a flatbuffers-java jar"; exit 2; }
OUT=${TEST_OUT:-$HERE/build-test}
rm -rf "$OUT"; mkdir -p "$OUT"
javac -d "$OUT" -cp "$FB" "$HERE/src/FlatBuf.java" "$HERE/src/Policy.java" "$HERE/test/FlatBufTest.java"
echo "reference: $FB"
java -cp "$OUT:$FB" FlatBufTest
