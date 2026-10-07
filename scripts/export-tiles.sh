#!/usr/bin/env bash
# Régénère les quatre PNG utilisés par TileRenderer à partir des SVG du dossier inspiration ; nécessite Inkscape.
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p app/src/main/res/drawable-nodpi
# Arguments : SVG source ($1), nom de ressource sans extension ($2).
# Recadre au dessin, conserve la transparence et fixe la largeur à 512 pixels.
export_tile() {
    inkscape "$1" --export-area-drawing --export-width=512 \
        --export-background-opacity=0 --export-filename="app/src/main/res/drawable-nodpi/$2.png"
}
export_tile 'inspiration/hexagone vert.svg' tile_green
export_tile 'inspiration/hexagone jaune.svg' tile_yellow
export_tile 'inspiration/hexagone orange warning.svg' tile_orange
export_tile 'inspiration/hexagone rouge emergency.svg' tile_red
