package org.evawallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.BatteryManager
import android.os.Build
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/** Tuiles clairsemées avec relais fluorescent, suspendu lorsque le fond est invisible. */
class EvaWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = MosaicEngine()

    private inner class MosaicEngine : Engine() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val destination = RectF()
        private val layout = MosaicLayout(MosaicLayout.TILE_WIDTH_DP * resources.displayMetrics.density)
        private var visible = false
        private var surfaceReady = false
        private var receiverRegistered = false
        private val handler = Handler(Looper.getMainLooper())
        private val frame = Runnable { drawMosaic() }
        private var cells = emptyList<MosaicLayout.Cell>()
        private var population: TilePopulation? = null
        private var charging = false
        private var chargeStart = 0L
        private var batteryLevel = -1
        private var batteryScale = 100
        private var surfaceWidth = 0
        private var surfaceHeight = 0
        private val store = ConfigStore(this@EvaWallpaperService)
        private var config = store.read()
        private var lastBattery: Intent? = null
        private val configListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            config = store.read()
            chargeStart = SystemClock.uptimeMillis()
            band = null
            lastBattery?.let(::updateBattery)
        }
        private var band: BatteryBand? = null
        private var tile: Bitmap? = null
        private var chargeTile: Bitmap? = null
        private val batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_BATTERY_CHANGED) updateBattery(intent)
            }
        }

        init { store.preferences.registerOnSharedPreferenceChangeListener(configListener) }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                if (!receiverRegistered) {
                    val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                    val current = if (Build.VERSION.SDK_INT >= 33) {
                        registerReceiver(batteryReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
                    } else {
                        registerReceiver(batteryReceiver, filter)
                    }
                    receiverRegistered = true
                    current?.let(::updateBattery)
                }
                chargeStart = SystemClock.uptimeMillis()
                population?.resume(chargeStart)
                drawMosaic()
            } else {
                handler.removeCallbacks(frame)
                population?.pause()
                stopListening()
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            surfaceReady = true
            if (width != surfaceWidth || height != surfaceHeight) {
                surfaceWidth = width
                surfaceHeight = height
                cells = layout.visibleCells(width, height)
                population = null
                updatePopulation()
            }
            drawMosaic()
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) {
            super.onSurfaceRedrawNeeded(holder)
            drawMosaic()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            surfaceReady = false
            handler.removeCallbacks(frame)
            population?.pause()
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            visible = false
            handler.removeCallbacks(frame)
            stopListening()
            store.preferences.unregisterOnSharedPreferenceChangeListener(configListener)
            tile?.recycle()
            tile = null
            chargeTile?.recycle()
            chargeTile = null
            super.onDestroy()
        }

        private fun stopListening() {
            if (receiverRegistered) {
                unregisterReceiver(batteryReceiver)
                receiverRegistered = false
            }
        }

        private fun updateBattery(intent: Intent) {
            lastBattery = intent
            val next = BatteryBand.fromLevel(
                intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
                intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1),
                config.thresholds,
            ) ?: return
            batteryLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            batteryScale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            val nextCharging = plugged && (status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL)
            if (charging != nextCharging) {
                charging = nextCharging
                chargeStart = SystemClock.uptimeMillis()
                if (charging) population?.pause() else population?.resume(chargeStart)
            }
            updatePopulation()
            if (next != band) {
                val nextTile = TileRenderer.render(resources, next, config.style(next))
                tile?.recycle()
                tile = nextTile
                chargeTile?.recycle()
                chargeTile = TileRenderer.render(resources, next, config.style(next).copy(color = config.charging.color))
                band = next
            }
            drawMosaic()
        }

        private fun updatePopulation() {
            if (batteryLevel < 0 || cells.isEmpty()) return
            val now = SystemClock.uptimeMillis()
            val count = TilePopulation.countForBattery(cells.size, batteryLevel, batteryScale, config.minimumTiles)
            val current = population
            if (current == null) {
                population = TilePopulation(cells.size, count, now, effects = config.effects)
            } else {
                current.setEffects(config.effects)
                current.setTarget(count, now)
            }
            if (charging) population?.settleTarget()
        }

        private fun drawMosaic() {
            handler.removeCallbacks(frame)
            if (!visible || !surfaceReady) return
            val now = SystemClock.uptimeMillis()
            val scene = population
            if (!charging) scene?.advance(now)
            val holder = surfaceHolder
            val canvas = holder.lockCanvas()
            if (canvas == null) {
                handler.postDelayed(frame, TilePopulation.FRAME_MS)
                return
            }
            try {
                canvas.drawColor(Color.BLACK)
                val bitmap = tile
                if (bitmap != null && scene != null) {
                    val lights = scene.lights(now)
                    val elapsed = now - chargeStart
                    val chargedCells = if (charging) ChargingPulse.bottomToTop(cells, lights)
                        .take(ChargingPulse.filledCount(elapsed, lights.size, config.charging)).toSet() else emptySet()
                    val pulse = if (charging) ChargingPulse.alpha(elapsed, config.charging) else 1f
                    for (light in lights) {
                        val cell = cells[light.cell]
                        destination.set(
                            cell.centerX - layout.tileWidth / 2, cell.centerY - layout.tileHeight / 2,
                            cell.centerX + layout.tileWidth / 2, cell.centerY + layout.tileHeight / 2,
                        )
                        paint.alpha = (light.alpha * pulse * 255).toInt().coerceIn(0, 255)
                        canvas.drawBitmap(if (light.cell in chargedCells) chargeTile ?: bitmap else bitmap, null, destination, paint)
                    }
                }
            } finally {
                paint.alpha = 255
                holder.unlockCanvasAndPost(canvas)
            }
            // 25 images/s pendant les transitions ou la charge ; attente entre les relais sinon.
            (if (charging) TilePopulation.FRAME_MS else scene?.nextDelay(now))?.let { handler.postDelayed(frame, it) }
        }
    }
}
