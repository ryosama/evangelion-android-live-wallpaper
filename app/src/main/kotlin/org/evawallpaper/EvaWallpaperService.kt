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
    /**
     * Crée une scène indépendante pour chaque instance Android du fond, notamment l’aperçu et le fond
     * installé.
     */
    override fun onCreateEngine(): Engine = MosaicEngine()

    /**
     * Instance possédant sa surface, ses bitmaps et sa population ; callbacks sérialisés sur le thread
     * principal.
     */
    private inner class MosaicEngine : Engine() {
        // Pinceau partagé entre tuiles ; son alpha est réinitialisé après chaque image.
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        // Rectangle réutilisé pour placer chaque bitmap sans allocation par tuile.
        private val destination = RectF()
        // Géométrie en pixels ; seule la largeur nominale est convertie depuis les dp.
        private val layout = MosaicLayout(MosaicLayout.TILE_WIDTH_DP * resources.displayMetrics.density)
        // Visibilité transmise par Android ; aucun rendu n’est planifié si elle est fausse.
        private var visible = false
        // Indique qu’une surface de dessin est disponible, indépendamment de la visibilité.
        private var surfaceReady = false
        // Suit l’abonnement batterie afin de ne pas doubler les inscriptions/désinscriptions.
        private var receiverRegistered = false
        // Planifie tous les rendus sur le thread principal Android.
        private val handler = Handler(Looper.getMainLooper())
        // Callback unique retiré avant replanification pour éviter plusieurs boucles de rendu.
        private val frame = Runnable { drawMosaic() }
        // Table des centres de la grille ; les indices de TilePopulation pointent dans cette liste.
        private var cells = emptyList<MosaicLayout.Cell>()
        // État des cellules allumées et de leurs relais, indépendant du dessin Android.
        private var population: TilePopulation? = null
        // Vrai si le téléphone est alimenté et Android indique une charge active ou terminée.
        private var charging = false
        // Origine de l’animation de charge, en millisecondes de SystemClock.uptimeMillis.
        private var chargeStart = 0L
        // Dernier niveau valide reçu ; -1 signifie qu’aucune mesure exploitable n’est encore connue.
        private var batteryLevel = -1
        // Échelle associée au niveau : ne pas supposer que toutes les mesures sont sur 100.
        private var batteryScale = 100
        // Largeur précédente en pixels ; permet de détecter un changement de géométrie.
        private var surfaceWidth = 0
        // Hauteur précédente en pixels ; permet de détecter un changement de géométrie.
        private var surfaceHeight = 0
        // Accès aux réglages persistés par MainActivity.
        private val store = ConfigStore(this@EvaWallpaperService)
        // Instantané des réglages courants ; remplacé à chaque notification de ConfigStore.
        private var config = store.read()
        // Dernier Intent reçu, réutilisé pour recalculer le rendu après modification des réglages.
        private var lastBattery: Intent? = null
        // Relit les réglages et invalide band pour forcer la régénération des deux bitmaps même si le niveau est inchangé.
        private val configListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            config = store.read()
            chargeStart = SystemClock.uptimeMillis()
            band = null
            lastBattery?.let(::updateBattery)
        }
        // État correspondant au bitmap en cache ; null force sa reconstruction.
        private var band: BatteryBand? = null
        // Bitmap de couleur normale, possédé et recyclé par ce moteur.
        private var tile: Bitmap? = null
        // Variante dans la couleur de charge, avec le même texte que la tuile normale.
        private var chargeTile: Bitmap? = null
        // Récepteur de la mesure Android ; nourrit updateBattery tant que le fond est visible.
        private val batteryReceiver = object : BroadcastReceiver() {
            /**
             * Relaye les notifications batterie Android vers updateBattery ; l’abonnement est limité aux
             * périodes où le fond est visible.
             */
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_BATTERY_CHANGED) updateBattery(intent)
            }
        }

        init { store.preferences.registerOnSharedPreferenceChangeListener(configListener) }

        /**
         * Démarre ou suspend le suivi batterie et le rendu. Au retour visible, relit la mesure courante,
         * relance la population et remet le cycle de charge à zéro.
         */
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

        /**
         * Recalcule les cellules et la population lorsque les dimensions en pixels changent, puis demande
         * une image sur la surface disponible.
         */
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

        /** Répond à une demande explicite de redessin Android en réutilisant le chemin de rendu habituel. */
        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) {
            super.onSurfaceRedrawNeeded(holder)
            drawMosaic()
        }

        /** Empêche tout dessin sur une surface détruite et termine proprement le relais en cours. */
        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            surfaceReady = false
            handler.removeCallbacks(frame)
            population?.pause()
            super.onSurfaceDestroyed(holder)
        }

        /** Libère les abonnements, callbacks et bitmaps possédés par cette instance du moteur. */
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

        /**
         * Désabonne le récepteur batterie une seule fois ; receiverRegistered évite les doubles
         * désinscriptions.
         */
        private fun stopListening() {
            if (receiverRegistered) {
                unregisterReceiver(batteryReceiver)
                receiverRegistered = false
            }
        }

        /**
         * Valide la mesure, choisit BatteryBand, détecte la charge, actualise TilePopulation puis recrée
         * les bitmaps si nécessaire. Utilisée aussi après un changement de configuration.
         */
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
            // Une alimentation branchée sans état CHARGING/FULL ne déclenche pas l’animation.
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

        /**
         * Relie la mesure et les réglages à TilePopulation. Recalcule le nombre même si la couleur ne
         * change pas ; en charge, stabilise immédiatement la scène.
         */
        private fun updatePopulation() {
            if (batteryLevel < 0 || cells.isEmpty()) return
            // Horloge monotone en millisecondes ; évite les sauts liés aux changements d’heure civile.
            val now = SystemClock.uptimeMillis()
            // Cible calculée depuis la capacité, la fraction de batterie et le minimum configuré.
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

        /**
         * Avance la scène hors charge, dessine les bitmaps sur fond noir, applique ChargingPulse pendant la
         * charge puis programme le prochain rendu utile. Toujours exécutée sur le thread principal.
         */
        private fun drawMosaic() {
            handler.removeCallbacks(frame)
            if (!visible || !surfaceReady) return
            // Horloge monotone en millisecondes ; évite les sauts liés aux changements d’heure civile.
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
                    // Indices déjà balayés ce cycle, triés par ChargingPulse avant sélection du nombre rempli.
                    val chargedCells = if (charging) ChargingPulse.bottomToTop(cells, lights)
                        .take(ChargingPulse.filledCount(elapsed, lights.size, config.charging)).toSet() else emptySet()
                    // Multiplicateur commun de l’opacité pendant la charge ; 1 hors charge.
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
