package ro.weatherwindow

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

class SettingsActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("weather", Context.MODE_PRIVATE) }
    private val previewTexts = mutableMapOf<String, TextView>()

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
            text = "Modifică dimensiunile textului și vezi rezultatul în previzualizarea live."
            textSize = 14f
            setTextColor(Color.LTGRAY)
            setPadding(0, 0, 0, dp(8))
        })

        root.addView(TextView(this).apply {
            text = "PREVIZUALIZARE LIVE"
            textSize = 12f
            setTextColor(Color.rgb(175, 200, 218))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(3), 0, dp(5))
        })

        val previewScroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
        }
        val previewCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(Color.rgb(32, 53, 70))
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        previewTexts["size_location"] = previewText("Ploiești", 22f, true).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        previewTexts["size_temperature"] = previewText("18°", 53f, true).apply {
            gravity = Gravity.END
        }
        topRow.addView(previewTexts.getValue("size_location"))
        topRow.addView(previewTexts.getValue("size_temperature"))
        previewCard.addView(topRow)

        previewTexts["size_condition"] = previewText("🌤️  Parțial noros", 18f)
        previewCard.addView(previewTexts.getValue("size_condition"))
        previewTexts["size_details"] = previewText("Se simte 17°  •  Umiditate 62%  •  Vânt 12 km/h", 16f)
        previewCard.addView(previewTexts.getValue("size_details"))

        previewCard.addView(separator())
        previewTexts["label_hourly"] = previewText("PROGNOZĂ ORARĂ · 24 H", 15f, true)
        previewCard.addView(previewTexts.getValue("label_hourly"))
        previewTexts["size_hourly"] = previewText("12:00 🌤️ 18°/0%  ·  13:00 ☀️ 19°/0%\n14:00 ☀️ 20°/0%  ·  15:00 🌤️ 20°/5%", 14f)
        previewTexts.getValue("size_hourly").typeface = Typeface.MONOSPACE
        previewCard.addView(previewTexts.getValue("size_hourly"))

        previewCard.addView(separator())
        previewTexts["label_daily"] = previewText("URMĂTOARELE 7 ZILE", 12f, true)
        previewCard.addView(previewTexts.getValue("label_daily"))
        previewTexts["size_daily"] = previewText(
            "Azi ☀️ 18°/12°  ·  Lun 🌤️ 20°/13°\nMar ☁️ 19°/11°  ·  Mie 🌧️ 17°/10°\nJoi 🌦️ 18°/9°  ·  Vin ☀️ 21°/12°\nSâm 🌤️ 22°/13°",
            11f
        )
        previewCard.addView(previewTexts.getValue("size_daily"))

        previewScroll.addView(previewCard)
        root.addView(previewScroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(250)
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

        setContentView(root)
        applyPreviewSizes()
    }

    private fun previewText(value: String, size: Float, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(Color.WHITE)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(2), 0, dp(2))
        }

    private fun separator(): View = View(this).apply {
        setBackgroundColor(Color.rgb(84, 112, 132))
    }.also {
        it.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
        ).apply {
            topMargin = dp(5)
            bottomMargin = dp(4)
        }
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
            previewTexts[key]?.textSize = prefs.getInt(key, defaultValue).toFloat()
        }
        previewTexts["label_hourly"]?.textSize = (prefs.getInt("size_hourly", 14) + 1).toFloat()
        previewTexts["label_daily"]?.textSize = (prefs.getInt("size_daily", 11) + 1).toFloat()
    }

    private fun addTextSizeControl(
        parent: LinearLayout,
        label: String,
        preferenceKey: String,
        min: Int,
        max: Int,
        defaultValue: Int
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
                        previewTexts[preferenceKey]?.textSize = value.toFloat()
                        if (preferenceKey == "size_hourly") {
                            previewTexts["label_hourly"]?.textSize = (value + 1).toFloat()
                        }
                        if (preferenceKey == "size_daily") {
                            previewTexts["label_daily"]?.textSize = (value + 1).toFloat()
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
