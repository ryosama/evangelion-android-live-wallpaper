# Evangelion Live Wallpaper

Application Android privée, installée directement par USB, sans Google Play.
Compatibilité : Android 8.0 (API 26) et versions ultérieures.

## Environnement local

Les outils sont installés dans `.tools/`, exclus de Git. Aucun changement du shell global.
Sur une nouvelle machine Linux x86_64 avec `curl`, `unzip` et `tar`, lancer
`./scripts/setup.sh` puis accepter les licences Android présentées. Les archives
Java et outils Android sont vérifiées par SHA-256. Prévoir plusieurs Go libres
et un accès Internet pour les dépendances de la première compilation.

```bash
source scripts/env.sh
./scripts/doctor.sh
./scripts/build.sh
```

Java Temurin 21, Gradle 8.11.1 (wrapper avec SHA-256), Android Gradle Plugin
8.9.2, SDK de compilation 35 et Build Tools 35.0.0. Application compatible
Android 8.0 et versions ultérieures, y compris Android 12. Java natif et Canvas,
sans bibliothèque graphique ni service externe. Gradle est limité à un worker
et 1 Go de mémoire pour limiter les ressources utilisées.

Le SDK de compilation 35 n’impose pas Android 15 au téléphone : `minSdk 26`
définit la version Android minimale.

## Installer sur un appareil Android

1. Dans **Paramètres → À propos du téléphone**, toucher sept fois le numéro
   de build pour activer les options développeur.
2. Activer **Débogage USB** dans les options développeur.
3. Connecter un câble USB de données, déverrouiller le téléphone et accepter
   l’autorisation de débogage pour cet ordinateur.
4. Depuis le répertoire du projet :

```bash
source scripts/env.sh
adb devices -l
./scripts/install-phone.sh
```

Le script compile, installe l’APK puis ouvre l’application. Toucher
« Prévisualiser le fond animé », puis utiliser la confirmation Android pour
l’appliquer. Le script cible un téléphone USB ; passer son numéro de série
comme argument si plusieurs appareils sont branchés.

L’APK est dans `app/build/outputs/apk/debug/app-debug.apk`. Il est signé avec
la clé de développement automatiquement créée par les outils Android.
`adb install -r` met à jour l’application en conservant ses données, si la
signature est identique. Aucune publication ni compte Google Play nécessaire.
Pour une distribution interne durable, il faudra une clé de signature dédiée,
conservée hors Git et sauvegardée ; ce prototype utilise seulement la signature debug.

Si `adb devices` indique `unauthorized`, accepter la demande sur le téléphone.
Si la liste est vide, vérifier le câble et le mode USB. Sous Linux, installer les règles udev Android si nécessaire.

## Émulateur

Un émulateur Android nécessite la virtualisation matérielle et KVM sous Linux.
Sur une machine compatible disposant de suffisamment d’espace :

```bash
./scripts/emulator.sh
# Dans un second terminal, après le démarrage d’Android :
source scripts/env.sh
./scripts/build.sh
adb -e install -r app/build/outputs/apk/debug/app-debug.apk
adb -e shell am start -n org.evawallpaper/.MainActivity
```

Le script crée à la demande un appareil Android 12 (API 31). L’image système
n’est pas téléchargée sur une machine sans accélération, pour économiser
plusieurs Go. Ne pas considérer l’émulateur comme validé tant qu’il n’a pas démarré.

## Prototype

Le lanceur ouvre la prévisualisation d’un `WallpaperService`. Le fond de test
contient un disque violet et une ligne verte animés à environ 20 images/s.
L’animation s’arrête quand le fond n’est pas visible ou que sa surface est détruite.
Ce n’est pas encore le visuel Evangelion définitif. Aucune permission Internet.

```bash
source scripts/env.sh
./gradlew :app:lintDebug
adb logcat -s AndroidRuntime
```

## Références officielles

- [Outils Android](https://developer.android.com/studio#command-tools)
- [Prérequis Android Studio](https://developer.android.com/studio/install)
- [Accélération de l’émulateur](https://developer.android.com/studio/run/emulator-acceleration)
- [Déploiement USB](https://developer.android.com/studio/run/device?hl=fr)
- [Compatibilité AGP 8.9](https://developer.android.com/build/releases/agp-8-9-0-release-notes)

## Validation

Compiler l’APK, exécuter les tests et l’analyse Android avant installation.
Vérifier le rendu et le cycle de vie du fond sur un appareil compatible.
