package org.evawallpaper

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class TilePopulationTest {
    @Test fun populationTracksBatteryWithSevenAsDefaultMinimum() {
        assertEquals(200, TilePopulation.countForBattery(200, 100, 100))
        assertEquals(100, TilePopulation.countForBattery(200, 50, 100))
        assertEquals(7, TilePopulation.countForBattery(200, 0, 100))
        assertEquals(30, TilePopulation.countForBattery(60, 50, 100))
        assertEquals(100, TilePopulation.countForBattery(200, 1, 2))
        assertEquals(6, TilePopulation.countForBattery(6, 0, 100))
        assertEquals(0, TilePopulation.countForBattery(0, 100, 100))
    }

    @Test fun relaysFlickerAndMoveWithoutFallingBelowTen() {
        val scene = TilePopulation(80, 10, 0L, Random(42))
        val initial = scene.lights(0L).map { it.cell }.toSet()
        var moved = false
        var flickered = false
        for (now in 0L..30_000L step 20L) {
            scene.advance(now)
            val lights = scene.lights(now)
            assertTrue(lights.size >= 10)
            assertTrue(lights.size <= 11)
            assertEquals(lights.size, lights.map { it.cell }.distinct().size)
            assertTrue(lights.all { it.cell in 0..79 && it.alpha > 0f && it.alpha <= 1f })
            assertEquals(10f, lights.sumOf { it.alpha.toDouble() }.toFloat(), 0.0001f)
            if (lights.any { it.alpha < 1f }) flickered = true
            if (lights.map { it.cell }.toSet() != initial) moved = true
        }
        assertTrue(moved)
        assertTrue(flickered)
    }

    @Test fun changedBatteryAdjustsPopulationEvenWithinTheSameColorBand() {
        val scene = TilePopulation(200, 50, 0L, Random(3))
        scene.setTarget(TilePopulation.countForBattery(200, 125, 1000), 0L)
        for (now in 0L..60_000L step 40L) {
            scene.advance(now)
            assertTrue(scene.lights(now).size >= 25)
        }
        scene.pause()
        assertEquals(25, scene.lights(60_000L).size)
        scene.setTarget(10, 60_000L)
        for (now in 60_000L..100_000L step 40L) {
            scene.advance(now)
            assertTrue(scene.lights(now).size >= 10)
        }
        scene.pause()
        assertEquals(10, scene.lights(100_000L).size)
        scene.setTarget(30, 100_000L)
        for (now in 100_000L..150_000L step 40L) scene.advance(now)
        scene.pause()
        assertEquals(30, scene.lights(150_000L).size)
    }

    @Test fun fullSmallGridsDoNotBlinkOrScheduleBusyLoops() {
        val scene = TilePopulation(6, 6, 0L, Random(1))
        scene.advance(10_000L)
        assertEquals(6, scene.lights(10_000L).size)
        assertNull(scene.nextDelay(10_000L))
    }

    @Test fun resumeKeepsPositionsAndDoesNotCatchUpWhileHidden() {
        val scene = TilePopulation(80, 10, 0L, Random(5))
        for (now in 0L..5000L step 40L) scene.advance(now)
        scene.pause()
        val settled = scene.lights(5000L)
        assertTrue(settled.all { it.alpha == 1f })
        scene.resume(100_000L)
        scene.advance(100_000L)
        assertEquals(settled, scene.lights(100_000L))
        assertTrue(scene.nextDelay(100_000L)!! >= 1800L)
    }

    @Test fun visibleGridIncludesClippedTilesButNotFullyOffscreenTiles() {
        val layout = MosaicLayout(168f)
        val cells = layout.visibleCells(720, 1600)
        // Régression : l’ancienne grille s’arrêtait avant cette colonne à droite.
        assertTrue(cells.any { it.centerX >= 720 && it.centerX - layout.tileWidth / 2 < 720 })
        assertTrue(cells.any { it.centerY >= 1600 && it.centerY - layout.tileHeight / 2 < 1600 })
        assertTrue(cells.all {
            it.centerX + layout.tileWidth / 2 > 0 && it.centerX - layout.tileWidth / 2 < 720 &&
                it.centerY + layout.tileHeight / 2 > 0 && it.centerY - layout.tileHeight / 2 < 1600
        })
        assertEquals(cells.size, cells.distinct().size)
        assertTrue(layout.visibleCells(0, 1600).isEmpty())
        assertTrue(layout.visibleCells(720, 0).isEmpty())
    }

    @Test fun gridExtendsToRightAndBottomInPortraitAndLandscape() {
        for (tileWidth in listOf(96f, 168f, 192f, 288f, 384f)) {
            val layout = MosaicLayout(tileWidth)
            for ((width, height) in listOf(720 to 1600, 1600 to 720, 1080 to 2400, 1 to 1)) {
                val cells = layout.visibleCells(width, height)
                assertTrue(cells.maxOf { it.centerX } + layout.tileWidth / 2 >= width)
                for (column in cells.groupBy { it.centerX }.values) {
                    assertTrue(column.minOf { it.centerY } - layout.tileHeight / 2 <= MosaicLayout.GAP_PX)
                    assertTrue(column.maxOf { it.centerY } + layout.tileHeight / 2 >= height - MosaicLayout.GAP_PX)
                }
                val count = TilePopulation.countForBattery(cells.size, 100, 100)
                assertEquals(cells.size, count)
            }
        }
    }

    @Test fun customMinimumIsCappedByAvailableCells() {
        assertEquals(5, TilePopulation.countForBattery(200, 0, 100, 5))
        assertEquals(3, TilePopulation.countForBattery(3, 0, 100, 5))
        assertEquals(60, TilePopulation.countForBattery(60, 50, 100, 100))
        assertEquals(7, WallpaperConfig().minimumTiles)
        for (minimum in listOf(-1, 101)) {
            assertThrows(IllegalArgumentException::class.java) { WallpaperConfig(minimumTiles = minimum) }
        }
    }

    @Test fun chargingSettlesCountWithoutMovingExistingTiles() {
        val scene = TilePopulation(80, 10, 0L, Random(8))
        val initial = scene.lights(0L).map { it.cell }.toSet()
        scene.setTarget(40, 0L)
        scene.settleTarget()
        assertEquals(40, scene.lights(0L).size)
        assertTrue(scene.lights(0L).map { it.cell }.containsAll(initial))
        assertTrue(scene.lights(0L).all { it.alpha == 1f })
        scene.setTarget(0, 0L)
        scene.settleTarget()
        assertTrue(scene.lights(0L).isEmpty())
    }

    @Test fun disabledMinimumCanTurnOffAllTilesAndResumeLater() {
        val scene = TilePopulation(80, 10, 0L, Random(8))
        scene.setTarget(TilePopulation.countForBattery(80, 0, 100, 0), 0L)
        for (now in 0L..30_000L step 40L) scene.advance(now)
        assertTrue(scene.lights(30_000L).isEmpty())
        assertNull(scene.nextDelay(30_000L))
        scene.setTarget(TilePopulation.countForBattery(80, 125, 1000, 0), 30_000L)
        for (now in 30_000L..60_000L step 40L) scene.advance(now)
        scene.pause()
        assertEquals(10, scene.lights(60_000L).size)
    }
}
