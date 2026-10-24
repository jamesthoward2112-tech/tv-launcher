#!/bin/bash
# HowardTV installs as the default HOME app, without disabling Nvidia's launcher.
#   ./install.sh                 build, install, and choose HowardTV as Home
#   ./install.sh --set-home      choose an already-installed HowardTV as Home
#   ./install.sh --install-only  install without changing the current Home
#   ./install.sh --restore       uninstall HowardTV; Nvidia Home remains installed
set -euo pipefail
cd "$(dirname "$0")"
PKG=uk.co.howardtv
if [[ ${1:-} == --restore ]]; then
    adb uninstall "$PKG"
    echo "HowardTV removed. Nvidia's original Home remains installed."
    exit
fi
if [[ ${1:-} == --set-home ]]; then
    adb shell cmd package set-home-activity "$PKG/.HomeActivity"
    echo "HowardTV selected as the default Home launcher."
    exit
fi
./build.sh
adb install -r HowardTV.apk
if [[ ${1:-} != --install-only ]]; then
    adb shell cmd package set-home-activity "$PKG/.HomeActivity"
fi
adb shell am start -n "$PKG/.HomeActivity"
echo "HowardTV opened. Nvidia's original Home has not been disabled."
