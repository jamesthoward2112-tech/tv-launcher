#!/bin/bash
# Build, install, and make this the TV's home screen in place of the stock launcher.
#   ./install.sh            install and set as home
#   ./install.sh --restore  give the home screen back to the stock launcher
set -euo pipefail
cd "$(dirname "$0")"
PKG=com.ambient.launcher
STOCK=com.google.android.apps.tv.launcherx
# Google TV's setup app has a recovery screen that claims HOME at priority 1. Regular apps
# are capped at 0, and Android only honours a default home among the top-priority
# candidates, so with the stock launcher off it would win. Shell cannot disable just that
# screen, so the whole setup app goes (a factory reset brings it back).
SETUP=com.google.android.tungsten.setupwraith
if [[ ${1:-} == --restore ]]; then
    adb shell pm enable "$STOCK"
    adb shell pm enable "$SETUP"
    adb shell cmd package set-home-activity "$STOCK/.home.HomeActivity"
    adb shell input keyevent KEYCODE_HOME
    echo "stock launcher restored"
    exit
fi
./build.sh
adb install -r launcher.apk
# The Wi-Fi pill can only read the network name with location access.
adb shell pm grant "$PKG" android.permission.ACCESS_FINE_LOCATION
# TCL's TclAppBoot otherwise keeps apps it does not know from starting on their own.
adb shell appops set "$PKG" APP_AUTO_START allow
# The gear key: GearService swaps the Settings app it opens for the quick settings panel.
SVC="$PKG/$PKG.GearService"
# Always rewrite our entry: after a reinstall a stale one can linger until Android prunes it.
CUR=$(adb shell settings get secure enabled_accessibility_services | tr -d '\r')
OTHERS=$(tr ':' '\n' <<<"$CUR" | { grep -v -e '^null$' -e '^$' -e GearService || true; } | paste -sd:)
adb shell settings put secure enabled_accessibility_services "${OTHERS:+$OTHERS:}$SVC"
adb shell settings put secure accessibility_enabled 1
adb shell pm disable-user --user 0 "$STOCK"
adb shell pm disable-user --user 0 "$SETUP"
# Others still answer HOME (the Settings fallback), so pick ours explicitly. Last, since
# disabling a package re-resolves the default.
adb shell cmd package set-home-activity "$PKG/.HomeActivity"
adb shell input keyevent KEYCODE_HOME
echo "installed and set as home; undo with: ./install.sh --restore"
