package org.evawallpaper

import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class TileEffectsTest {
    @Test fun fadeIsSmoothMonotonicAndHasExactEndpoints() {
        assertEquals(0f, TileEffect.FADE.opacity(0f), 0f)
        assertEquals(1f, TileEffect.FADE.opacity(1f), 0f)
        assertEquals(0.5f, TileEffect.FADE.opacity(0.5f), 0.0001f)
        var previous = 0f
        for (step in 0..1000) {
            val alpha = TileEffect.FADE.opacity(step / 1000f)
            assertTrue(alpha >= previous)
            assertTrue(alpha in 0f..1f)
            previous = alpha
        }
    }

    @Test fun defaultsAndUnknownStoredEffectsPreserveFlicker() {
        assertEquals(TileEffects(), WallpaperConfig().effects)
        assertEquals(TileEffect.FLICKER, TileEffect.fromStored(""))
        assertEquals(TileEffect.FLICKER, TileEffect.fromStored("unknown"))
        assertEquals(TileEffect.FADE, TileEffect.fromStored("FADE"))
        for (step in 0..100) {
            val progress = step / 100f
            assertEquals(TilePopulation.ignition(progress), TileEffect.FLICKER.opacity(progress), 0f)
        }
    }

    @Test fun allFourCombinationsPreserveVisibleCountAndDistinctPositions() {
        for (appearance in TileEffect.entries) for (disappearance in TileEffect.entries) {
            val scene = TilePopulation(80, 10, 0L, Random(9), TileEffects(appearance, disappearance))
            var animated = false
            for (now in 0L..20_000L step 10L) {
                scene.advance(now)
                val lights = scene.lights(now)
                assertTrue(lights.all { it.alpha in 0f..1f })
                // Même après conversion en alpha 8 bits du Canvas, le minimum reste visible.
                assertTrue(lights.count { (it.alpha * 255).toInt() > 0 } >= 10)
                assertTrue(lights.size <= 11)
                assertEquals(lights.size, lights.map { it.cell }.distinct().size)
                animated = animated || lights.any { it.alpha < 1f }
            }
            assertTrue(animated)
        }
    }

    @Test fun effectChangeDoesNotAlterTransitionAlreadyInProgress() {
        val reference = TilePopulation(80, 10, 0L, Random(13))
        val changed = TilePopulation(80, 10, 0L, Random(13))
        var changedAt = -1L
        for (now in 0L..6000L step 10L) {
            reference.advance(now)
            changed.advance(now)
            if (reference.lights(now).any { it.alpha < 1f }) {
                changedAt = now
                break
            }
        }
        assertTrue(changedAt >= 0)
        changed.setEffects(TileEffects(TileEffect.FADE, TileEffect.FADE))
        for (now in changedAt..changedAt + 1350L step 10L) {
            reference.advance(now)
            changed.advance(now)
            assertEquals(reference.lights(now), changed.lights(now))
        }
    }

    @Test fun choosingOneDirectionDoesNotChangeTheOther() {
        val original = TileEffects()
        val entranceOnly = original.copy(appearance = TileEffect.FADE)
        assertEquals(TileEffect.FLICKER, entranceOnly.disappearance)
        val exitOnly = original.copy(disappearance = TileEffect.FADE)
        assertEquals(TileEffect.FLICKER, exitOnly.appearance)
    }
}
