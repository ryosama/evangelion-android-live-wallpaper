package org.evawallpaper

import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import kotlin.math.min
import kotlin.math.sin

/** Prototype Canvas : aucune ressource réseau, animation suspendue hors écran. */
class EvaWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = EvaEngine()

    private inner class EvaEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val frame = Runnable { drawFrame() }
        private var visible = false
        private var surfaceReady = false

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            scheduleFrame()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            surfaceReady = true
            scheduleFrame()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            surfaceReady = false
            handler.removeCallbacks(frame)
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            visible = false
            handler.removeCallbacks(frame)
            super.onDestroy()
        }

        private fun scheduleFrame() {
            handler.removeCallbacks(frame)
            if (visible && surfaceReady) handler.post(frame)
        }

        private fun drawFrame() {
            if (!visible || !surfaceReady) return
            val holder = surfaceHolder
            val canvas = holder.lockCanvas()
            if (canvas != null) {
                try {
                    canvas.drawColor(Color.rgb(15, 12, 24))
                    val wave = sin(SystemClock.uptimeMillis() / 1600.0).toFloat()
                    val radius = min(canvas.width, canvas.height) * (0.22f + 0.03f * wave)
                    paint.color = Color.rgb(116, 67, 166)
                    canvas.drawCircle(canvas.width / 2f, canvas.height / 2f, radius, paint)
                    paint.color = Color.rgb(164, 231, 72)
                    val y = canvas.height * (0.5f + 0.25f * wave)
                    canvas.drawRect(0f, y, canvas.width.toFloat(), y + 5f, paint)
                } finally {
                    holder.unlockCanvasAndPost(canvas)
                }
            }
            if (visible && surfaceReady) handler.postDelayed(frame, 50L)
        }
    }
}
