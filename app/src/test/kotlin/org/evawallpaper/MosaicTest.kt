package org.evawallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.sqrt

class MosaicTest {
    /** Vérifie les frontières exactes et les fractions de batterie sans arrondi préalable. */
    @Test fun batteryThresholdsIncludeTheirLowerBoundary() {
        val cases = listOf(
            1000 to BatteryBand.GREEN, 500 to BatteryBand.GREEN,
            499 to BatteryBand.YELLOW, 300 to BatteryBand.YELLOW,
            299 to BatteryBand.ORANGE, 150 to BatteryBand.ORANGE,
            149 to BatteryBand.RED, 0 to BatteryBand.RED,
        )
        for ((level, expected) in cases) assertEquals(expected, BatteryBand.fromLevel(level, 1000))
        assertEquals(BatteryBand.GREEN, BatteryBand.fromLevel(1, 2))
        assertEquals(BatteryBand.YELLOW, BatteryBand.fromLevel(1, 3))
    }

    /** Une mesure Android impossible doit renvoyer null, afin de préserver le dernier rendu connu. */

    @Test fun invalidBatteryReadingsDoNotSelectAColor() {
        for ((level, scale) in listOf(-1 to 100, 50 to -1, 0 to 0, 101 to 100)) {
            assertNull(BatteryBand.fromLevel(level, scale))
        }
    }

    /**
     * Vérifie l’écart entre faces colorées verticalement et en diagonale, indépendamment de la densité
     * Android.
     */

    @Test fun gapIsThreePhysicalPixelsAtEveryDensityAndInEveryDirection() {
        for (density in listOf(1f, 1.5f, 2f, 3f, 4f)) {
            val layout = MosaicLayout(96f * density)
            assertEquals(3f, layout.rowStep - layout.coloredHeight, 0.001f)
            val diagonalDistance = sqrt(layout.columnStep * layout.columnStep +
                layout.columnOffset(1) * layout.columnOffset(1))
            assertEquals(3f, diagonalDistance - layout.coloredHeight, 0.001f)
            assertEquals(0f, layout.columnOffset(2), 0f)
        }
    }
    /** Vérifie que BatteryBand respecte les seuils personnalisés de WallpaperConfig. */
    @Test fun customThresholdsDriveAllFourBands() {
        val thresholds = BatteryThresholds(10, 40, 80)
        val cases = listOf(
            0 to BatteryBand.RED, 99 to BatteryBand.RED,
            100 to BatteryBand.ORANGE, 399 to BatteryBand.ORANGE,
            400 to BatteryBand.YELLOW, 799 to BatteryBand.YELLOW,
            800 to BatteryBand.GREEN, 1000 to BatteryBand.GREEN,
        )
        for ((level, expected) in cases) {
            assertEquals(expected, BatteryBand.fromLevel(level, 1000, thresholds))
        }
    }

    /** Vérifie le contrat des seuils : plages non vides, ordonnées et dans 0..100. */

    @Test fun crossedOrEmptyRangesAreRejected() {
        for ((bad, ok, perfect) in listOf(
            Triple(0, 30, 50), Triple(15, 15, 50), Triple(30, 15, 50),
            Triple(15, 50, 50), Triple(15, 30, 101),
        )) {
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                BatteryThresholds(bad, ok, perfect)
            }
        }
        val narrow = BatteryThresholds(98, 99, 100)
        assertEquals(BatteryBand.ORANGE, BatteryBand.fromLevel(98, 100, narrow))
        assertEquals(BatteryBand.YELLOW, BatteryBand.fromLevel(99, 100, narrow))
        assertEquals(BatteryBand.GREEN, BatteryBand.fromLevel(100, 100, narrow))
    }

    /** Vérifie les textes initiaux et la restauration d’un modèle neuf après édition. */

    @Test fun defaultStylesAndResetKeepOriginalTexts() {
        val original = WallpaperConfig()
        assertEquals("WARNING", original.style(BatteryBand.ORANGE).text)
        assertEquals("EMERGENCY", original.style(BatteryBand.RED).text)
        assertEquals("", original.style(BatteryBand.GREEN).text)
        assertEquals("", original.style(BatteryBand.YELLOW).text)
        val edited = original.copy(styles = original.styles.map { it.copy(text = "Autre") })
        assertEquals("Autre", edited.style(BatteryBand.GREEN).text)
        assertEquals(original, WallpaperConfig())
    }
}
