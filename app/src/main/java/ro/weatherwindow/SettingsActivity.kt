package ro.weatherwindow

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
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
import kotlin.math.atan2
import kotlin.math.sqrt

class SettingsActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("weather", Context.MODE_PRIVATE) }
    private val previewViews = mutableMapOf<String, TextView>()
    private val previewTextViews = mutableListOf<TextView>()
    private var previewBackground: View? = null
    private var previewOverlay: View? = null
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private val previewTextScale = 0.72f
    private fun previewScaleFor(key: String): Float = when (key) {
        "size_location" -> 0.52f
        "size_temperature" -> 0.46f
        "size_details" -> 0.52f
        else -> previewTextScale
    }

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
        previewBackground?.visibility = View.GONE
        val backgroundOpacity = prefs.getInt("background_opacity", 100).coerceIn(0, 100) / 100f
        previewOverlay?.alpha = backgroundOpacity * 0.85f
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
        // Dedicated square 4x4 preview, independent from the live launcher widget size.
        val previewSize = minOf(dp(320), (resources.displayMetrics.widthPixels - dp(36)).coerceAtLeast(dp(240)))
        previewScroll.addView(preview, FrameLayout.LayoutParams(
            previewSize, previewSize, android.view.Gravity.CENTER
        ))
        val previewContent = preview.findViewById<LinearLayout>(R.id.widget_content)
        previewContent.setPadding(dp(8), dp(8), dp(8), dp(8))
        val previewHeader = previewContent.getChildAt(0)
        previewHeader.layoutParams = previewHeader.layoutParams.apply { height = dp(70) }
        val previewAnimation = preview.findViewById<View>(R.id.weather_animation)
        previewAnimation.layoutParams = previewAnimation.layoutParams.apply {
            width = dp(92)
            height = dp(46)
        }
        root.addView(previewScroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, previewSize
        ))

        root.addView(TextView(this).apply {
            text = "EFECT STICLĂ"
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
            previewViews[key]?.textSize = prefs.getInt("shared_$key", defaultValue) * previewScaleFor(key)
        }
        previewViews.filterKeys { it.startsWith("size_hourly") }.values.forEach { it.textSize = prefs.getInt("shared_size_hourly", prefs.getInt("size_hourly_5x4", 12)).coerceIn(8, 16) * previewTextScale }
        previewViews.filterKeys { it.startsWith("size_daily") }.values.forEach { it.textSize = prefs.getInt("shared_size_daily", prefs.getInt("size_daily_5x4", 12)).coerceIn(8, 16) * previewTextScale }
        previewViews["label_hourly"]?.textSize = (prefs.getInt("shared_size_hourly", prefs.getInt("size_hourly_5x4", 12)).coerceIn(8, 16) + 1) * previewTextScale
        previewViews["label_daily"]?.textSize = (prefs.getInt("shared_size_daily", prefs.getInt("size_daily_5x4", 12)).coerceIn(8, 16) + 1) * previewTextScale
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
        val hsv = FloatArray(3)
        Color.colorToHSV(existing, hsv)
        var chosenColor = existing
        var brightness = hsv[2]

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(4))
        }
        val selectionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val previewColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(dp(8), 0, dp(4), 0)
        }
        val temperatureSample = TextView(this).apply {
            text = "22°"
            textSize = 38f
            typeface = Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            setTextColor(existing)
        }
        val conditionSample = TextView(this).apply {
            text = "☀️  Cer senin"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            setTextColor(existing)
            setPadding(0, dp(2), 0, dp(5))
        }
        val summarySample = TextView(this).apply {
            text = "Ploiești · 14:00\nSe simte 21°\nVânt 8 km/h · Ploaie 0%"
            textSize = 11f
            gravity = android.view.Gravity.CENTER
            setTextColor(existing)
            setLineSpacing(dp(2).toFloat(), 1f)
        }
        val eye = TextView(this).apply {
            text = "◉"
            textSize = 26f
            gravity = android.view.Gravity.CENTER
            setTextColor(existing)
        }
        val previewCaption = TextView(this).apply {
            text = "PREVIZUALIZARE"
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.LTGRAY)
            gravity = android.view.Gravity.CENTER
            setPadding(0, dp(5), 0, dp(4))
        }
        val hexLabel = TextView(this).apply {
            text = String.format("#%06X", 0xFFFFFF and existing)
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
            setPadding(0, dp(6), 0, 0)
        }

        fun refreshSample(color: Int) {
            temperatureSample.setTextColor(color)
            conditionSample.setTextColor(color)
            summarySample.setTextColor(color)
            eye.setTextColor(color)
            hexLabel.text = String.format("#%06X", 0xFFFFFF and color)
        }

        val wheel = ColorWheelView(this, existing) { color ->
            chosenColor = color
            refreshSample(color)
        }
        selectionRow.addView(wheel, LinearLayout.LayoutParams(dp(205), dp(205)))
        previewColumn.addView(eye)
        previewColumn.addView(temperatureSample)
        previewColumn.addView(conditionSample)
        previewColumn.addView(summarySample)
        previewColumn.addView(previewCaption)
        previewColumn.addView(hexLabel)
        selectionRow.addView(previewColumn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        panel.addView(selectionRow)

        val intensityLabel = TextView(this).apply {
            text = "Luminozitate / intensitate: ${(brightness * 100).toInt()}%"
            textSize = 13f
            setTextColor(Color.WHITE)
            setPadding(dp(2), dp(10), 0, 0)
        }
        val intensitySeek = SeekBar(this).apply {
            max = 100
            progress = (brightness * 100).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    brightness = progress / 100f
                    intensityLabel.text = "Luminozitate / intensitate: ${progress}%"
                    wheel.setBrightness(brightness)
                    chosenColor = wheel.currentColor()
                    refreshSample(chosenColor)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        panel.addView(intensityLabel)
        panel.addView(intensitySeek)

        AlertDialog.Builder(this)
            .setTitle("Alege culoarea textului")
            .setMessage("Atinge și glisează pe cerc. Spre margine culoarea este mai saturată; spre centru devine mai pală.")
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

    private inner class ColorWheelView(
        context: Context,
        initialColor: Int,
        private val onColorChanged: (Int) -> Unit
    ) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var wheelBitmap: Bitmap? = null
        private var centerX = 0f
        private var centerY = 0f
        private var radius = 0f
        private var hue = 0f
        private var saturation = 0f
        private var brightness = 1f
        private var selectedColor = initialColor

        init {
            val hsv = FloatArray(3)
            Color.colorToHSV(initialColor, hsv)
            hue = hsv[0]
            saturation = hsv[1]
            brightness = hsv[2]
            isClickable = true
        }

        fun setBrightness(value: Float) {
            brightness = value.coerceIn(0f, 1f)
            updateSelectedColor()
        }

        fun currentColor(): Int = selectedColor

        private fun updateSelectedColor() {
            selectedColor = Color.HSVToColor(floatArrayOf(hue, saturation, brightness))
            onColorChanged(selectedColor)
            invalidate()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val desired = dp(205)
            setMeasuredDimension(
                resolveSize(desired, widthMeasureSpec),
                resolveSize(desired, heightMeasureSpec)
            )
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            centerX = w / 2f
            centerY = h / 2f
            radius = (minOf(w, h) / 2f) - dp(9)
            val side = minOf(w, h).coerceAtLeast(1)
            val pixels = IntArray(side * side)
            val cx = (side - 1) / 2f
            val cy = (side - 1) / 2f
            val r = side / 2f - 2f
            for (y in 0 until side) {
                for (x in 0 until side) {
                    val dx = x - cx
                    val dy = y - cy
                    val distance = sqrt(dx * dx + dy * dy)
                    if (distance <= r) {
                        val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                        val pixelHue = (angle + 360f) % 360f
                        val sat = (distance / r).coerceIn(0f, 1f)
                        pixels[y * side + x] = Color.HSVToColor(floatArrayOf(pixelHue, sat, 1f))
                    } else {
                        pixels[y * side + x] = Color.TRANSPARENT
                    }
                }
            }
            wheelBitmap = Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val bitmap = wheelBitmap ?: return
            val side = minOf(width, height).toFloat()
            val left = (width - side) / 2f
            val top = (height - side) / 2f
            canvas.drawBitmap(bitmap, null, android.graphics.RectF(left, top, left + side, top + side), paint)
            val angle = Math.toRadians(hue.toDouble())
            val markerRadius = radius * saturation
            val x = centerX + (kotlin.math.cos(angle) * markerRadius).toFloat()
            val y = centerY + (kotlin.math.sin(angle) * markerRadius).toFloat()
            paint.style = Paint.Style.FILL
            paint.color = selectedColor
            canvas.drawCircle(x, y, dp(9).toFloat(), paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(4).toFloat()
            paint.color = Color.BLACK
            canvas.drawCircle(x, y, dp(10).toFloat(), paint)
            paint.strokeWidth = dp(2).toFloat()
            paint.color = Color.WHITE
            canvas.drawCircle(x, y, dp(10).toFloat(), paint)
            paint.style = Paint.Style.FILL
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - centerX
                    val dy = event.y - centerY
                    val distance = sqrt(dx * dx + dy * dy)
                    if (distance <= radius + dp(12)) {
                        hue = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 360f) % 360f
                        saturation = (distance / radius).coerceIn(0f, 1f)
                        updateSelectedColor()
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    performClick()
                    return true
                }
            }
            return true
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }
    }

    private fun addBackgroundOpacityControl(parent: LinearLayout) {
        val current = (((prefs.getInt("background_opacity", 100).coerceIn(0, 100) + 5) / 10) * 10)
        val valueLabel = TextView(this).apply {
            text = "Intensitate sticlă: $current%"
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
                    valueLabel.text = "Intensitate sticlă: $opacity%"
                    if (fromUser) {
                        prefs.edit().putInt("background_opacity", opacity).apply()
                        previewBackground?.visibility = View.GONE
                        previewOverlay?.alpha = (opacity / 100f) * 0.85f
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
                        previewViews[preferenceKey]?.textSize = value * previewScaleFor(preferenceKey)
                        if (preferenceKey == "size_hourly") {
                            previewViews.filterKeys { it.startsWith("size_hourly") }.values.forEach { it.textSize = value * previewTextScale }
                            previewViews["label_hourly"]?.textSize = (value + 1) * previewTextScale
                        }
                        if (preferenceKey == "size_daily") {
                            previewViews.filterKeys { it.startsWith("size_daily") }.values.forEach { it.textSize = value * previewTextScale }
                            previewViews["label_daily"]?.textSize = (value + 1) * previewTextScale
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
