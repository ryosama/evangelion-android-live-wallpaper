package org.evawallpaper

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

class ConfigStore(context: Context) {
    val preferences = context.getSharedPreferences("wallpaper", Context.MODE_PRIVATE)

    fun read(): WallpaperConfig = runCatching {
        val json = preferences.getString("config", null) ?: return WallpaperConfig()
        decode(json)
    }.getOrDefault(WallpaperConfig())

    fun write(config: WallpaperConfig) {
        // Un seul changement atomique pour que le fond ne voie pas de réglages partiels.
        preferences.edit { putString("config", encode(config)) }
    }

    companion object {
        fun encode(config: WallpaperConfig): String = JSONObject().apply {
            put("bad", config.thresholds.bad)
            put("ok", config.thresholds.ok)
            put("perfect", config.thresholds.perfect)
            put("minimumTiles", config.density.minimumTiles)
            put("tileDivisor", config.density.tileDivisor)
            put("appearanceEffect", config.effects.appearance.name)
            put("disappearanceEffect", config.effects.disappearance.name)
            put("styles", JSONArray().apply {
                config.styles.forEach { style ->
                    put(JSONObject().put("color", style.color).put("text", style.text))
                }
            })
        }.toString()

        fun decode(json: String): WallpaperConfig {
            val root = JSONObject(json)
            val styles = root.getJSONArray("styles")
            require(styles.length() == 4)
            return WallpaperConfig(
                BatteryThresholds(root.getInt("bad"), root.getInt("ok"), root.getInt("perfect")),
                List(4) { index ->
                    val item = styles.getJSONObject(index)
                    TileStyle(item.getInt("color") or 0xFF000000.toInt(), item.getString("text"))
                },
                // Les anciens réglages gardent leurs couleurs/textes et reçoivent 10 / 4.
                TileDensity(
                    root.optInt("minimumTiles", 10).coerceIn(0, 100),
                    root.optInt("tileDivisor", 4).coerceIn(1, 20),
                ),
                TileEffects(
                    TileEffect.fromStored(root.optString("appearanceEffect", "FLICKER")),
                    TileEffect.fromStored(root.optString("disappearanceEffect", "FLICKER")),
                ),
            )
        }
    }
}
