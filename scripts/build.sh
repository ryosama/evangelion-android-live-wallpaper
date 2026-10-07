#!/usr/bin/env bash
# Compile l’APK debug avec le Gradle Wrapper du dépôt ; transmet les arguments supplémentaires à Gradle.
set -euo pipefail
source "$(dirname "$0")/env.sh"
cd "$ANDROID_PROJECT_ROOT"
exec ./gradlew --no-daemon :app:assembleDebug "$@"
