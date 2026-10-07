package org.evawallpaper

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

/** Conserve exactement les PNG d'origine tant que les réglages restent par défaut. */
object TileRenderer {
    /**
     * Crée un bitmap de tuile pour MainActivity ou EvaWallpaperService. Le propriétaire doit le recycler
     * après usage. Réutilise le dessin PNG original aux réglages par défaut, sinon recolore le motif sans
     * texte et ajoute le texte demandé.
     */
    fun render(resources: Resources, band: BatteryBand, style: TileStyle): Bitmap {
        if (style == WallpaperConfig.DEFAULT_STYLES[band.ordinal]) {
            val id = when (band) {
                BatteryBand.GREEN -> R.drawable.tile_green
                BatteryBand.YELLOW -> R.drawable.tile_yellow
                BatteryBand.ORANGE -> R.drawable.tile_orange
                BatteryBand.RED -> R.drawable.tile_red
            }
            return BitmapFactory.decodeResource(resources, id)
        }
        // Le vert n'a pas de texte : sa composante verte forme un masque de couleur.
        // PNG vert sans texte servant de masque ; libéré dès que sa copie modifiable est créée.
        val source = BitmapFactory.decodeResource(resources, R.drawable.tile_green)
        // Image renvoyée au demandeur, qui devient responsable de son recyclage.
        val bitmap = source.copy(Bitmap.Config.ARGB_8888, true)
        source.recycle()
        // Tampon ARGB pour recolorer en une seule lecture/écriture du bitmap.
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (index in pixels.indices) {
            val pixel = pixels[index]
            // Intensité du vert d’origine (#008000) : préserve le noir des détails et les bords anticrénelés.
            val weight = Color.green(pixel).coerceAtMost(128) / 128f
            pixels[index] = Color.argb(Color.alpha(pixel),
                (Color.red(style.color) * weight).toInt(),
                (Color.green(style.color) * weight).toInt(),
                (Color.blue(style.color) * weight).toInt())
        }
        bitmap.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        if (style.text.isNotEmpty()) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                textSize = bitmap.width * 0.1024f
            }
            // Largeur du texte avant réduction ; la limite de 78 % garde le texte dans l’hexagone.
            val measured = paint.measureText(style.text)
            if (measured > bitmap.width * 0.78f) paint.textSize *= bitmap.width * 0.78f / measured
            Canvas(bitmap).drawText(style.text, bitmap.width / 2f, bitmap.height * 0.542f, paint)
        }
        return bitmap
    }
}
