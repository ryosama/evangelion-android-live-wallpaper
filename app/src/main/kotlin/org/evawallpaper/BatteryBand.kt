package org.evawallpaper

/** Seuils inclusifs, sans arrondir le pourcentage avant la comparaison. */
enum class BatteryBand {
    GREEN, YELLOW, ORANGE, RED;

    companion object {
        fun fromLevel(level: Int, scale: Int, thresholds: BatteryThresholds = BatteryThresholds()): BatteryBand? {
            if (scale <= 0 || level < 0 || level > scale) return null
            val scaledLevel = level.toLong() * 100
            return when {
                scaledLevel >= scale.toLong() * thresholds.perfect -> GREEN
                scaledLevel >= scale.toLong() * thresholds.ok -> YELLOW
                scaledLevel >= scale.toLong() * thresholds.bad -> ORANGE
                else -> RED
            }
        }
    }
}
