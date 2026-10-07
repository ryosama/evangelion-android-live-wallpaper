package org.evawallpaper

import kotlin.math.PI
import kotlin.math.cos

/** Horloge relative au début de charge ; aucune dépendance au rendu Android. */
object ChargingPulse {
    /**
     * Renvoie l’opacité commune aux tuiles en charge (0..1). elapsed est en millisecondes depuis le début
     * du cycle ; le cosinus effectue un aller-retour doux.
     */
    fun alpha(elapsed: Long, settings: ChargingAnimation = ChargingAnimation()): Float {
        // Durée d’un aller-retour complet d’opacité en millisecondes.
        val period = settings.pulseMillis.toLong()
        val phase = elapsed.coerceAtLeast(0) % period
        // Opacité minimale convertie du pourcentage stocké vers l’intervalle 0..1.
        val minimum = settings.minimumOpacity / 100.0
        return (minimum + (1.0 - minimum) * (1.0 + cos(2.0 * PI * phase / period)) / 2.0).toFloat()
    }

    /**
     * Nombre de tuiles ayant pris la couleur de charge : progression pendant fillSeconds, maintien pendant
     * holdSeconds, puis remise à zéro. elapsed est en millisecondes.
     */
    fun filledCount(elapsed: Long, count: Int, settings: ChargingAnimation): Int {
        // Durée de remplissage convertie en millisecondes.
        val fill = settings.fillSeconds * 1000L
        // Durée totale avant retour aux couleurs normales : remplissage plus maintien.
        val cycle = fill + settings.holdSeconds * 1000L
        val phase = elapsed.coerceAtLeast(0) % cycle
        return if (phase >= fill) count else (phase * count / fill).toInt()
    }

    /**
     * Trie les indices des tuiles allumées pour le balayage du service : Y décroissant (bas en premier),
     * puis X croissant à hauteur égale.
     */
    fun bottomToTop(cells: List<MosaicLayout.Cell>, lights: List<TilePopulation.Light>): List<Int> =
        lights.map { it.cell }.sortedWith(compareByDescending<Int> { cells[it].centerY }.thenBy { cells[it].centerX })
}
