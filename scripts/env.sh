#!/usr/bin/env bash
# À charger avec : source scripts/env.sh
ANDROID_PROJECT_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
export JAVA_HOME="$ANDROID_PROJECT_ROOT/.tools/jdk"
export ANDROID_HOME="$ANDROID_PROJECT_ROOT/.tools/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_USER_HOME="$ANDROID_PROJECT_ROOT/.tools/android-user"
export ANDROID_AVD_HOME="$ANDROID_USER_HOME/avd"
export GRADLE_USER_HOME="$ANDROID_PROJECT_ROOT/.tools/gradle-user"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
