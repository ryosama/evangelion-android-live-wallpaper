package org.evawallpaper

import kotlin.math.roundToInt
import kotlin.random.Random

/** Animation indépendante d'Android, pilotée par une horloge monotone. */
class TilePopulation(
    private val capacity: Int,
    target: Int,
    now: Long,
    private val random: Random = Random.Default,
    private var effects: TileEffects = TileEffects(),
) {
    data class Light(val cell: Int, val alpha: Float)
    private data class Transition(val outgoing: Int?, val incoming: Int?, val start: Long, val duration: Long, val effects: TileEffects)
    private val occupied = (0 until capacity).shuffled(random).take(target.coerceIn(0, capacity)).toMutableSet()
    private var target = target.coerceIn(0, capacity)
    private var transition: Transition? = null
    private var nextChange = now + idleDelay()

    companion object {
        const val FRAME_MS = 40L

        fun countForBattery(capacity: Int, level: Int, scale: Int, density: TileDensity = TileDensity()): Int {
            require(capacity >= 0 && scale > 0 && level in 0..scale)
            return (capacity.toDouble() * level / scale / density.tileDivisor).roundToInt()
                .coerceAtLeast(density.minimumTiles).coerceAtMost(capacity)
        }

        // Impulsions brusques, coupures et reprises : pas de fondu régulier.
        fun ignition(progress: Float): Float = when {
            progress < 0.08f -> 0f
            progress < 0.15f -> 0.18f
            progress < 0.25f -> 0f
            progress < 0.33f -> 0.75f
            progress < 0.43f -> 0.05f
            progress < 0.51f -> 0.35f
            progress < 0.60f -> 0f
            progress < 0.71f -> 0.90f
            progress < 0.78f -> 0.15f
            progress < 0.89f -> 1f
            progress < 0.95f -> 0.45f
            else -> 1f
        }
    }

    // Le choix modifié s'applique au prochain relais, sans saut dans celui en cours.
    fun setEffects(value: TileEffects) { effects = value }

    fun setTarget(value: Int, now: Long) {
        val next = value.coerceIn(0, capacity)
        if (target == next) return
        target = next
        if (transition == null) nextChange = now
    }

    /** Suspend une transition à son état final, sans rattrapage d'animations au réveil. */
    fun pause() { completeTransition() }
    fun resume(now: Long) { nextChange = now + if (occupied.size != target) 150L else idleDelay() }

    private fun idleDelay() = random.nextLong(1800L, 4201L)

    private fun completeTransition() {
        transition?.let {
            it.outgoing?.let(occupied::remove)
            it.incoming?.let(occupied::add)
        }
        transition = null
    }

    fun advance(now: Long) {
        transition?.let {
            if (now - it.start < it.duration) return
            completeTransition()
            nextChange = now + if (occupied.size != target) 150L else idleDelay()
        }
        if (now < nextChange) return
        val empty = (0 until capacity).filterNot { it in occupied }
        val outgoing = if (occupied.size >= target && occupied.isNotEmpty()) occupied.random(random) else null
        val incoming = if (occupied.size <= target && empty.isNotEmpty()) empty.random(random) else null
        // Une grille entièrement allumée ne dispose d'aucune destination libre.
        if (occupied.size == target && (outgoing == null || incoming == null)) return
        if (outgoing == null && incoming == null) return
        transition = Transition(outgoing, incoming, now, random.nextLong(850L, 1351L), effects)
    }

    fun lights(now: Long): List<Light> {
        val active = transition
        val lights = occupied.filter { it != active?.outgoing }.map { Light(it, 1f) }.toMutableList()
        if (active != null) {
            val progress = ((now - active.start).toFloat() / active.duration).coerceIn(0f, 1f)
            val on = active.effects.appearance.opacity(progress)
            val off = 1f - active.effects.disappearance.opacity(progress)
            active.outgoing?.let { if (off > 0f) lights.add(Light(it, off)) }
            active.incoming?.let { if (on > 0f) lights.add(Light(it, on)) }
        }
        return lights
    }

    fun nextDelay(now: Long): Long? = when {
        transition != null -> FRAME_MS
        occupied.size == target && (target == 0 || target == capacity) -> null
        else -> (nextChange - now).coerceAtLeast(1L)
    }
}
