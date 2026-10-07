package org.evawallpaper

import kotlin.math.sqrt

/**
 * Géométrie des SVG à pointes latérales. tileWidth est la largeur totale d’une tuile en pixels physiques,
 * fournie par le service.
 */
class MosaicLayout(val tileWidth: Float) {
    companion object {
        // Largeur de référence en dp ; le service la convertit en pixels avant de créer la géométrie.
        const val TILE_WIDTH_DP = 96f
        // Espacement des faces colorées en pixels physiques, indépendant de la densité Android.
        const val GAP_PX = 3f
        // Rayon et contour extérieur des SVG Inkscape (unités du viewBox).
        // Rayon du motif SVG source, utilisé pour conserver ses proportions.
        private const val SVG_RADIUS = 61.510185f
        // Épaisseur du trait noir extérieur du SVG ; entre déjà dans l’écart visible.
        private const val SVG_STROKE = 0.865f
    }

    // Le contour noir de l'image fait déjà partie de l'espace noir visible.
    // On calcule donc l'écart depuis les faces colorées, pas depuis le PNG.
    // Rayon extérieur incluant le contour, dans les unités du SVG.
    private val outerRadius = SVG_RADIUS + SVG_STROKE / sqrt(3f)
    // Hauteur en pixels de la face colorée, sans le contour noir.
    val coloredHeight = tileWidth * (SVG_RADIUS * sqrt(3f) - SVG_STROKE) / (2 * outerRadius)
    // Distance verticale entre centres de deux tuiles d’une même colonne.
    val rowStep = coloredHeight + GAP_PX
    // Distance horizontale entre colonnes décalées, issue de la géométrie hexagonale.
    val columnStep = rowStep * sqrt(3f) / 2
    // Hauteur totale du bitmap mis à l’échelle, en pixels.
    val tileHeight = tileWidth * sqrt(3f) / 2

    /** Décale verticalement une colonne sur deux d’une demi-rangée pour former le pavage en nid d’abeilles. */
    fun columnOffset(column: Int): Float = if (column % 2 == 0) 0f else rowStep / 2

    /** Centre en pixels dans le repère de la surface ; peut déborder pour une tuile partiellement visible. */
    data class Cell(val centerX: Float, val centerY: Float)

    /**
     * Construit les centres en pixels pour la surface du service, y compris les tuiles partiellement
     * visibles à droite/en bas. Une surface vide ne produit aucune cellule.
     */
    fun visibleCells(width: Int, height: Int): List<Cell> = buildList {
        if (width <= 0 || height <= 0) return@buildList
        // Demi-largeur utilisée pour inclure les tuiles coupées par le bord droit.
        val halfWidth = tileWidth / 2
        // Demi-hauteur utilisée pour tester l’intersection avec les bords haut/bas.
        val halfHeight = tileHeight / 2
        var column = 0
        while (column * columnStep - halfWidth < width) {
            val x = column * columnStep
            var y = columnOffset(column) - rowStep
            while (y - halfHeight < height) {
                if (y + halfHeight > 0) add(Cell(x, y))
                y += rowStep
            }
            column++
        }
    }
}
