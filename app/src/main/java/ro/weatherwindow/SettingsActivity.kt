package ro.weatherwindow

import android.app.Activity
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
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        preview.findViewById<TextView>(R.id.hourly_left).text =
            "12:00 🌤️ 18°/0%\n13:00 ☀️ 19°/0%\n14:00 ☀️ 20°/0%"
        preview.findViewById<TextView>(R.id.hourly_right).text =
            "15:00 🌤️ 20°/5%\n16:00 🌤️ 19°/5%\n17:00 ☁️ 18°/10%"
        preview.findViewById<TextView>(R.id.daily_label).text = "URMĂTOARELE 7 ZILE"
        preview.findViewById<TextView>(R.id.daily).text =
            "Azi ☀️ 18°/12°  ·  Lun 🌤️ 20°/13°\nMar ☁️ 19°/11°  ·  Mie 🌧️ 17°/10°\nJoi 🌦️ 18°/9°  ·  Vin ☀️ 21°/12°\nSâm 🌤️ 22°/13°"
        preview.findViewById<View>(R.id.weather_background).setBackgroundColor(Color.rgb(48, 75, 96))
        preview.findViewById<View>(R.id.weather_background).alpha = 0.35f
        listOf(
            "size_location" to R.id.location,
            "size_temperature" to R.id.temperature,
            "size_condition" to R.id.condition,
            "size_details" to R.id.details,
            "size_hourly" to R.id.hourly_left,
            "size_daily" to R.id.daily
        ).forEach { (key, id) -> previewViews[key] = preview.findViewById(id) }
        previewViews["size_hourly_right"] = preview.findViewById(R.id.hourly_right)
        previewViews["label_hourly"] = preview.findViewById(R.id.hourly_label)
        previewViews["label_daily"] = preview.findViewById(R.id.daily_label)
        previewScroll.addView(preview, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ))
        root.addView(previewScroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(300)
        ))

        root.addView(TextView(this).apply {
            text = "DIMENSIUNE TEXT"
            textSize = 12f
            setTextColor(Color.rgb(175, 200, 218))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(10), 0, dp(3))
        })
        val controls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        addTextSizeControl(controls, "Locație", "size_location", 10, 30, 22)
        addTextSizeControl(controls, "Temperatură", "size_temperature", 24, 64, 53)
        addTextSizeControl(controls, "Condiții meteo", "size_condition", 9, 24, 18)
        addTextSizeControl(controls, "Detalii (resimțită, umiditate, vânt)", "size_details", 8, 22, 16)
        addTextSizeControl(controls, "Prognoză orară", "size_hourly", 7, 20, 14)
        addTextSizeControl(controls, "Prognoză 7 zile", "size_daily", 7, 18, 11)
        val controlsScroll = ScrollView(this).apply {
            isFillViewport = true
            addView(controls)
        }
        root.addView(controlsScroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        root.addView(Button(this).apply {
            text = "Resetează la dimensiunile implicite"
            setOnClickListener {
                prefs.edit()
                    .putInt("size_location", 22)
                    .putInt("size_temperature", 53)
                    .putInt("size_condition", 18)
                    .putInt("size_details", 16)
                    .putInt("size_hourly", 14)
                    .putInt("size_daily", 11)
                    .apply()
                WeatherWidgetProvider.updateTextSizes(this@SettingsActivity)
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
    }

    private fun applyPreviewSizes() {
        val sizes = mapOf(
            "size_location" to 22,
            "size_temperature" to 53,
            "size_condition" to 18,
            "size_details" to 16,
            "size_hourly" to 14,
            "size_daily" to 11
        )
        sizes.forEach { (key, defaultValue) ->
            previewViews[key]?.textSize = prefs.getInt(key, defaultValue).toFloat()
        }
        previewViews["size_hourly_right"]?.textSize = prefs.getInt("size_hourly", 14).toFloat()
        previewViews["label_hourly"]?.textSize = (prefs.getInt("size_hourly", 14) + 1).toFloat()
        previewViews["label_daily"]?.textSize = (prefs.getInt("size_daily", 11) + 1).toFloat()
    }

    private fun addTextSizeControl(
        parent: LinearLayout, label: String, preferenceKey: String,
        min: Int, max: Int, defaultValue: Int
    ) {
        val current = prefs.getInt(preferenceKey, defaultValue).coerceIn(min, max)
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
                        prefs.edit().putInt(preferenceKey, value).apply()
                        previewViews[preferenceKey]?.textSize = value.toFloat()
                        if (preferenceKey == "size_hourly") {
                            previewViews["size_hourly_right"]?.textSize = value.toFloat()
                            previewViews["label_hourly"]?.textSize = (value + 1).toFloat()
                        }
                        if (preferenceKey == "size_daily") {
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
