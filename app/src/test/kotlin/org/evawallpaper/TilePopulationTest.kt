package org.evawallpaper

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class TilePopulationTest {
    @Test fun densityTracksBatteryWithAnAbsoluteMinimum() {
        assertEquals(50, TilePopulation.countForBattery(200, 100, 100))
        assertEquals(25, TilePopulation.countForBattery(200, 50, 100))
        assertEquals(10, TilePopulation.countForBattery(200, 0, 100))
        assertEquals(10, TilePopulation.countForBattery(60, 50, 100))
        assertEquals(25, TilePopulation.countForBattery(200, 1, 2))
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
        scene.setTarget(TilePopulation.countForBattery(200, 50, 100), 0L)
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

    @Test fun visibleGridDoesNotCountOffscreenPositions() {
        val layout = MosaicLayout(192f)
        val cells = layout.visibleCells(720, 1440)
        assertTrue(cells.size >= 10)
        assertTrue(cells.all { it.centerX >= 0 && it.centerX < 720 && it.centerY >= 0 && it.centerY < 1440 })
        assertTrue(layout.visibleCells(0, 0).isEmpty())
    }
    @Test fun customMinimumAndDivisorDriveDensity() {
        val density = TileDensity(minimumTiles = 5, tileDivisor = 8)
        assertEquals(25, TilePopulation.countForBattery(200, 100, 100, density))
        assertEquals(13, TilePopulation.countForBattery(200, 50, 100, density))
        assertEquals(5, TilePopulation.countForBattery(200, 0, 100, density))
        assertEquals(3, TilePopulation.countForBattery(3, 0, 100, density))
        assertEquals(0, TilePopulation.countForBattery(200, 0, 100, TileDensity(0, 4)))
        assertEquals(200, TilePopulation.countForBattery(200, 100, 100, TileDensity(0, 1)))
        assertEquals(60, TilePopulation.countForBattery(60, 50, 100, TileDensity(100, 20)))
        assertEquals(TileDensity(10, 4), WallpaperConfig().density)
    }

    @Test fun densitySettingsRejectInvalidValues() {
        for ((minimum, divisor) in listOf(-1 to 4, 101 to 4, 10 to 0, 10 to 21)) {
            assertThrows(IllegalArgumentException::class.java) { TileDensity(minimum, divisor) }
        }
    }

    @Test fun disabledMinimumCanTurnOffAllTilesAndResumeLater() {
        val scene = TilePopulation(80, 10, 0L, Random(8))
        scene.setTarget(TilePopulation.countForBattery(80, 0, 100, TileDensity(0, 4)), 0L)
        for (now in 0L..30_000L step 40L) scene.advance(now)
        assertTrue(scene.lights(30_000L).isEmpty())
        assertNull(scene.nextDelay(30_000L))
        scene.setTarget(TilePopulation.countForBattery(80, 100, 100, TileDensity(0, 8)), 30_000L)
        for (now in 30_000L..60_000L step 40L) scene.advance(now)
        scene.pause()
        assertEquals(10, scene.lights(60_000L).size)
    }
}
