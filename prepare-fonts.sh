#!/bin/bash
# Obtain the personal-use Lemon Milk font from its original distribution.
# A locally supplied font takes priority. Do not commit or republish the raw OTF.
set -euo pipefail
cd "$(dirname "$0")"
FONT=assets/fonts/LEMONMILK-Regular.otf
[[ -s "$FONT" ]] && exit 0
TEMP_DIR=$(mktemp -d)
trap 'rm -rf "$TEMP_DIR"' EXIT
curl -fLsS 'https://dl.dafont.com/dl/?f=lemon_milk' -o "$TEMP_DIR/lemon_milk.zip"
unzip -p "$TEMP_DIR/lemon_milk.zip" LEMONMILK-Regular.otf > "$FONT"
[[ -s "$FONT" ]] || { echo "Lemon Milk font download failed" >&2; exit 1; }
