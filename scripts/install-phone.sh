#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/env.sh"
cd "$ANDROID_PROJECT_ROOT"
adb_args=(-d)
if [ "$#" -gt 0 ]; then adb_args=(-s "$1"); fi
if [ "$(adb "${adb_args[@]}" get-state)" != device ]; then
    echo 'Branche le téléphone et autorise le débogage USB.' >&2
    exit 1
fi
./scripts/build.sh
adb "${adb_args[@]}" install -r app/build/outputs/apk/debug/app-debug.apk
adb "${adb_args[@]}" shell am start -n org.evawallpaper/.MainActivity
