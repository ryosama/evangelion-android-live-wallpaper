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
Android 8.0 et versions ultérieures, y compris Android 12. Kotlin 2.2.21 et Canvas,
Interface Material Components ; aucun service externe. Gradle est limité à un worker
et 1 Go de mémoire pour limiter les ressources utilisées. Le compilateur Kotlin fonctionne dans
le processus Gradle pour éviter un second processus résident. Le JDK reste
nécessaire aux outils de compilation ; les sources de l’application sont
exclusivement en Kotlin, dans `app/src/main/kotlin/`.

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

Pour installer un APK déjà compilé, sans relancer la compilation :

```bash
source scripts/env.sh
adb -d install -r app/build/outputs/apk/debug/app-debug.apk
adb -d shell am start -n org.evawallpaper/.MainActivity
```

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

## Configuration

L’icône de l’application ouvre les réglages. Ils sont aussi accessibles depuis
les réglages du fond d’écran, lorsque le sélecteur Android expose cette action.

- Aperçu des quatre tuiles : **Parfait**, **OK**, **Mauvais**, **Critique**.
- Une barre Material RangeSlider à quatre segments colorés, de 0 à 100 %, avec **trois curseurs** :
  entrée dans Mauvais, OK puis Parfait. Valeurs initiales : 15, 30 et 50 %.
  Pas de 1 %, curseurs non croisables, séparation minimale de 1 point.
  Le seuil appartient toujours à l’état supérieur. Les quatre plages restent
  valides, même avec des seuils à 98, 99 et 100 %.
- **Densité des tuiles (0.6.0)** : deux curseurs règlent le minimum de tuiles
  allumées (0 à 100, défaut 10) et la proportion à batterie pleine (une tuile sur
  1 à 20, défaut 4). Zéro désactive le minimum. La capacité de la grille reste
  la limite supérieure. Le bouton Appliquer enregistre ces valeurs et ajuste
  progressivement la population, avec l’animation existante.
- **Effets des tuiles (0.7.0)** : apparition au choix entre clignotement et fondu
  entrant (fade in), disparition au choix entre clignotement et fondu sortant
  (fade out). Les deux choix sont indépendants et enregistrés avec Appliquer.
  Le clignotement reste le défaut dans les deux directions, y compris après mise
  à jour d’une ancienne configuration. Un relais déjà commencé conserve ses effets ;
  le nouveau choix est utilisé au suivant.
- Chaque état possède une couleur (sélecteur RGB ou code hexadécimal) et un
  texte optionnel de 24 caractères maximum. Le texte est ajusté à la tuile.
  Le bouton de couleur reflète la couleur sélectionnée dès sa validation, avec
  un texte noir ou blanc et une bordure contrastée pour rester lisible.
- **Appliquer** enregistre les réglages et actualise le fond installé.
- **Prévisualiser / choisir ce fond** enregistre et ouvre le sélecteur Android.
- **Restaurer les valeurs par défaut** restaure le brouillon ; toucher Appliquer
  pour enregistrer cette restauration.

Les modifications non appliquées survivent à une rotation de l’écran. Les réglages
appliqués sont conservés localement et restaurés au redémarrage. Les anciennes
configurations sans réglages de densité conservent leurs couleurs, textes et seuils,
avec le minimum 10 et la proportion 4. La restauration des valeurs par défaut
réinitialise également ces deux nouveaux curseurs. Les quatre PNG
originaux sont utilisés pour les valeurs par défaut ; les tuiles personnalisées
reprennent leur motif sans texte, recoloré, puis leur propre texte est dessiné.
L’espacement reste de 3 pixels. Les noms des états sont des libellés de configuration,
ils ne remplacent pas les textes WARNING/EMERGENCY présents par défaut sur les tuiles.

La validation sur téléphone doit couvrir les trois curseurs (y compris leurs
limites), Appliquer, les couleurs/textes, la rotation et le retour au fond d’écran.

## Mosaïque de batterie

Les hexagones fournis dans `inspiration/` occupent une grille en nid
d’abeilles (colonnes décalées), sur fond noir. Largeur actuelle : 96 dp. L’espace noir entre
les faces colorées est de **3 pixels physiques**, quelle que soit la densité.
Le calcul tient compte du contour noir des SVG. Les centres retenus sont dans la surface visible ; certaines tuiles sont
partiellement coupées aux bords. Textes WARNING et EMERGENCY conservés.

| Batterie | Tuile |
| --- | --- |
| 50 à 100 % | Verte |
| 30 à moins de 50 % | Jaune |
| 15 à moins de 30 % | Orange, WARNING |
| 0 à moins de 15 % | Rouge, EMERGENCY |

Les mises à jour de batterie sont écoutées seulement quand le fond est visible.
Une nouvelle tranche actualise le motif, et chaque changement de niveau recalcule
le nombre de tuiles, même dans la même tranche. Le niveau courant est relu au
retour à l’écran. Une lecture invalide conserve le dernier état connu (fond noir
avant la première lecture valide).

### Densité et animation fluorescente (0.5.0)

Pour `N` emplacements visibles, le nombre cible est :

```text
min(N, max(minimum, arrondi(N × pourcentage_batterie / (100 × proportion))))
```

Avec les valeurs par défaut (minimum 10, proportion 4), à 100 %, environ une
tuile sur quatre est allumée ; à 50 %, une sur huit ;
à 25 %, une sur seize. Le minimum de dix prévaut lorsque le calcul donne moins.
Par exemple, sur une grille de 60 emplacements : 15 tuiles à 100 %, puis au
moins 10 à 50 %. Une surface possédant moins de dix emplacements les utilise tous.

Les positions initiales sont tirées au hasard. Après une pause aléatoire de
1,8 à 4,2 secondes, une tuile quitte sa position pour un emplacement libre.
Le relais dure entre 0,85 et 1,35 seconde. Le clignotement utilise des impulsions
et coupures irrégulières rappelant un starter fluorescent. Le fondu utilise une
progression douce, continue et monotone de l’opacité. Chaque direction suit son
effet choisi ; lorsque les deux effets sont identiques, les opacités de l’ancienne
et de la nouvelle position sont complémentaires. La première s’éteint pendant que
la seconde s’allume. Pendant ce relais, une position supplémentaire peut être
partiellement allumée ; le nombre reste au moins égal à la cible en régime stable. Les diminutions et
augmentations de densité se font également par clignotement, une tuile à la fois.

Le moteur dessine à 25 images/s pendant les transitions uniquement. Entre deux
relais, il attend la prochaine échéance sans boucle de rendu. L’animation et les
callbacks sont suspendus dès que le fond est caché ou sa surface détruite, puis
reprennent sans rattrapage des animations manquées. Couleurs, textes et seuils
personnalisés restent conservés.

Les PNG transparents de 512 pixels sont exportés fidèlement des SVG, recadrés
sur le dessin, et stockés dans `drawable-nodpi`. Pour les régénérer avec Inkscape :

```bash
./scripts/export-tiles.sh
```

Compilation et vérifications :

```bash
source scripts/env.sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Les tests couvrent les frontières 50/49,9, 30/29,9, 15/14,9 %, les lectures
invalides et l’espacement dans les trois directions du pavage à plusieurs densités.
Les tests vérifient aussi la densité, le minimum pendant les clignotements, les
changements de niveau, les destinations distinctes et la pause/reprise.
Les tests couvrent aussi les quatre combinaisons d’effets, les bornes et la
progression du fondu, et le changement de choix pendant un relais. La couleur
des boutons et les nouveaux choix d’effets de la 0.7.0 restent à vérifier sur
le téléphone.

## Références officielles

- [RangeSlider à plusieurs curseurs](https://developer.android.com/reference/com/google/android/material/slider/RangeSlider)

- [Outils Android](https://developer.android.com/studio#command-tools)
- [Prérequis Android Studio](https://developer.android.com/studio/install)
- [Accélération de l’émulateur](https://developer.android.com/studio/run/emulator-acceleration)
- [Déploiement USB](https://developer.android.com/studio/run/device?hl=fr)
- [Configuration Kotlin et compatibilité Gradle](https://kotlinlang.org/docs/gradle-configure-project.html)
- [Compatibilité AGP 8.9](https://developer.android.com/build/releases/agp-8-9-0-release-notes)

## Validation

Compiler l’APK, exécuter les tests et l’analyse Android avant installation.
Vérifier le rendu et le cycle de vie du fond sur un appareil compatible.
