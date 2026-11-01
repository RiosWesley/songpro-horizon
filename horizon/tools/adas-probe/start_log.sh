#!/bin/sh
# Starts the read-only ADAS logger on the head unit, detached, so it keeps logging after adb (and
# the laptop) disconnects. Pull the log afterwards with: adb pull /data/local/tmp/adas-probe-drive.log
#
#   ./start_log.sh <seconds> <adb-serial>      (default 1500 s = 25 min)
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
SECONDS_TO_LOG=${1:-1500}
SER=${2:?"usage: start_log.sh [seconds] <adb-serial, e.g. ip-of-the-head-unit:5555>"}
adb -s "$SER" push "$HERE/build/adas-probe.dex" /data/local/tmp/adas-probe.dex >/dev/null
adb -s "$SER" shell "date > /data/local/tmp/adas-probe-drive.log; \
  (setsid nohup sh -c 'CLASSPATH=/data/local/tmp/adas-probe.dex app_process / AdasProbe $SECONDS_TO_LOG \
  >> /data/local/tmp/adas-probe-drive.log 2>&1' >/dev/null 2>&1 &)"
sleep 3
adb -s "$SER" shell "tail -n 2 /data/local/tmp/adas-probe-drive.log | cut -c1-160"
echo "logging for $SECONDS_TO_LOG s on the head unit"
