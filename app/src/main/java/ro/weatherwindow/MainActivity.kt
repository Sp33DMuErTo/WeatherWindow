package ro.weatherwindow

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.view.Gravity
import android.view.WindowInsets
import android.os.Build
import android.widget.Button
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var cityInput: AutoCompleteTextView
    private lateinit var cityAdapter: ArrayAdapter<String>
    private var citySuggestions: List<CitySuggestion> = emptyList()
    private var selectingSuggestion = false
    private lateinit var status: TextView
    private lateinit var forecastContainer: LinearLayout
    private lateinit var locationManager: LocationManager
    private val prefs by lazy { getSharedPreferences("weather", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 36, 28, 28)
            setBackgroundColor(Color.rgb(19, 35, 50))
        }
        val title = TextView(this).apply {
            text = "WeatherWindow"
            textSize = 28f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }
        val subtitle = TextView(this).apply {
            text = "Vremea ta, în timp real"
            textSize = 15f
            setTextColor(Color.LTGRAY)
        }
        cityAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, mutableListOf<String>())
        cityInput = AutoCompleteTextView(this).apply {
            hint = "Scrie primele litere ale orașului"
            threshold = 2
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            setAdapter(cityAdapter)
            setOnItemClickListener { _, _, position, _ ->
                val chosen = citySuggestions.getOrNull(position)
                if (chosen != null) {
                    selectingSuggestion = true
                    setText(chosen.label, false)
                    dismissDropDown()
                    selectingSuggestion = false
                    loadCoordinates(chosen.latitude, chosen.longitude, chosen.name, false)
                }
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (selectingSuggestion) return
                    val query = s?.toString()?.trim().orEmpty()
                    if (query.length < 2) {
                        citySuggestions = emptyList()
                        cityAdapter.clear()
                        return
                    }
                    thread {
                        try {
                            val results = WeatherRepository.searchCities(query)
                            runOnUiThread {
                                if (cityInput.text.toString().trim().equals(query, ignoreCase = true)) {
                                    citySuggestions = results
                                    cityAdapter.clear()
                                    cityAdapter.addAll(results.map { it.label })
                                    cityAdapter.notifyDataSetChanged()
                                    if (results.isNotEmpty() && cityInput.hasFocus()) cityInput.showDropDown()
                                }
                            }
                        } catch (_: Exception) {
                        }
                    }
                }
            })
        }
        val cityButton = Button(this).apply {
            text = "Caută / selectează orașul"
            setOnClickListener {
                if (cityInput.text.toString().trim().length < 2) {
                    status.text = "Scrie cel puțin două litere pentru a căuta un oraș."
                } else {
                    cityInput.showDropDown()
                    status.text = "Alege localitatea dorită din lista de sugestii."
                }
            }
        }
        val gpsButton = Button(this).apply {
            text = "Folosește locația GPS"
            setOnClickListener { useGps() }
        }
        val refreshButton = Button(this).apply {
            text = "Actualizează vremea"
            setOnClickListener { refreshSaved() }
        }
        status = TextView(this).apply {
            text = "Alege un oraș sau permite accesul la locație. Datele meteo sunt furnizate de Open-Meteo."
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(0, 22, 0, 0)
        }
        listOf(title, subtitle, cityInput, cityButton, gpsButton, refreshButton, status).forEach {
            root.addView(it, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 12
            })
        }
        forecastContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(forecastContainer, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = 18
        })

        val settingsButton = Button(this).apply {
            text = "⚙️ Setări widget"
            setOnClickListener {
                startActivity(android.content.Intent(this@MainActivity, SettingsActivity::class.java))
            }
        }
        root.addView(settingsButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = 12
        })

        val page = ScrollView(this).apply {
            isFillViewport = false
            clipToPadding = false
            addView(root)
        }
        page.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val safe = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
        setContentView(page)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 42)
        }
        if (prefs.contains("lat")) refreshSaved()
    }

    private fun loadCity(city: String) {
        if (city.isBlank()) {
            status.text = "Scrie mai întâi numele unui oraș."
            return
        }
        status.text = "Caut orașul și descarc prognoza…"
        thread {
            try {
                val coords = WeatherRepository.geocode(city)
                val cleanName = city.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                val snapshot = WeatherRepository.load(this, coords.first, coords.second, cleanName)
                prefs.edit().putString("city", cleanName).putFloat("lat", coords.first.toFloat())
                    .putFloat("lon", coords.second.toFloat()).putBoolean("is_gps", false).apply()
                runOnUiThread { showWeather(snapshot) }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Nu am putut încărca vremea: ${e.message ?: "verifică internetul"}" }
            }
        }
    }

    private fun useGps() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 41)
            return
        }
        status.text = "Obțin locația GPS…"
        try {
            val providers = locationManager.getProviders(true)
            var best: Location? = null
            for (provider in providers) {
                val candidate = locationManager.getLastKnownLocation(provider)
                if (candidate != null && (best == null || candidate.accuracy < best.accuracy)) best = candidate
            }
            if (best != null) {
                loadCoordinates(best.latitude, best.longitude, "Locația mea", true)
            } else {
                val provider = if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) LocationManager.NETWORK_PROVIDER else LocationManager.GPS_PROVIDER
                locationManager.requestLocationUpdates(provider, 1000L, 0f, object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        locationManager.removeUpdates(this)
                        loadCoordinates(location.latitude, location.longitude, "Locația mea", true)
                    }
                }, Looper.getMainLooper())
                status.text = "Aștept poziția GPS. Activează serviciile de localizare și încearcă din nou dacă durează."
            }
        } catch (e: SecurityException) {
            status.text = "Permisiunea de locație nu este disponibilă."
        } catch (e: Exception) {
            status.text = "Nu pot obține locația. Verifică GPS-ul."
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 41 && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) useGps()
        else if (requestCode == 41) status.text = "Ai refuzat accesul la locație. Poți alege manual orașul."
    }

    private fun loadCoordinates(lat: Double, lon: Double, label: String, isGps: Boolean = prefs.getBoolean("is_gps", false)) {
        status.text = "Încarc prognoza pentru poziția ta…"
        thread {
            try {
                val cityName = if (isGps) WeatherRepository.reverseGeocode(this, lat, lon) ?: label else label
                val snapshot = WeatherRepository.load(this, lat, lon, cityName)
                prefs.edit().putString("city", cityName).putFloat("lat", lat.toFloat())
                    .putFloat("lon", lon.toFloat()).putBoolean("is_gps", isGps).apply()
                runOnUiThread { showWeather(snapshot) }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Eroare la preluarea vremii: ${e.message ?: "conexiune indisponibilă"}" }
            }
        }
    }

    private fun refreshSaved() {
        if (!prefs.contains("lat")) {
            status.text = "Alege un oraș sau folosește locația GPS."
            return
        }
        loadCoordinates(prefs.getFloat("lat", 0f).toDouble(), prefs.getFloat("lon", 0f).toDouble(),
            prefs.getString("city", "Locația mea") ?: "Locația mea")
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun label(text: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

    private fun roundedBackground(color: Int, strokeColor: Int = Color.TRANSPARENT): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(18).toFloat()
            if (strokeColor != Color.TRANSPARENT) setStroke(dp(1), strokeColor)
        }

    private fun showWeather(w: WeatherSnapshot) {
        status.text = "Prognoză actualizată • Date furnizate de Open-Meteo"
        forecastContainer.removeAllViews()

        val currentCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = roundedBackground(Color.rgb(31, 57, 78), Color.rgb(66, 103, 128))
        }
        currentCard.addView(label(w.city, 17f, Color.rgb(203, 221, 235), true))
        currentCard.addView(label("${w.temperature}°", 48f, Color.WHITE, true))
        currentCard.addView(label("${WeatherRepository.symbol(w.code)}  ${WeatherRepository.condition(w.code)}", 19f, Color.WHITE, true))
        currentCard.addView(label("Se simte ca ${w.feelsLike}°     •     Vânt ${w.wind} km/h     •     Apus ${w.sunset}", 13f, Color.rgb(204, 218, 229)))
        forecastContainer.addView(currentCard, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(22)
        })

        val hourlyTitle = TextView(this).apply {
            text = "Următoarele 24 de ore"
            textSize = 21f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }
        forecastContainer.addView(hourlyTitle)
        forecastContainer.addView(TextView(this).apply {
            text = "Lista completă pentru următoarele 24 de ore"
            textSize = 12f
            setTextColor(Color.rgb(160, 184, 202))
            setPadding(0, dp(3), 0, dp(10))
        })

        val hourlyList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        w.hourlyForecast.forEachIndexed { index, hour ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(10), dp(14), dp(10))
                background = if (index == 0) roundedBackground(Color.rgb(48, 91, 119), Color.rgb(113, 185, 220))
                    else roundedBackground(Color.rgb(27, 48, 66), Color.rgb(49, 72, 91))
            }
            row.addView(label(hour.time, 15f, Color.WHITE, true), LinearLayout.LayoutParams(dp(58), LinearLayout.LayoutParams.WRAP_CONTENT))
            row.addView(label(WeatherRepository.symbol(hour.code), 27f, Color.WHITE), LinearLayout.LayoutParams(dp(52), LinearLayout.LayoutParams.WRAP_CONTENT))
            row.addView(label("${hour.temperature}°", 21f, Color.WHITE, true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(label("💧 ${hour.precipitation}%", 14f, Color.rgb(133, 205, 242), true))
            hourlyList.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(58)).apply {
                bottomMargin = dp(7)
            })
        }
        forecastContainer.addView(hourlyList, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(22)
        })

        forecastContainer.addView(TextView(this).apply {
            text = "Temperatura resimțită, umiditatea și vântul sunt măsurate pentru condițiile actuale."
            textSize = 12f
            setTextColor(Color.rgb(160, 184, 202))
        })
        WeatherWidgetProvider.updateAll(this, w)
        try {
            WeatherAnimationService.start(this)
        } catch (_: Exception) {
            status.text = "Prognoză actualizată • Animația nu a pornit; widgetul rămâne funcțional."
        }
    }
}
