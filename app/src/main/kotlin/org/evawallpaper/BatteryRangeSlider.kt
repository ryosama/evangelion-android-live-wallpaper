package org.evawallpaper

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.google.android.material.slider.LabelFormatter
import com.google.android.material.slider.RangeSlider

/** Une seule piste à quatre couleurs, avec les trois poignées accessibles de Material. */
class BatteryRangeSlider(context: Context) : RangeSlider(context) {
    private val segmentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    var segmentColors: List<Int> = WallpaperConfig.DEFAULT_STYLES.reversed().map { it.color }
        set(value) { field = value; invalidate() }

    init {
        layoutDirection = LAYOUT_DIRECTION_LTR
        labelBehavior = LabelFormatter.LABEL_FLOATING
        trackTintList = ColorStateList.valueOf(Color.TRANSPARENT)
        thumbTintList = ColorStateList.valueOf(Color.WHITE)
        trackHeight = (8 * resources.displayMetrics.density).toInt()
        isTickVisible = false
    }

    override fun onDraw(canvas: Canvas) {
        val boundaries = listOf(valueFrom) + values + valueTo
        if (boundaries.size == 5 && segmentColors.size == 4) {
            // Avec LABEL_FLOATING, la piste Material est centrée dans la hauteur mesurée.
            val center = height / 2f
            val half = trackHeight / 2f
            for (index in 0..3) {
                val start = trackSidePadding + trackWidth * (boundaries[index] - valueFrom) / (valueTo - valueFrom)
                val end = trackSidePadding + trackWidth * (boundaries[index + 1] - valueFrom) / (valueTo - valueFrom)
                segmentPaint.color = segmentColors[index]
                canvas.drawRect(start, center - half, end, center + half, segmentPaint)
            }
        }
        super.onDraw(canvas)
    }
}
