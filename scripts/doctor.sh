#!/usr/bin/env bash
set -eu
source "$(dirname "$0")/env.sh"
java -version
sdkmanager --version
adb version
if [ -x "$ANDROID_HOME/emulator/emulator" ]; then
  emulator -version
  emulator -accel-check || true
fi
printf '\nEspace disque :\n'
df -h "$ANDROID_PROJECT_ROOT"
printf '\nAppareils Android :\n'
adb devices -l
