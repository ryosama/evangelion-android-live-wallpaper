package org.evawallpaper

import kotlin.math.PI
import kotlin.math.cos

/** Horloge relative au début de charge ; aucune dépendance au rendu Android. */
object ChargingPulse {
    fun alpha(elapsed: Long, settings: ChargingAnimation = ChargingAnimation()): Float {
        val period = settings.pulseMillis.toLong()
        val phase = elapsed.coerceAtLeast(0) % period
        val minimum = settings.minimumOpacity / 100.0
        return (minimum + (1.0 - minimum) * (1.0 + cos(2.0 * PI * phase / period)) / 2.0).toFloat()
    }

    fun filledCount(elapsed: Long, count: Int, settings: ChargingAnimation): Int {
        val fill = settings.fillSeconds * 1000L
        val cycle = fill + settings.holdSeconds * 1000L
        val phase = elapsed.coerceAtLeast(0) % cycle
        return if (phase >= fill) count else (phase * count / fill).toInt()
    }

    fun bottomToTop(cells: List<MosaicLayout.Cell>, lights: List<TilePopulation.Light>): List<Int> =
        lights.map { it.cell }.sortedWith(compareByDescending<Int> { cells[it].centerY }.thenBy { cells[it].centerX })
}
