#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/env.sh"
if ! emulator -accel-check; then
    echo 'Émulateur accéléré indisponible : virtualisation/KVM nécessaire. Utilise le téléphone USB sur cette machine.' >&2
    exit 1
fi
if ! emulator -list-avds | grep -qx 'Eva_API_31'; then
    sdkmanager 'system-images;android-31;default;x86_64'
    echo no | avdmanager create avd --name Eva_API_31 --package 'system-images;android-31;default;x86_64'
fi
exec emulator -avd Eva_API_31 -no-snapshot -memory 1536 "$@"
