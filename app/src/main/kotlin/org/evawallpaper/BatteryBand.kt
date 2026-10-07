package org.evawallpaper

/** Seuils inclusifs, sans arrondir le pourcentage avant la comparaison. */
enum class BatteryBand {
    GREEN, YELLOW, ORANGE, RED;

    companion object {
        /**
         * Convertit la mesure Android level/scale en état selon BatteryThresholds. Renvoie null pour une
         * mesure invalide ; le service conserve alors son dernier rendu.
         */
        fun fromLevel(level: Int, scale: Int, thresholds: BatteryThresholds = BatteryThresholds()): BatteryBand? {
            if (scale <= 0 || level < 0 || level > scale) return null
            // Produit entier en Long pour comparer les fractions sans arrondi et sans débordement d’un Int.
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
