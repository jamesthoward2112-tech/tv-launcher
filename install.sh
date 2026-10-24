#!/bin/bash
# Safe HowardTV installer: does NOT claim HOME or disable any system app.
#   ./install.sh             install and open as a normal application
#   ./install.sh --restore   uninstall HowardTV without touching Shield's stock Home
set -euo pipefail
cd "$(dirname "$0")"
PKG=uk.co.howardtv
if [[ ${1:-} == --restore ]]; then
    adb uninstall "$PKG"
    echo "HowardTV removed. Shield's system launcher was never modified."
    exit
fi
./build.sh
adb install -r HowardTV.apk
adb shell am start -n "$PKG/.HomeActivity"
echo "HowardTV opened; Shield's current Home app is unchanged."
