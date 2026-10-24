#!/bin/bash
# Build a release APK without Gradle: fetch the AndroidX jars/aars, then
# aapt2 -> javac -> R8 (shrink and optimise) -> zipalign -> apksigner.
set -euo pipefail
cd "$(dirname "$0")"
SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
BT="$SDK/build-tools/34.0.0"
JAR="$SDK/platforms/android-34/android.jar"
OUT=build
MAVEN=https://dl.google.com/android/maven2

# RecyclerView 1.2.1 and its runtime dependencies, all plain Java (no Kotlin stdlib).
LIBS=(
    androidx.recyclerview:recyclerview:1.2.1:aar
    androidx.core:core:1.3.2:aar
    androidx.customview:customview:1.0.0:aar
    androidx.lifecycle:lifecycle-runtime:2.0.0:aar
    androidx.versionedparcelable:versionedparcelable:1.1.0:aar
    androidx.lifecycle:lifecycle-common:2.0.0:jar
    androidx.arch.core:core-common:2.0.0:jar
    androidx.collection:collection:1.0.0:jar
    androidx.annotation:annotation:1.1.0:jar
)
mkdir -p libs
# R8 from Maven rather than build-tools: build-tools 34's d8 also crashes on the
# method-parameter metadata javac 21 writes for enums and bridge methods.
R8=libs/r8-8.5.35.jar
[[ -f $R8 ]] || curl -sSfL -o "$R8" "$MAVEN/com/android/tools/r8/8.5.35/r8-8.5.35.jar"
for lib in "${LIBS[@]}"; do
    IFS=: read -r g a v t <<<"$lib"
    f="libs/$a-$v.$t"
    [[ -f $f ]] || curl -sSfL -o "$f" "$MAVEN/${g//.//}/$a/$v/$a-$v.$t"
done

rm -rf "$OUT" && mkdir -p "$OUT/classes" "$OUT/dex" "$OUT/aar"
CP=()           # jars to compile against and dex
RES=()          # compiled library resources
RULES=()        # keep rules shipped with the libraries
PKGS=()         # library packages that need their own R class
for f in libs/*; do
    [[ $f == "$R8" ]] && continue
    case $f in
    *.jar) CP+=("$f") ;;
    *.aar)
        d="$OUT/aar/$(basename "$f" .aar)"
        mkdir -p "$d" && unzip -q -o "$f" -d "$d"
        [[ -f $d/classes.jar ]] && CP+=("$d/classes.jar")
        [[ -f $d/proguard.txt ]] && RULES+=(--pg-conf "$d/proguard.txt")
        if [[ -d $d/res ]] && [[ -n $(ls -A "$d/res") ]]; then
            "$BT/aapt2" compile --dir "$d/res" -o "$d/res.zip"
            RES+=(-R "$d/res.zip")
            PKGS+=("$(sed -n 's/.*package="\([^"]*\)".*/\1/p' "$d/AndroidManifest.xml" | head -1)")
        fi ;;
    esac
done

"$BT/aapt2" compile --dir res -o "$OUT/res.zip"
"$BT/aapt2" link -I "$JAR" --manifest AndroidManifest.xml -A assets \
    --auto-add-overlay "${RES[@]}" --extra-packages "$(IFS=:; echo "${PKGS[*]}")" \
    --java "$OUT/gen" --proguard "$OUT/aapt-rules.txt" -o "$OUT/unsigned.apk" "$OUT/res.zip"
CLASSPATH_JARS=$(IFS=:; echo "${CP[*]}")
javac --release 11 -nowarn -Xlint:-options -classpath "$JAR:$CLASSPATH_JARS" -d "$OUT/classes" \
    $(find src "$OUT/gen" -name '*.java')
# Keeps: what the manifest and layouts name (from aapt2), the libraries' own rules, ours.
java -cp "$R8" com.android.tools.r8.R8 --release --min-api 28 --lib "$JAR" --output "$OUT/dex" \
    --pg-conf "$OUT/aapt-rules.txt" --pg-conf proguard-rules.pro "${RULES[@]}" \
    $(find "$OUT/classes" -name '*.class') "${CP[@]}"
(cd "$OUT/dex" && zip -q ../unsigned.apk classes*.dex)
"$BT/zipalign" -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

# Release key: SIGNING_KEYSTORE / SIGNING_PASSWORD from the environment (CI sets them from
# repository secrets), else from $XDG_CONFIG_HOME/tv-ambient/signing.env, so local and CI
# builds install over each other. Without one, a throwaway debug key.
ENV_FILE="${XDG_CONFIG_HOME:-$HOME/.config}/tv-ambient/signing.env"
if [[ -z ${SIGNING_KEYSTORE:-} && -r $ENV_FILE ]]; then
    # shellcheck disable=SC1090
    source "$ENV_FILE"
fi
if [[ -n ${SIGNING_KEYSTORE:-} ]]; then
    export SIGNING_PASSWORD
    "$BT/apksigner" sign --ks "$SIGNING_KEYSTORE" --ks-pass env:SIGNING_PASSWORD --out HowardTV.apk "$OUT/aligned.apk"
else
    KS=debug.keystore
    [[ -f $KS ]] || keytool -genkeypair -keystore $KS -storepass android -keypass android \
        -alias debug -keyalg RSA -validity 10000 -dname "CN=HowardTV Test" >/dev/null
    echo "warning: no release key; signing with $KS" >&2
    "$BT/apksigner" sign --ks $KS --ks-pass pass:android --out HowardTV.apk "$OUT/aligned.apk"
fi
echo "built $(pwd)/HowardTV.apk"
