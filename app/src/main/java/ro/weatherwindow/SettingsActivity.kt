package ro.weatherwindow

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

class SettingsActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("weather", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
            setBackgroundColor(Color.rgb(19, 35, 50))
        }

        val backButton = Button(this).apply {
            text = "← Înapoi"
            setOnClickListener { finish() }
        }
        root.addView(backButton)

        val title = TextView(this).apply {
            text = "⚙️ Setări widget"
            textSize = 25f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 18, 0, 4)
        }
        root.addView(title)

        val subtitle = TextView(this).apply {
            text = "Ajustează separat dimensiunea fiecărei categorii de text. Modificările se aplică imediat pe widget."
            textSize = 14f
            setTextColor(Color.LTGRAY)
            setPadding(0, 0, 0, 18)
        }
        root.addView(subtitle)

        addTextSizeControl(root, "Locație", "size_location", 10, 30, 22)
        addTextSizeControl(root, "Temperatură", "size_temperature", 24, 64, 53)
        addTextSizeControl(root, "Condiții meteo", "size_condition", 9, 24, 18)
        addTextSizeControl(root, "Detalii (resimțită, umiditate, vânt)", "size_details", 8, 22, 16)
        addTextSizeControl(root, "Prognoză orară", "size_hourly", 7, 20, 14)
        addTextSizeControl(root, "Prognoză 7 zile", "size_daily", 7, 18, 11)

        val resetButton = Button(this).apply {
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
        }
        root.addView(resetButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = 22
        })

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        }
        setContentView(scroll)
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
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(0, 14, 0, 0)
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
                        WeatherWidgetProvider.updateTextSizes(this@SettingsActivity)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        parent.addView(valueLabel)
        parent.addView(seekBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = 8
        })
    }
}
