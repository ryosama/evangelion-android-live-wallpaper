package org.evawallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.android.material.radiobutton.MaterialRadioButton
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var store: ConfigStore
    private lateinit var draft: WallpaperConfig
    private lateinit var ranges: TextView
    private val previews = mutableListOf<ImageView>()
    private val previewBitmaps = mutableListOf<Bitmap>()
    private val names = listOf("Parfait", "OK", "Mauvais", "Critique")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ConfigStore(this)
        draft = savedInstanceState?.getString("draft")?.let {
            runCatching { ConfigStore.decode(it) }.getOrNull()
        } ?: store.read()
        showConfiguration()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("draft", ConfigStore.encode(draft))
        super.onSaveInstanceState(outState)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun label(value: String, size: Float = 16f) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(Color.WHITE)
        setPadding(0, dp(8), 0, dp(8))
    }

    private fun button(value: String, action: () -> Unit) = MaterialButton(this).apply {
        text = value
        isAllCaps = false
        setOnClickListener { action() }
    }

    private fun showConfiguration() {
        previews.clear()
        val previousBitmaps = previewBitmaps.toList()
        previewBitmaps.clear()
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(16, 16, 20)) }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(24))
        }
        scroll.addView(content)
        setContentView(scroll)
        previousBitmaps.forEach { it.recycle() }
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(scroll)
        content.addView(label("Mon fond d’écran", 26f))
        content.addView(label("Aperçu des quatre états", 18f))
        val previewRow = LinearLayout(this)
        BatteryBand.entries.forEachIndexed { index, band ->
            val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val image = ImageView(this).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = "Tuile ${names[index]}"
            }
            previews.add(image)
            val bitmap = TileRenderer.render(resources, band, draft.styles[index])
            previewBitmaps.add(bitmap)
            image.setImageBitmap(bitmap)
            column.addView(image, LinearLayout.LayoutParams(-1, dp(86)))
            column.addView(label(names[index], 12f).apply { gravity = Gravity.CENTER })
            previewRow.addView(column, LinearLayout.LayoutParams(0, -2, 1f))
        }
        content.addView(previewRow)
        content.addView(label("Niveaux de batterie", 21f))
        content.addView(label("Déplace les trois curseurs pour répartir les quatre états. Chaque seuil appartient à l’état supérieur.", 14f))
        content.addView(LinearLayout(this).apply {
            addView(label("0 %", 12f), LinearLayout.LayoutParams(0, -2, 1f))
            addView(label("100 %", 12f).apply { gravity = Gravity.END }, LinearLayout.LayoutParams(0, -2, 1f))
        })
        val slider = BatteryRangeSlider(this).apply {
            segmentColors = draft.styles.reversed().map { it.color }
            valueFrom = 0f
            valueTo = 100f
            stepSize = 1f
            values = listOf(draft.thresholds.bad.toFloat(), draft.thresholds.ok.toFloat(), draft.thresholds.perfect.toFloat())
            setMinSeparationValue(1f)
            setLabelFormatter { "${it.toInt()} %" }
            contentDescription = "Seuils Mauvais, OK et Parfait, en pourcentage de batterie"
        }
        content.addView(slider, LinearLayout.LayoutParams(-1, -2))
        ranges = label("")
        updateRanges()
        content.addView(ranges)
        slider.addOnChangeListener { control, _, fromUser ->
            if (fromUser) {
                val values = control.values.map { it.toInt() }
                val bad = values[0].coerceIn(1, 98)
                val ok = values[1].coerceIn(bad + 1, 99)
                val perfect = values[2].coerceIn(ok + 1, 100)
                draft = draft.copy(thresholds = BatteryThresholds(bad, ok, perfect))
                val corrected = listOf(bad.toFloat(), ok.toFloat(), perfect.toFloat())
                if (control.values != corrected) control.values = corrected
                updateRanges()
            }
        }
        content.addView(label(getString(R.string.density_title), 21f))
        content.addView(label(getString(R.string.density_description), 14f))
        val minimumLabel = label(getString(R.string.minimum_tiles_value, draft.density.minimumTiles))
        content.addView(minimumLabel)
        content.addView(Slider(this).apply {
            valueFrom = 0f
            valueTo = 100f
            stepSize = 1f
            value = draft.density.minimumTiles.toFloat()
            contentDescription = getString(R.string.minimum_tiles_accessibility)
            setLabelFormatter { getString(R.string.tile_count_value, it.toInt()) }
            addOnChangeListener { _, value, fromUser ->
                if (fromUser) {
                    draft = draft.copy(density = draft.density.copy(minimumTiles = value.toInt()))
                    minimumLabel.text = getString(R.string.minimum_tiles_value, draft.density.minimumTiles)
                }
            }
        }, LinearLayout.LayoutParams(-1, -2))
        val ratioLabel = label(getString(R.string.tile_ratio_value, draft.density.tileDivisor))
        content.addView(ratioLabel)
        content.addView(Slider(this).apply {
            valueFrom = 1f
            valueTo = 20f
            stepSize = 1f
            value = draft.density.tileDivisor.toFloat()
            contentDescription = getString(R.string.tile_ratio_accessibility)
            setLabelFormatter { getString(R.string.one_in_value, it.toInt()) }
            addOnChangeListener { _, value, fromUser ->
                if (fromUser) {
                    draft = draft.copy(density = draft.density.copy(tileDivisor = value.toInt()))
                    ratioLabel.text = getString(R.string.tile_ratio_value, draft.density.tileDivisor)
                }
            }
        }, LinearLayout.LayoutParams(-1, -2))
        content.addView(label(getString(R.string.effects_title), 21f))
        content.addView(effectSelector(R.string.appearance_effect, R.string.fade_in, draft.effects.appearance) {
            draft = draft.copy(effects = draft.effects.copy(appearance = it))
        })
        content.addView(effectSelector(R.string.disappearance_effect, R.string.fade_out, draft.effects.disappearance) {
            draft = draft.copy(effects = draft.effects.copy(disappearance = it))
        })
        content.addView(label("Couleurs et textes", 21f))
        BatteryBand.entries.forEachIndexed { index, _ ->
            content.addView(label(names[index], 18f))
            val colorButton = button(getString(R.string.color_button, hex(draft.styles[index].color))) {}
            styleColorButton(colorButton, draft.styles[index].color)
            colorButton.setOnClickListener {
                chooseColor(index) {
                    colorButton.text = getString(R.string.color_button, hex(draft.styles[index].color))
                    styleColorButton(colorButton, draft.styles[index].color)
                    slider.segmentColors = draft.styles.reversed().map { it.color }
                    updatePreview(index)
                    updateRanges()
                }
            }
            content.addView(colorButton)
            val text = EditText(this).apply {
                hint = "Texte de la tuile (vide = aucun)"
                setHintTextColor(Color.LTGRAY)
                setTextColor(Color.WHITE)
                contentDescription = "Texte ${names[index]}"
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                setSingleLine(true)
                filters = arrayOf(InputFilter.LengthFilter(24))
                setText(draft.styles[index].text)
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        updateStyle(index, draft.styles[index].copy(text = s.toString()))
                        updatePreview(index)
                    }
                    override fun afterTextChanged(s: Editable?) = Unit
                })
            }
            content.addView(text, LinearLayout.LayoutParams(-1, dp(56)))
        }
        content.addView(button("Appliquer") {
            store.write(draft)
            Toast.makeText(this, "Réglages appliqués au fond d’écran", Toast.LENGTH_SHORT).show()
        })
        content.addView(button(getString(R.string.preview)) {
            store.write(draft)
            startActivity(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(this@MainActivity, EvaWallpaperService::class.java))
            })
        })
        content.addView(button("Restaurer les valeurs par défaut") {
            draft = WallpaperConfig()
            showConfiguration()
            Toast.makeText(this, "Valeurs restaurées. Touche Appliquer pour enregistrer.", Toast.LENGTH_SHORT).show()
        })
    }

    private fun styleColorButton(button: MaterialButton, color: Int) {
        button.backgroundTintList = ColorStateList.valueOf(color)
        val foreground = if (ColorUtils.calculateContrast(Color.WHITE, color) >=
            ColorUtils.calculateContrast(Color.BLACK, color)) Color.WHITE else Color.BLACK
        button.setTextColor(foreground)
        button.strokeWidth = dp(1)
        button.strokeColor = ColorStateList.valueOf(foreground)
        button.rippleColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(foreground, 48))
    }

    private fun effectSelector(title: Int, fadeLabel: Int, selected: TileEffect, changed: (TileEffect) -> Unit): LinearLayout {
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(label(getString(title), 18f))
        val group = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        val flicker = MaterialRadioButton(this).apply {
            id = android.view.View.generateViewId()
            setText(R.string.flicker_effect)
            minHeight = dp(48)
        }
        val fade = MaterialRadioButton(this).apply {
            id = android.view.View.generateViewId()
            setText(fadeLabel)
            minHeight = dp(48)
        }
        group.addView(flicker)
        group.addView(fade)
        group.check(if (selected == TileEffect.FLICKER) flicker.id else fade.id)
        group.setOnCheckedChangeListener { _, id ->
            changed(if (id == flicker.id) TileEffect.FLICKER else TileEffect.FADE)
        }
        column.addView(group)
        return column
    }

    private fun updateRanges() {
        val t = draft.thresholds
        ranges.text = getString(R.string.battery_ranges, t.bad, t.ok, t.perfect)
    }

    private fun updateStyle(index: Int, value: TileStyle) {
        draft = draft.copy(styles = draft.styles.toMutableList().apply { set(index, value) })
    }

    private fun updatePreview(index: Int) {
        val bitmap = TileRenderer.render(resources, BatteryBand.entries[index], draft.styles[index])
        previews[index].setImageBitmap(bitmap)
        previewBitmaps[index].recycle()
        previewBitmaps[index] = bitmap
    }

    private fun hex(color: Int) = String.format(Locale.ROOT, "#%06X", color and 0xFFFFFF)

    private fun chooseColor(index: Int, changed: () -> Unit) {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(8))
        }
        val input = EditText(this).apply {
            setSingleLine(true)
            hint = "#RRGGBB"
            contentDescription = "Code de couleur hexadécimal"
            filters = arrayOf(InputFilter.LengthFilter(7))
            setText(hex(draft.styles[index].color))
        }
        panel.addView(input)
        val swatch = TextView(this).apply {
            text = " "
            setBackgroundColor(draft.styles[index].color)
        }
        panel.addView(swatch, LinearLayout.LayoutParams(-1, dp(32)))
        // Un sélecteur RGB permet le choix visuel, sans connaître un code couleur.
        val channels = intArrayOf(Color.red(draft.styles[index].color), Color.green(draft.styles[index].color), Color.blue(draft.styles[index].color))
        val colorBars = mutableListOf<android.widget.SeekBar>()
        listOf("Rouge", "Vert", "Bleu").forEachIndexed { channel, name ->
            panel.addView(TextView(this).apply { text = name })
            panel.addView(android.widget.SeekBar(this).apply {
                colorBars.add(this)
                max = 255
                progress = channels[channel]
                contentDescription = name
                setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar: android.widget.SeekBar, value: Int, fromUser: Boolean) {
                        if (!fromUser) return
                        channels[channel] = value
                        val color = Color.rgb(channels[0], channels[1], channels[2])
                        input.setText(hex(color))
                        swatch.setBackgroundColor(color)
                    }
                    override fun onStartTrackingTouch(bar: android.widget.SeekBar) = Unit
                    override fun onStopTrackingTouch(bar: android.widget.SeekBar) = Unit
                })
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val value = s.toString().trim()
                if (!value.matches(Regex("#[0-9a-fA-F]{6}"))) return
                val color = value.toColorInt()
                swatch.setBackgroundColor(color)
                channels[0] = Color.red(color)
                channels[1] = Color.green(color)
                channels[2] = Color.blue(color)
                colorBars.forEachIndexed { index, bar -> bar.progress = channels[index] }
            }
        })
        val dialog = MaterialAlertDialogBuilder(this).setTitle("Couleur — ${names[index]}")
            .setView(panel).setNegativeButton("Annuler", null).setPositiveButton("Choisir", null).create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = input.text.toString().trim()
                if (!value.matches(Regex("#[0-9a-fA-F]{6}"))) {
                    input.error = "Utilise le format #RRGGBB"
                } else {
                    updateStyle(index, draft.styles[index].copy(color = value.toColorInt()))
                    changed()
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    override fun onDestroy() {
        previews.forEach { it.setImageDrawable(null) }
        previewBitmaps.forEach { it.recycle() }
        super.onDestroy()
    }
}
