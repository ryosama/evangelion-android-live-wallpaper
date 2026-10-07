#!/usr/bin/env bash
# Affiche les versions, la disponibilité KVM, le disque et les appareils ADB ; ne compile ni n’installe l’application.
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
