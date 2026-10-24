# HowardTV

HowardTV is James Howard's ad-free, cinema-themed Android TV launcher for the Nvidia Shield.
It is a personal customisation of [zhengnanli/tv-launcher](https://github.com/zhengnanli/tv-launcher),
retaining the upstream MIT licence and its Android TV remote-navigation foundation.

## Appearance and controls

- The approved **Luxury Home Cinema Under Starlit Mountains** artwork is shipped in the APK.
  It is never replaced by internet photos.
- Nvidia-green HowardTV logo at the upper left, clock/date and Gillingham weather at upper right.
- Eight dock tiles: **Netflix, Disney+, Prime Video, PurpleGlobe IPTV, Kodi, YouTube,
  Spotify, All Applications**.
- Installed app banners/icons are read from the Shield. Missing defaults are visibly named
  placeholders. The last All Applications tile enters a scrollable app grid.
- D-pad moves focus; **OK** launches an app; **hold OK**, then use arrows and OK to
  rearrange shortcuts and swap an installed app from the grid into the dock.
- **Back** returns from the app grid or menu to the dock.
- Up from the dock reaches the utility pills and Settings. Weather and temperature units
  can be toggled there. Weather works without device location permission; the default
  forecast uses Gillingham, Kent (coordinates can be overridden by `config.json`).

## Safe Nvidia Shield test installation

1. Download the direct `HowardTV.apk` from a tagged release or the APK from a build artifact.
2. Install with `adb install -r HowardTV.apk`, or sideload it with a file manager.
3. Open **HowardTV** from the Shield app list. Test the directional pad, all shortcuts and
   All Applications before deciding whether to use it as your Home screen.

**The install script and APK do not disable or replace the stock Nvidia launcher.** No root,
system permissions, or accessibility service setup is required. To roll back, simply use
the stock Home screen and uninstall HowardTV in Settings → Apps, or run
`adb uninstall uk.co.howardtv`.

## Build

Run `./build.sh` with a JDK and Android SDK platform/build tools 34.0.0. The script produces
`HowardTV.apk` (test-key-signed unless a signing keystore is configured). The GitHub Actions
workflow runs contract checks and builds the same APK on pushes to the project branch.
The test APK is not yet a claim of successful testing on physical Shield hardware.

## Source and licensing

The original TV launcher code and the Outfit font were supplied by the upstream project:
- [Original tv-launcher](https://github.com/zhengnanli/tv-launcher), MIT licence.
- Outfit font: SIL Open Font License (`assets/fonts/OFL.txt`).

HowardTV logos and cinema wallpaper are personal artwork supplied by the project owner.
Streaming service names and icons belong to their respective owners; icons from installed
apps are provided by those apps at runtime.
