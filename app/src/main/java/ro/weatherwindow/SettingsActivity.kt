package ro.weatherwindow

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.os.Build
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

class SettingsActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("weather", Context.MODE_PRIVATE) }
    private val previewViews = mutableMapOf<String, TextView>()
    private val previewTextViews = mutableListOf<TextView>()
    private var previewBackground: View? = null
    private var previewOverlay: View? = null
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val storedOpacity = prefs.getInt("background_opacity", 100).coerceIn(0, 100)
        val snappedOpacity = ((storedOpacity + 5) / 10) * 10
        if (storedOpacity != snappedOpacity) {
            prefs.edit().putInt("background_opacity", snappedOpacity).apply()
            WeatherWidgetProvider.updateBackgroundOpacity(this)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(12))
            setBackgroundColor(Color.rgb(19, 35, 50))
        }
        root.addView(Button(this).apply {
            text = "← Înapoi"
            setOnClickListener { finish() }
        })
        root.addView(TextView(this).apply {
            text = "⚙️ Setări widget"
            textSize = 25f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(10), 0, dp(3))
        })
        root.addView(TextView(this).apply {
            text = "Modifică dimensiunile și vezi exact aranjarea widgetului."
            textSize = 14f
            setTextColor(Color.LTGRAY)
            setPadding(0, 0, 0, dp(8))
        })
        root.addView(TextView(this).apply {
            text = "PREVIZUALIZARE LIVE · STRUCTURĂ REALĂ"
            textSize = 12f
            setTextColor(Color.rgb(175, 200, 218))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(3), 0, dp(5))
        })

        val previewScroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
        }
        val preview = layoutInflater.inflate(R.layout.weather_widget, previewScroll, false)
        preview.findViewById<TextView>(R.id.location).text = "Ploiești"
        preview.findViewById<TextView>(R.id.temperature).text = "18°"
        preview.findViewById<TextView>(R.id.condition).text = "🌤️  Parțial noros"
        preview.findViewById<TextView>(R.id.details).text = "Se simte 17°  •  Umiditate 62%  •  Vânt 12 km/h"
        preview.findViewById<TextView>(R.id.hourly_label).text = "PROGNOZĂ ORARĂ · 6 H"
        val previewHourly = listOf(
            "12:00\n18°\n🌤️\n0%", "13:00\n19°\n☀️\n0%", "14:00\n20°\n☀️\n0%",
            "15:00\n20°\n🌤️\n5%", "16:00\n19°\n🌤️\n5%", "17:00\n18°\n☁️\n10%"
        )
        listOf(R.id.hourly_1, R.id.hourly_2, R.id.hourly_3, R.id.hourly_4, R.id.hourly_5, R.id.hourly_6)
            .forEachIndexed { index, id -> preview.findViewById<TextView>(id).text = previewHourly[index] }
        preview.findViewById<TextView>(R.id.daily_label).text = "URMĂTOARELE 6 ZILE"
        val previewDaily = listOf(
            "Dum\n20°/13°\n☀️\n0%", "Lun\n21°/14°\n🌤️\n5%", "Mar\n19°/12°\n☁️\n10%",
            "Mie\n17°/11°\n🌧️\n65%", "Joi\n18°/10°\n🌦️\n35%", "Vin\n22°/13°\n☀️\n0%"
        )
        listOf(R.id.daily_1, R.id.daily_2, R.id.daily_3, R.id.daily_4, R.id.daily_5, R.id.daily_6)
            .forEachIndexed { index, id -> preview.findViewById<TextView>(id).text = previewDaily[index] }
        previewTextViews.addAll(intArrayOf(
            R.id.location, R.id.temperature, R.id.condition, R.id.details,
            R.id.hourly_label, R.id.daily_label,
            R.id.hourly_1, R.id.hourly_2, R.id.hourly_3, R.id.hourly_4, R.id.hourly_5, R.id.hourly_6,
            R.id.daily_1, R.id.daily_2, R.id.daily_3, R.id.daily_4, R.id.daily_5, R.id.daily_6
        ).map { preview.findViewById<TextView>(it) })
        previewBackground = preview.findViewById(R.id.weather_background)
        previewOverlay = preview.findViewById(R.id.weather_overlay)
        val backgroundOpacity = prefs.getInt("background_opacity", 100).coerceIn(0, 100) / 100f
        previewBackground?.setBackgroundColor(Color.rgb(48, 75, 96))
        previewBackground?.alpha = backgroundOpacity
        previewOverlay?.alpha = backgroundOpacity
        listOf(
            "size_location" to R.id.location,
            "size_temperature" to R.id.temperature,
            "size_condition" to R.id.condition,
            "size_details" to R.id.details,
            "size_hourly" to R.id.hourly_1,
            "size_daily" to R.id.daily_1
        ).forEach { (key, id) -> previewViews[key] = preview.findViewById(id) }
        listOf(R.id.hourly_2, R.id.hourly_3, R.id.hourly_4, R.id.hourly_5, R.id.hourly_6).forEachIndexed { index, id -> previewViews["size_hourly_$index"] = preview.findViewById(id) }
        previewViews["label_hourly"] = preview.findViewById(R.id.hourly_label)
        listOf(R.id.daily_2, R.id.daily_3, R.id.daily_4, R.id.daily_5, R.id.daily_6).forEachIndexed { index, id -> previewViews["size_daily_$index"] = preview.findViewById(id) }
        previewViews["label_daily"] = preview.findViewById(R.id.daily_label)
        previewScroll.addView(preview, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ))
        root.addView(previewScroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(300)
        ))

        root.addView(TextView(this).apply {
            text = "ASPECT FUNDAL"
            textSize = 12f
            setTextColor(Color.rgb(175, 200, 218))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(10), 0, dp(3))
        })
        val controls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        addBackgroundOpacityControl(controls)
        addTextColorControl(controls)
        root.addView(TextView(this).apply {
            text = "DIMENSIUNE TEXT"
            textSize = 12f
            setTextColor(Color.rgb(175, 200, 218))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(10), 0, dp(3))
        })
        addTextSizeControl(controls, "Locație", "size_location", 10, 30, 22)
        addTextSizeControl(controls, "Temperatură", "size_temperature", 24, 64, 53)
        addTextSizeControl(controls, "Condiții meteo", "size_condition", 9, 24, 18)
        addTextSizeControl(controls, "Detalii (resimțită, umiditate, vânt)", "size_details", 8, 22, 16)
        addTextSizeControl(controls, "Prognoză orară", "size_hourly", 8, 16, 12)
        addTextSizeControl(controls, "Prognoză 6 zile", "size_daily", 8, 16, 12)
        val controlsScroll = ScrollView(this).apply {
            isFillViewport = true
            addView(controls)
        }
        root.addView(controlsScroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        root.addView(Button(this).apply {
            text = "Resetează toate setările"
            setOnClickListener {
                prefs.edit()
                    .putInt("shared_size_location", 22).putInt("shared_size_temperature", 53)
                    .putInt("shared_size_condition", 18).putInt("shared_size_details", 16)
                    .putInt("shared_size_hourly", 12).putInt("shared_size_daily", 12)
                    .putInt("background_opacity", 100)
                    .remove("text_color_enabled").remove("text_color")
                    .apply()
                WeatherWidgetProvider.updateTextSizes(this@SettingsActivity)
                WeatherWidgetProvider.updateBackgroundOpacity(this@SettingsActivity)
                WeatherWidgetProvider.updateTextColor(this@SettingsActivity)
                recreate()
            }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })
        root.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val safe = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(dp(18) + safe.left, dp(12) + safe.top,
                    dp(18) + safe.right, dp(12) + safe.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(dp(18) + insets.systemWindowInsetLeft, dp(12) + insets.systemWindowInsetTop,
                    dp(18) + insets.systemWindowInsetRight, dp(12) + insets.systemWindowInsetBottom)
            }
            insets
        }
        setContentView(root)
        applyPreviewSizes()
        applyPreviewTextColor()
    }

    private fun applyPreviewSizes() {
        val sizes = mapOf(
            "size_location" to 22,
            "size_temperature" to 53,
            "size_condition" to 18,
            "size_details" to 16,
            "size_hourly" to 12,
            "size_daily" to 12
        )
        sizes.forEach { (key, defaultValue) ->
            previewViews[key]?.textSize = prefs.getInt(key, defaultValue) * 1.15f
        }
        previewViews.filterKeys { it.startsWith("size_hourly") }.values.forEach { it.textSize = prefs.getInt("shared_size_hourly", prefs.getInt("size_hourly_5x4", 12)).coerceIn(8, 16) * 1.15f }
        previewViews.filterKeys { it.startsWith("size_daily") }.values.forEach { it.textSize = prefs.getInt("shared_size_daily", prefs.getInt("size_daily_5x4", 12)).coerceIn(8, 16) * 1.15f }
        previewViews["label_hourly"]?.textSize = (prefs.getInt("shared_size_hourly", prefs.getInt("size_hourly_5x4", 12)).coerceIn(8, 16) + 1) * 1.15f
        previewViews["label_daily"]?.textSize = (prefs.getInt("shared_size_daily", prefs.getInt("size_daily_5x4", 12)).coerceIn(8, 16) + 1) * 1.15f
    }


    private fun addTextColorControl(parent: LinearLayout) {
        parent.addView(TextView(this).apply {
            text = "CULOAREA TEXTULUI"
            textSize = 12f
            setTextColor(Color.rgb(175, 200, 218))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(10), 0, dp(3))
        })
        val currentLabel = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(0, dp(4), 0, dp(5))
        }
        fun refreshLabel() {
            currentLabel.text = if (prefs.getBoolean("text_color_enabled", false)) {
                val color = prefs.getInt("text_color", Color.WHITE)
                "Culoare selectată: " + String.format("#%06X", 0xFFFFFF and color)
            } else "Culoare selectată: Originală"
        }
        val row = android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        val choices = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        choices.addView(Button(this).apply {
            text = "Original"
            setOnClickListener {
                prefs.edit().remove("text_color_enabled").remove("text_color").apply()
                applyPreviewTextColor()
                WeatherWidgetProvider.updateTextColor(this@SettingsActivity)
                refreshLabel()
            }
        }, LinearLayout.LayoutParams(dp(90), dp(46)).apply { rightMargin = dp(5) })
        val colors = listOf(
            "Alb" to Color.WHITE,
            "Negru" to Color.BLACK,
            "Galben" to Color.rgb(255, 213, 79),
            "Turcoaz" to Color.rgb(128, 222, 234),
            "Albastru" to Color.rgb(100, 181, 246),
            "Verde" to Color.rgb(129, 199, 132),
            "Portocaliu" to Color.rgb(255, 171, 145),
            "Roz" to Color.rgb(244, 143, 177)
        )
        colors.forEach { (name, color) ->
            choices.addView(TextView(this).apply {
                text = "●\n" + name
                textSize = 12f
                gravity = android.view.Gravity.CENTER
                setTextColor(color)
                contentDescription = name
                setPadding(dp(3), dp(3), dp(3), dp(3))
                setOnClickListener {
                    prefs.edit().putBoolean("text_color_enabled", true).putInt("text_color", color).apply()
                    applyPreviewTextColor()
                    WeatherWidgetProvider.updateTextColor(this@SettingsActivity)
                    refreshLabel()
                }
            }, LinearLayout.LayoutParams(dp(68), dp(48)).apply { rightMargin = dp(3) })
        }
        row.addView(choices)
        parent.addView(currentLabel)
        parent.addView(row)
        parent.addView(Button(this).apply {
            text = "Alege o culoare personalizată"
            setOnClickListener { showCustomTextColorDialog { refreshLabel() } }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(4); bottomMargin = dp(7) })
        refreshLabel()
    }

    private fun applyPreviewTextColor() {
        val enabled = prefs.getBoolean("text_color_enabled", false)
        val selected = prefs.getInt("text_color", Color.WHITE)
        val ids = intArrayOf(
            R.id.location, R.id.temperature, R.id.condition, R.id.details,
            R.id.hourly_label, R.id.daily_label,
            R.id.hourly_1, R.id.hourly_2, R.id.hourly_3, R.id.hourly_4, R.id.hourly_5, R.id.hourly_6,
            R.id.daily_1, R.id.daily_2, R.id.daily_3, R.id.daily_4, R.id.daily_5, R.id.daily_6
        )
        val defaults = intArrayOf(
            Color.rgb(247, 250, 252), Color.WHITE, Color.rgb(220, 234, 244), Color.rgb(194, 212, 226),
            Color.rgb(175, 200, 218), Color.rgb(175, 200, 218),
            Color.rgb(240, 245, 248), Color.rgb(240, 245, 248), Color.rgb(240, 245, 248),
            Color.rgb(240, 245, 248), Color.rgb(240, 245, 248), Color.rgb(240, 245, 248),
            Color.rgb(240, 245, 248), Color.rgb(240, 245, 248), Color.rgb(240, 245, 248),
            Color.rgb(240, 245, 248), Color.rgb(240, 245, 248), Color.rgb(240, 245, 248)
        )
        previewTextViews.forEachIndexed { index, view ->
            view.setTextColor(if (enabled) selected else defaults[index])
        }
    }

    private fun showCustomTextColorDialog(onApplied: () -> Unit) {
        val existing = if (prefs.getBoolean("text_color_enabled", false))
            prefs.getInt("text_color", Color.WHITE) else Color.WHITE
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
        }
        val sample = TextView(this).apply {
            text = "WeatherWindow  18°  ·  Prognoză meteo"
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(8), 0, dp(12))
            gravity = android.view.Gravity.CENTER
        }
        panel.addView(sample)
        val bars = arrayOfNulls<SeekBar>(3)
        val labels = arrayOf("Roșu", "Verde", "Albastru")
        val initial = intArrayOf(Color.red(existing), Color.green(existing), Color.blue(existing))
        val channelLabels = arrayOfNulls<TextView>(3)
        var chosenColor = existing
        fun updateSample() {
            val rgb = IntArray(3) { bars[it]?.progress ?: initial[it] }
            chosenColor = Color.rgb(rgb[0], rgb[1], rgb[2])
            sample.setTextColor(chosenColor)
            for (i in 0..2) channelLabels[i]?.text = labels[i] + ": " + rgb[i]
        }
        for (i in 0..2) {
            val channelLabel = TextView(this).apply {
                textSize = 13f
                setTextColor(Color.WHITE)
            }
            channelLabels[i] = channelLabel
            panel.addView(channelLabel)
            val bar = SeekBar(this).apply {
                max = 255
                progress = initial[i]
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = updateSample()
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            }
            bars[i] = bar
            panel.addView(bar)
        }
        updateSample()
        AlertDialog.Builder(this)
            .setTitle("Culoare personalizată · RGB")
            .setView(panel)
            .setNegativeButton("Anulează", null)
            .setPositiveButton("Aplică") { _, _ ->
                prefs.edit().putBoolean("text_color_enabled", true).putInt("text_color", chosenColor).apply()
                applyPreviewTextColor()
                WeatherWidgetProvider.updateTextColor(this@SettingsActivity)
                onApplied()
            }
            .show()
    }

    private fun addBackgroundOpacityControl(parent: LinearLayout) {
        val current = (((prefs.getInt("background_opacity", 100).coerceIn(0, 100) + 5) / 10) * 10)
        val valueLabel = TextView(this).apply {
            text = "Opacitate fundal: $current%"
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(0, dp(7), 0, 0)
        }
        val seekBar = SeekBar(this).apply {
            max = 10
            progress = current / 10
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val opacity = progress * 10
                    valueLabel.text = "Opacitate fundal: $opacity%"
                    if (fromUser) {
                        prefs.edit().putInt("background_opacity", opacity).apply()
                        val alpha = opacity / 100f
                        previewBackground?.alpha = alpha
                        previewOverlay?.alpha = alpha
                        WeatherWidgetProvider.updateBackgroundOpacity(this@SettingsActivity)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        parent.addView(valueLabel)
        parent.addView(seekBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) })
    }

    private fun addTextSizeControl(
        parent: LinearLayout, label: String, preferenceKey: String,
        min: Int, max: Int, defaultValue: Int
    ) {
        val storedKey = "shared_${preferenceKey}"
        val current = prefs.getInt(storedKey, defaultValue).coerceIn(min, max)
        val valueLabel = TextView(this).apply {
            text = "$label: ${current}sp"
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(0, dp(7), 0, 0)
        }
        val seekBar = SeekBar(this).apply {
            this.max = max - min
            progress = current - min
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = min + progress
                    valueLabel.text = "$label: ${value}sp"
                    if (fromUser) {
                        prefs.edit().putInt(storedKey, value).apply()
                        previewViews[preferenceKey]?.textSize = value * 1.15f
                        if (preferenceKey == "size_hourly") {
                            previewViews.filterKeys { it.startsWith("size_hourly") }.values.forEach { it.textSize = value * 1.15f }
                            previewViews["label_hourly"]?.textSize = (value + 1) * 1.15f
                        }
                        if (preferenceKey == "size_daily") {
                            previewViews.filterKeys { it.startsWith("size_daily") }.values.forEach { it.textSize = value.toFloat() }
                            previewViews["label_daily"]?.textSize = (value + 1).toFloat()
                        }
                        WeatherWidgetProvider.updateTextSizes(this@SettingsActivity)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        parent.addView(valueLabel)
        parent.addView(seekBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(5) })
    }
}
