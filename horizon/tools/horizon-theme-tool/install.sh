#!/bin/sh
# OUTDATED: written before the tests on the car. On SX309 `restore` does nothing (RESOURCE_IS_SAME)
# and a `load` only shows after `adb reboot fission-all`. Use the app's BYD panel, or follow
# horizon/RECUPERACAO.md ("Observado no carro" and section 5).
#
# PRINTS the adb commands to put the Horizon carrier and the tool on the car. It executes NOTHING
# on the car (no adb call is made here). Run the printed lines yourself, one at a time.
#
#   ./install.sh /path/to/OFFLINE_ONLY_horizon_sx309_88.rcc <adb-serial>
#
# The selectable name is Di3F_DynastyOceanTranslate_V0001_TranslateHorizon_<nonce>.rcc where the
# nonce is the first 32 hex digits of the carrier's SHA-256 (name <-> content). The native matcher
# is (Di3F_.*_.*)_Translate(.*).rcc; "Horizon" keeps it apart from DashCast's TranslateDashCast_ files.
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
RCC=${1:?usage: install.sh CARRIER.rcc [adb-serial]}
SER=${2:?"usage: install.sh CARRIER.rcc <adb-serial, e.g. ip-of-the-head-unit:5555>"}
DEX=$HERE/build/horizon-theme-tool.dex
[ -f "$RCC" ] || { echo "no such carrier: $RCC" >&2; exit 2; }
[ -f "$DEX" ] || { echo "build the tool first: ./build.sh" >&2; exit 2; }
SHA=$(sha256sum "$RCC" | cut -d' ' -f1)
NAME=Di3F_DynastyOceanTranslate_V0001_TranslateHorizon_$(echo "$SHA" | cut -c1-32).rcc
SIZE=$(wc -c < "$RCC")
[ "$SIZE" -lt 4194304 ] || { echo "carrier larger than the 4 MiB bound" >&2; exit 2; }
DSHA=$(sha256sum "$DEX" | cut -d' ' -f1)
cat <<PRINT
# ---- nothing below has been executed ------------------------------------------------------------
# carrier   : $RCC
# sha256    : $SHA   ($SIZE bytes)
# name      : $NAME
# tool dex  : $DEX  sha256 $DSHA
# Preconditions: car stationary, in P, parking brake on; ADB reachable by a second path; read
# horizon/RECUPERACAO.md first.

# 1. [LE] baseline: the translate directory must not exist yet (or be empty)
adb -s $SER shell ls -la /sdcard/.cluster /sdcard/.cluster/translate

# 2. [ESCREVE] tool, outside the lookup directory
adb -s $SER push $DEX /data/local/tmp/horizon-theme-tool.dex
adb -s $SER shell sha256sum /data/local/tmp/horizon-theme-tool.dex      # expect $DSHA

# 3. [LE] read-only OEM query (cohort check + decoded selection); expect "[FACTORY DEFAULT]"
adb -s $SER shell 'CLASSPATH=/data/local/tmp/horizon-theme-tool.dex app_process / HorizonThemeTool query'

# 4. [ESCREVE] stage the carrier outside the lookup directory, verify, then one atomic rename in
adb -s $SER shell mkdir -p /sdcard/.cluster/translate /sdcard/.cluster/horizon /sdcard/.cluster/retired
adb -s $SER push $RCC /sdcard/.cluster/horizon/.incoming.rcc
adb -s $SER shell sha256sum /sdcard/.cluster/horizon/.incoming.rcc      # expect $SHA
adb -s $SER shell mv /sdcard/.cluster/horizon/.incoming.rcc /sdcard/.cluster/translate/$NAME
adb -s $SER shell ls -la /sdcard/.cluster/translate                     # exactly ONE .rcc

# 5. [ESCREVE, Binder] select it (ONE request; exit 0 settled, 2 ambiguous: observe, do not repeat)
adb -s $SER shell 'CLASSPATH=/data/local/tmp/horizon-theme-tool.dex app_process / HorizonThemeTool load $NAME --sha256 $SHA --assert-parked'

# 6. [LE] look at the cluster, then query again
adb -s $SER shell 'CLASSPATH=/data/local/tmp/horizon-theme-tool.dex app_process / HorizonThemeTool query'

# 7. [ESCREVE, Binder] undo: ask the OEM for the default selection
adb -s $SER shell 'CLASSPATH=/data/local/tmp/horizon-theme-tool.dex app_process / HorizonThemeTool restore --assert-parked'

# 8. If restore does not bring the factory panel back: RECUPERACAO.md section 5
adb -s $SER shell mv /sdcard/.cluster/translate/$NAME /sdcard/.cluster/retired/$NAME
adb -s $SER shell ls -la /sdcard/.cluster/translate /sdcard/.cluster/retired
adb -s $SER reboot fission-all                                          # ONE attempt, never repeated
PRINT
