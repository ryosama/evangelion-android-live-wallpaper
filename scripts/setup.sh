#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/env.sh"
source "$ANDROID_PROJECT_ROOT/scripts/toolchain.env"
cd "$ANDROID_PROJECT_ROOT"
mkdir -p .tools/downloads "$ANDROID_HOME/cmdline-tools"
fetch() {
    local url="$1" output="$2" checksum="$3"
    if ! printf '%s  %s\n' "$checksum" "$output" | sha256sum --check --status 2>/dev/null; then
        curl -fL --retry 2 "$url" -o "$output"
        printf '%s  %s\n' "$checksum" "$output" | sha256sum --check
    fi
}
if [ ! -x "$JAVA_HOME/bin/java" ]; then
    fetch "$JDK_URL" .tools/downloads/jdk.tar.gz "$JDK_SHA256"
    mkdir -p "$JAVA_HOME"
    tar -xzf .tools/downloads/jdk.tar.gz --strip-components=1 -C "$JAVA_HOME"
fi
if [ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
    fetch "$CMDLINE_URL" .tools/downloads/commandline.zip "$CMDLINE_SHA256"
    unzip -qo .tools/downloads/commandline.zip -d "$ANDROID_HOME/cmdline-tools"
    mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
fi
# Présentation interactive des licences sur une nouvelle installation.
sdkmanager --licenses
sdkmanager 'platform-tools' 'platforms;android-35' 'build-tools;35.0.0' 'emulator'
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
printf '\nInstallation terminée. Compiler avec ./scripts/build.sh\n'
