#!/usr/bin/env bash
# Définit l’environnement local commun aux scripts sans installer ni télécharger quoi que ce soit.
# À charger avec : source scripts/env.sh
# Racine calculée depuis ce script, même quand env.sh est chargé depuis un autre dossier.
ANDROID_PROJECT_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
# JDK local utilisé par Gradle et les outils Android.
export JAVA_HOME="$ANDROID_PROJECT_ROOT/.tools/jdk"
# Racine du SDK installée par setup.sh.
export ANDROID_HOME="$ANDROID_PROJECT_ROOT/.tools/android-sdk"
# Alias de compatibilité pointant vers le même SDK.
export ANDROID_SDK_ROOT="$ANDROID_HOME"
# Préférences et clés de développement Android locales, exclues du dépôt.
export ANDROID_USER_HOME="$ANDROID_PROJECT_ROOT/.tools/android-user"
# Images et configuration des appareils virtuels du projet.
export ANDROID_AVD_HOME="$ANDROID_USER_HOME/avd"
# Cache de dépendances et distributions Gradle local au projet.
export GRADLE_USER_HOME="$ANDROID_PROJECT_ROOT/.tools/gradle-user"
# Priorité aux outils du projet ; conserve les commandes système déjà accessibles.
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
