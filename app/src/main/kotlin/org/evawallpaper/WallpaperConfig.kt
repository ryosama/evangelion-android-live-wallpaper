package org.evawallpaper

/** Chaque état possède au moins un point de pourcentage, bornes non croisables. */
data class BatteryThresholds(val bad: Int = 15, val ok: Int = 30, val perfect: Int = 50) {
    init { require(bad in 1..98 && ok in bad + 1..99 && perfect in ok + 1..100) }
}

data class ChargingAnimation(
    val fillSeconds: Int = 4,
    val holdSeconds: Int = 5,
    val color: Int = 0xFF0088FF.toInt(),
    val pulseMillis: Int = 1000,
    val minimumOpacity: Int = 40,
) {
    init {
        require(fillSeconds in 1..30 && holdSeconds in 0..60)
        require(pulseMillis in 200..5000 && pulseMillis % 100 == 0)
        require(minimumOpacity in 0..100)
    }
}

enum class TileEffect {
    FLICKER, FADE;

    fun opacity(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        return when (this) {
            FLICKER -> TilePopulation.ignition(p)
            FADE -> p * p * (3f - 2f * p)
        }
    }

    companion object {
        fun fromStored(value: String): TileEffect = entries.firstOrNull { it.name == value } ?: FLICKER
    }
}

data class TileEffects(
    val appearance: TileEffect = TileEffect.FLICKER,
    val disappearance: TileEffect = TileEffect.FLICKER,
)

data class TileStyle(val color: Int, val text: String)

data class WallpaperConfig(
    val thresholds: BatteryThresholds = BatteryThresholds(),
    val styles: List<TileStyle> = DEFAULT_STYLES,
    val minimumTiles: Int = 7,
    val effects: TileEffects = TileEffects(),
    val charging: ChargingAnimation = ChargingAnimation(),
) {
    init {
        require(minimumTiles in 0..100)
        require(styles.size == 4 && styles.all { it.text.length <= 24 })
    }
    fun style(band: BatteryBand): TileStyle = styles[band.ordinal]

    companion object {
        // Ordre de BatteryBand : Parfait, OK, Mauvais, Critique.
        val DEFAULT_STYLES = listOf(
            TileStyle(0xFF008000.toInt(), ""), TileStyle(0xFFD4AA00.toInt(), ""),
            TileStyle(0xFFD45500.toInt(), "WARNING"), TileStyle(0xFFE20D00.toInt(), "EMERGENCY"),
        )
    }
}
