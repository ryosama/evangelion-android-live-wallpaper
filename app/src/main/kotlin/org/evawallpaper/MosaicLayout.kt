package org.evawallpaper

import kotlin.math.sqrt

/** Géométrie des SVG fournis : hexagones réguliers à pointes latérales. */
class MosaicLayout(val tileWidth: Float) {
    companion object {
        const val TILE_WIDTH_DP = 96f
        const val GAP_PX = 3f
        // Rayon et contour extérieur des SVG Inkscape (unités du viewBox).
        private const val SVG_RADIUS = 61.510185f
        private const val SVG_STROKE = 0.865f
    }

    // Le contour noir de l'image fait déjà partie de l'espace noir visible.
    // On calcule donc l'écart depuis les faces colorées, pas depuis le PNG.
    private val outerRadius = SVG_RADIUS + SVG_STROKE / sqrt(3f)
    val coloredHeight = tileWidth * (SVG_RADIUS * sqrt(3f) - SVG_STROKE) / (2 * outerRadius)
    val rowStep = coloredHeight + GAP_PX
    val columnStep = rowStep * sqrt(3f) / 2
    val tileHeight = tileWidth * sqrt(3f) / 2

    fun columnOffset(column: Int): Float = if (column % 2 == 0) 0f else rowStep / 2

    data class Cell(val centerX: Float, val centerY: Float)

    /** Inclut les tuiles partiellement visibles, même si leur centre est hors écran. */
    fun visibleCells(width: Int, height: Int): List<Cell> = buildList {
        if (width <= 0 || height <= 0) return@buildList
        val halfWidth = tileWidth / 2
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
