#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/env.sh"
cd "$ANDROID_PROJECT_ROOT"
exec ./gradlew --no-daemon :app:assembleDebug "$@"
