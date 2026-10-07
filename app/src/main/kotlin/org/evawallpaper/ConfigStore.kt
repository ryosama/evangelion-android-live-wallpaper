package org.evawallpaper

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/** Frontière JSON entre WallpaperConfig, le brouillon de MainActivity et le moteur du fond. */
class ConfigStore(context: Context) {
    // Stockage privé partagé par activité et service ; exposé pour que le moteur observe les changements.
    val preferences = context.getSharedPreferences("wallpaper", Context.MODE_PRIVATE)

    /**
     * Charge le JSON persistant. Une absence ou une configuration illisible/invalide rétablit
     * WallpaperConfig par défaut, sans faire échouer l’écran ou le service.
     */
    fun read(): WallpaperConfig = runCatching {
        val json = preferences.getString("config", null) ?: return WallpaperConfig()
        decode(json)
    }.getOrDefault(WallpaperConfig())

    /**
     * Enregistre la configuration en une seule valeur JSON. MainActivity l’appelle à la validation ; le
     * listener du service reçoit ensuite le changement.
     */
    fun write(config: WallpaperConfig) {
        // Un seul changement atomique pour que le fond ne voie pas de réglages partiels.
        preferences.edit { putString("config", encode(config)) }
    }

    companion object {
        /**
         * Sérialise les réglages pour SharedPreferences et pour le brouillon sauvegardé par MainActivity
         * lors d’une recréation Android.
         */
        fun encode(config: WallpaperConfig): String = JSONObject().apply {
            put("bad", config.thresholds.bad)
            put("ok", config.thresholds.ok)
            put("perfect", config.thresholds.perfect)
            put("minimumTiles", config.minimumTiles)
            put("chargeFillSeconds", config.charging.fillSeconds)
            put("chargeHoldSeconds", config.charging.holdSeconds)
            put("chargeColor", config.charging.color)
            put("chargePulseMillis", config.charging.pulseMillis)
            put("chargeMinimumOpacity", config.charging.minimumOpacity)
            put("appearanceEffect", config.effects.appearance.name)
            put("disappearanceEffect", config.effects.disappearance.name)
            put("styles", JSONArray().apply {
                config.styles.forEach { style ->
                    put(JSONObject().put("color", style.color).put("text", style.text))
                }
            })
        }.toString()

        /**
         * Reconstruit et valide les modèles. Les nouveaux champs absents reçoivent leurs défauts ; les
         * limites des curseurs sont appliquées aux valeurs persistées.
         */
        fun decode(json: String): WallpaperConfig {
            val root = JSONObject(json)
            // Table ordonnée comme BatteryBand ; exactement quatre styles sont nécessaires.
            val styles = root.getJSONArray("styles")
            require(styles.length() == 4)
            return WallpaperConfig(
                BatteryThresholds(root.getInt("bad"), root.getInt("ok"), root.getInt("perfect")),
                List(4) { index ->
                    val item = styles.getJSONObject(index)
                    TileStyle(item.getInt("color") or 0xFF000000.toInt(), item.getString("text"))
                },
                root.optInt("minimumTiles", 7).coerceIn(0, 100),
                TileEffects(
                    TileEffect.fromStored(root.optString("appearanceEffect", "FLICKER")),
                    TileEffect.fromStored(root.optString("disappearanceEffect", "FLICKER")),
                ),
                ChargingAnimation(
                    root.optInt("chargeFillSeconds", 4).coerceIn(1, 30),
                    root.optInt("chargeHoldSeconds", 5).coerceIn(0, 60),
                    root.optInt("chargeColor", 0xFF0088FF.toInt()) or 0xFF000000.toInt(),
                    (root.optInt("chargePulseMillis", 1000).coerceIn(200, 5000) / 100) * 100,
                    root.optInt("chargeMinimumOpacity", 40).coerceIn(0, 100),
                ),
            )
        }
    }
}
