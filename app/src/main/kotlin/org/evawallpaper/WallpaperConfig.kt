package org.evawallpaper

/** Chaque état possède au moins un point de pourcentage, bornes non croisables. */
data class BatteryThresholds(val bad: Int = 15, val ok: Int = 30, val perfect: Int = 50) {
    init { require(bad in 1..98 && ok in bad + 1..99 && perfect in ok + 1..100) }
}

data class TileDensity(val minimumTiles: Int = 10, val tileDivisor: Int = 4) {
    init { require(minimumTiles in 0..100 && tileDivisor in 1..20) }
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
    val density: TileDensity = TileDensity(),
    val effects: TileEffects = TileEffects(),
) {
    init { require(styles.size == 4 && styles.all { it.text.length <= 24 }) }
    fun style(band: BatteryBand): TileStyle = styles[band.ordinal]

    companion object {
        // Ordre de BatteryBand : Parfait, OK, Mauvais, Critique.
        val DEFAULT_STYLES = listOf(
            TileStyle(0xFF008000.toInt(), ""), TileStyle(0xFFD4AA00.toInt(), ""),
            TileStyle(0xFFD45500.toInt(), "WARNING"), TileStyle(0xFFE20D00.toInt(), "EMERGENCY"),
        )
    }
}
