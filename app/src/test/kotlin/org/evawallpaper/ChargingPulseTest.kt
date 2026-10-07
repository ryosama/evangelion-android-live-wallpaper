package org.evawallpaper

import org.junit.Assert.*
import org.junit.Test

class ChargingPulseTest {
    /**
     * Vérifie les extrêmes, la monotonie de chaque demi-cycle et la répétition du pulse pour plusieurs
     * réglages.
     */
    @Test fun pulseUsesConfiguredPeriodAndMinimumOpacity() {
        for (period in listOf(200, 1000, 5000)) {
            for (opacity in listOf(0, 20, 40, 100)) {
                val settings = ChargingAnimation(pulseMillis = period, minimumOpacity = opacity)
                assertEquals(1f, ChargingPulse.alpha(0, settings), 0.00001f)
                assertEquals(opacity / 100f, ChargingPulse.alpha(period / 2L, settings), 0.00001f)
                assertEquals(1f, ChargingPulse.alpha(period.toLong(), settings), 0.00001f)
                val falling = (0L..period / 2L step 10).map { ChargingPulse.alpha(it, settings) }
                assertTrue(falling.zipWithNext().all { (a, b) -> a >= b })
                val rising = (period / 2L..period.toLong() step 10).map { ChargingPulse.alpha(it, settings) }
                assertTrue(rising.zipWithNext().all { (a, b) -> a <= b })
                assertEquals(ChargingPulse.alpha(123, settings), ChargingPulse.alpha(period * 10000L + 123, settings), 0.00001f)
            }
        }
        assertEquals(0.4f, ChargingPulse.alpha(500), 0.00001f)
        assertEquals(0xFF0088FF.toInt(), ChargingAnimation().color)
    }

    /** Empêche les modèles de charge de recevoir des durées ou opacités incompatibles avec les curseurs. */

    @Test fun invalidPulseSettingsAreRejected() {
        for (period in listOf(0, 199, 250, 5001)) {
            assertThrows(IllegalArgumentException::class.java) { ChargingAnimation(pulseMillis = period) }
        }
        for (opacity in listOf(-1, 101)) {
            assertThrows(IllegalArgumentException::class.java) { ChargingAnimation(minimumOpacity = opacity) }
        }
    }

    /** Vérifie le nombre rempli à mi-parcours, durant le maintien et exactement au retour du cycle. */

    @Test fun fillThenHoldThenResetWithCustomDurations() {
        for (settings in listOf(ChargingAnimation(), ChargingAnimation(2, 3), ChargingAnimation(30, 60))) {
            val fill = settings.fillSeconds * 1000L
            val end = fill + settings.holdSeconds * 1000L
            assertEquals(0, ChargingPulse.filledCount(0, 60, settings))
            assertEquals(30, ChargingPulse.filledCount(fill / 2, 60, settings))
            assertEquals(60, ChargingPulse.filledCount(fill, 60, settings))
            assertEquals(60, ChargingPulse.filledCount(end - 1, 60, settings))
            assertEquals(0, ChargingPulse.filledCount(end, 60, settings))
            assertEquals(0, ChargingPulse.filledCount(end * 1234, 60, settings))
            assertEquals(0, ChargingPulse.filledCount(fill, 0, settings))
        }
    }

    /** Le maintien nul redémarre sans pause ; les durées hors bornes sont rejetées à la construction. */

    @Test fun zeroHoldRestartsImmediatelyAndDurationsAreValidated() {
        assertEquals(0, ChargingPulse.filledCount(1000, 10, ChargingAnimation(1, 0)))
        for ((fill, hold) in listOf(0 to 5, 31 to 5, 4 to -1, 4 to 61)) {
            assertThrows(IllegalArgumentException::class.java) { ChargingAnimation(fill, hold) }
        }
    }

    /** Vérifie que le balayage ignore les cellules éteintes et départage une même hauteur de gauche à droite. */

    @Test fun sweepOrdersOnlyLitCellsFromBottomToTop() {
        val cells = listOf(MosaicLayout.Cell(0f, 0f), MosaicLayout.Cell(0f, 100f),
            MosaicLayout.Cell(50f, 100f), MosaicLayout.Cell(0f, 50f))
        val lights = listOf(0, 2, 1).map { TilePopulation.Light(it, 1f) }
        assertEquals(listOf(1, 2, 0), ChargingPulse.bottomToTop(cells, lights))
    }
}
