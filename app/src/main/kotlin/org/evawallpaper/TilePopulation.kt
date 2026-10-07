package org.evawallpaper

import kotlin.math.roundToInt
import kotlin.random.Random

/** Animation indépendante d'Android, pilotée par une horloge monotone. */
class TilePopulation(
    // Nombre de cellules fourni par MosaicLayout ; tous les indices doivent rester dans cette capacité.
    private val capacity: Int,
    // Nombre initial demandé ; immédiatement borné à la capacité.
    target: Int,
    // Instant initial monotone en millisecondes, fourni par le service ou les tests.
    now: Long,
    // Source aléatoire injectable dans les tests ; utilisée pour positions, pauses et durées.
    private val random: Random = Random.Default,
    // Effets à copier lors du prochain relais ; les relais actifs conservent leurs propres effets.
    private var effects: TileEffects = TileEffects(),
) {
    /** cell est un indice dans la liste MosaicLayout du service ; alpha est une opacité normalisée (0..1). */
    data class Light(val cell: Int, val alpha: Float)
    /**
     * Relais immuable : outgoing/incoming sont absents lors d’un ajout/retrait seul.
     * start et duration sont en millisecondes monotones ; effects fige les choix au début du relais.
     */
    private data class Transition(val outgoing: Int?, val incoming: Int?, val start: Long, val duration: Long, val effects: TileEffects)
    // Indices des cellules stables, sans doublons. Les deux extrémités d’un relais sont traitées séparément par lights.
    private val occupied = (0 until capacity).shuffled(random).take(target.coerceIn(0, capacity)).toMutableSet()
    // Nombre souhaité, borné par la capacité ; peut différer temporairement de occupied pendant un ajustement.
    private var target = target.coerceIn(0, capacity)
    // Relais courant, ou null entre deux animations.
    private var transition: Transition? = null
    // Prochaine échéance sur l’horloge monotone, en millisecondes.
    private var nextChange = now + idleDelay()

    companion object {
        // Intervalle de rendu animé : 40 ms, soit 25 images/s ; également utilisé pendant la charge.
        const val FRAME_MS = 40L

        /**
         * Calcule le nombre cible : capacité × level/scale arrondi, avec un minimum et sans dépasser les
         * emplacements disponibles. Appelée à chaque mesure par le service.
         */
        fun countForBattery(capacity: Int, level: Int, scale: Int, minimumTiles: Int = 7): Int {
            require(capacity >= 0 && scale > 0 && level in 0..scale)
            return (capacity.toDouble() * level / scale).roundToInt()
                .coerceAtLeast(minimumTiles).coerceAtMost(capacity)
        }

        /**
         * Profil fluorescent d’apparition : chaque seuil est une fraction du temps total, chaque valeur une
         * opacité. TileEffect l’utilise directement ou par complément pour l’extinction.
         */
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

    /**
     * Modifie les effets des prochains relais uniquement ; la transition active garde sa copie pour éviter
     * un saut visuel.
     */
    fun setEffects(value: TileEffects) { effects = value }

    /**
     * Change le nombre souhaité et déclenche son ajustement dès que possible. now est fourni par l’horloge
     * monotone du service, en millisecondes.
     */
    fun setTarget(value: Int, now: Long) {
        val next = value.coerceIn(0, capacity)
        if (target == next) return
        target = next
        if (transition == null) nextChange = now
    }

    /**
     * Pendant la charge, termine le relais puis ajuste immédiatement le nombre. Les cellules conservées
     * restent à leur place pour stabiliser le balayage.
     */
    fun settleTarget() {
        completeTransition()
        while (occupied.size > target) occupied.remove(occupied.random(random))
        val empty = (0 until capacity).filterNot { it in occupied }.shuffled(random)
        occupied.addAll(empty.take(target - occupied.size))
    }

    /**
     * Termine le relais à son état final lorsque le fond est caché ou la charge commence. Le service se
     * charge séparément d’arrêter les callbacks.
     */
    fun pause() { completeTransition() }
    /**
     * Replanifie une échéance à partir de now, sans rattraper les animations cachées ; 150 ms si le nombre
     * doit être corrigé, sinon une pause aléatoire.
     */
    fun resume(now: Long) { nextChange = now + if (occupied.size != target) 150L else idleDelay() }

    /**
     * Pause aléatoire de 1,8 à 4,2 secondes entre déplacements, en millisecondes. Random est injectable
     * pour rendre les tests reproductibles.
     */
    private fun idleDelay() = random.nextLong(1800L, 4201L)

    /**
     * Applique définitivement la sortie et l’entrée au jeu de cellules occupées, puis libère la transition
     * courante.
     */
    private fun completeTransition() {
        transition?.let {
            it.outgoing?.let(occupied::remove)
            it.incoming?.let(occupied::add)
        }
        transition = null
    }

    /**
     * Fait évoluer la scène à l’instant now : termine un relais expiré puis, à échéance, ajoute, retire ou
     * déplace une tuile selon la cible. Ne dessine rien.
     */
    fun advance(now: Long) {
        transition?.let {
            if (now - it.start < it.duration) return
            completeTransition()
            nextChange = now + if (occupied.size != target) 150L else idleDelay()
        }
        if (now < nextChange) return
        val empty = (0 until capacity).filterNot { it in occupied }
        // Cellule sortante si la scène doit se réduire ou déplacer une tuile ; null lors d’un ajout seul.
        val outgoing = if (occupied.size >= target && occupied.isNotEmpty()) occupied.random(random) else null
        // Cellule entrante libre si la scène doit grandir ou déplacer une tuile ; null lors d’un retrait seul.
        val incoming = if (occupied.size <= target && empty.isNotEmpty()) empty.random(random) else null
        // Une grille entièrement allumée ne dispose d'aucune destination libre.
        if (occupied.size == target && (outgoing == null || incoming == null)) return
        if (outgoing == null && incoming == null) return
        transition = Transition(outgoing, incoming, now, random.nextLong(850L, 1351L), effects)
    }

    /**
     * Produit les cellules à dessiner et leur opacité à now, sans modifier l’état. Le service transforme
     * ces indices en positions via MosaicLayout.
     */
    fun lights(now: Long): List<Light> {
        val active = transition
        val lights = occupied.filter { it != active?.outgoing }.map { Light(it, 1f) }.toMutableList()
        if (active != null) {
            // Avancement normalisé du relais ; borné pour supporter les retards de rendu.
            val progress = ((now - active.start).toFloat() / active.duration).coerceIn(0f, 1f)
            val on = active.effects.appearance.opacity(progress)
            // Opacité de disparition : complément du profil d’apparition choisi pour ce sens.
            val off = 1f - active.effects.disappearance.opacity(progress)
            active.outgoing?.let { if (off > 0f) lights.add(Light(it, off)) }
            active.incoming?.let { if (on > 0f) lights.add(Light(it, on)) }
        }
        return lights
    }

    /**
     * Délai en millisecondes avant le prochain rendu utile : cadence de transition, échéance de relais, ou
     * null pour une grille stable entièrement vide/pleine.
     */
    fun nextDelay(now: Long): Long? = when {
        transition != null -> FRAME_MS
        occupied.size == target && (target == 0 || target == capacity) -> null
        else -> (nextChange - now).coerceAtLeast(1L)
    }
}
