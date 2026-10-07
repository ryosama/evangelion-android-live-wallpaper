package org.evawallpaper

/**
 * Seuils inclusifs en pourcentage consommés par BatteryBand et édités par BatteryRangeSlider.
 * @property bad Entrée dans Mauvais ; les valeurs inférieures sont Critique.
 * @property ok Entrée dans OK.
 * @property perfect Entrée dans Parfait. Chaque état garde au moins un point de pourcentage.
 */
data class BatteryThresholds(val bad: Int = 15, val ok: Int = 30, val perfect: Int = 50) {
    init { require(bad in 1..98 && ok in bad + 1..99 && perfect in ok + 1..100) }
}

/** Paramètres partagés entre les curseurs de MainActivity, ConfigStore et ChargingPulse. */
data class ChargingAnimation(
    /** Durée du balayage complet en secondes (1..30). */
    val fillSeconds: Int = 4,
    /** Maintien de la couleur de charge en secondes (0..60). */
    val holdSeconds: Int = 5,
    /** Couleur ARGB de charge ; ConfigStore et le sélecteur garantissent un alpha opaque. */
    val color: Int = 0xFF0088FF.toInt(),
    /** Durée d’un aller-retour complet en millisecondes (200..5000, pas de 100). */
    val pulseMillis: Int = 1000,
    /** Opacité minimale du pulse en pourcentage (0..100). */
    val minimumOpacity: Int = 40,
) {
    init {
        require(fillSeconds in 1..30 && holdSeconds in 0..60)
        require(pulseMillis in 200..5000 && pulseMillis % 100 == 0)
        require(minimumOpacity in 0..100)
    }
}

/** Profils d’opacité utilisés par TilePopulation ; les noms servent aussi à la persistance JSON. */
enum class TileEffect {
    // FLICKER reproduit le starter fluorescent ; FADE applique un fondu continu.
    FLICKER, FADE;

    /**
     * Transforme une progression bornée à 0..1 en opacité d’apparition. TilePopulation utilise son
     * complément pour la disparition ; FADE utilise la courbe douce smoothstep.
     */
    fun opacity(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        return when (this) {
            FLICKER -> TilePopulation.ignition(p)
            FADE -> p * p * (3f - 2f * p)
        }
    }

    companion object {
        /**
         * Relit le nom enregistré dans ConfigStore ; une valeur inconnue retombe sur FLICKER pour rester
         * compatible avec les anciens réglages.
         */
        fun fromStored(value: String): TileEffect = entries.firstOrNull { it.name == value } ?: FLICKER
    }
}

/** Choix indépendants pour les deux sens d’un relais. */
data class TileEffects(
    /** Profil utilisé directement pour allumer la cellule entrante. */
    val appearance: TileEffect = TileEffect.FLICKER,
    /** Profil dont le complément éteint la cellule sortante. */
    val disappearance: TileEffect = TileEffect.FLICKER,
)

/** Couleur ARGB et texte optionnel (vide = aucun) consommés par TileRenderer. */
data class TileStyle(val color: Int, val text: String)

/** Instantané immuable : édité par copies dans MainActivity, sérialisé par ConfigStore, lu par le service. */
data class WallpaperConfig(
    /** Seuils de BatteryBand ; validés à la construction du modèle. */
    val thresholds: BatteryThresholds = BatteryThresholds(),
    /** Quatre styles ordonnés selon BatteryBand, jamais selon l’ordre visuel du curseur. */
    val styles: List<TileStyle> = DEFAULT_STYLES,
    /** Plancher de population (0..100) ; TilePopulation le borne à la capacité réelle. */
    val minimumTiles: Int = 7,
    /** Choix des profils hors charge ; copié dans chaque nouveau relais. */
    val effects: TileEffects = TileEffects(),
    /** Paramètres du balayage et de la pulsation lorsque la charge est détectée. */
    val charging: ChargingAnimation = ChargingAnimation(),
) {
    init {
        require(minimumTiles in 0..100)
        require(styles.size == 4 && styles.all { it.text.length <= 24 })
    }
    /**
     * Associe l’état de batterie à son style par ordinal. Conserver le même ordre dans BatteryBand,
     * DEFAULT_STYLES et les aperçus de MainActivity.
     */
    fun style(band: BatteryBand): TileStyle = styles[band.ordinal]

    companion object {
        // Ordre de BatteryBand : Parfait, OK, Mauvais, Critique.
        /**
         * Styles initiaux correspondant aux PNG originaux ; utilisés aussi par Restaurer les valeurs par
         * défaut.
         */
        val DEFAULT_STYLES = listOf(
            TileStyle(0xFF008000.toInt(), ""), TileStyle(0xFFD4AA00.toInt(), ""),
            TileStyle(0xFFD45500.toInt(), "WARNING"), TileStyle(0xFFE20D00.toInt(), "EMERGENCY"),
        )
    }
}
