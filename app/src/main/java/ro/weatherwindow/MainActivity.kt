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
import android.widget.Button
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.ScrollView
import android.widget.TextView
import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var cityInput: AutoCompleteTextView
    private lateinit var cityAdapter: ArrayAdapter<String>
    private var citySuggestions: List<CitySuggestion> = emptyList()
    private var selectingSuggestion = false
    private lateinit var status: TextView
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
                    loadCoordinates(chosen.latitude, chosen.longitude, chosen.name)
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
                            // Sugestiile pot lipsi temporar dacă nu există conexiune.
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

        val settingsTitle = TextView(this).apply {
            text = "PERSONALIZARE WIDGET"
            textSize = 19f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 28, 0, 10)
        }
        root.addView(settingsTitle)
        addTextSizeControl(root, "Locație", "size_location", 10, 22, 14)
        addTextSizeControl(root, "Temperatură", "size_temperature", 24, 52, 34)
        addTextSizeControl(root, "Condiții meteo", "size_condition", 9, 18, 12)
        addTextSizeControl(root, "Detalii (resimțită, umiditate, vânt)", "size_details", 8, 16, 9)
        addTextSizeControl(root, "Prognoză orară", "size_hourly", 7, 14, 8)
        addTextSizeControl(root, "Prognoză 7 zile", "size_daily", 7, 14, 8)

        val resetButton = Button(this).apply {
            text = "Resetează dimensiunile textului"
            setOnClickListener {
                prefs.edit()
                    .putInt("size_location", 14)
                    .putInt("size_temperature", 34)
                    .putInt("size_condition", 12)
                    .putInt("size_details", 9)
                    .putInt("size_hourly", 8)
                    .putInt("size_daily", 8)
                    .apply()
                WeatherWidgetProvider.updateTextSizes(this@MainActivity)
                recreate()
            }
        }
        root.addView(resetButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = 10
        })

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        }
        setContentView(scroll)
        if (prefs.contains("lat")) refreshSaved()
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
            setPadding(0, 8, 0, 0)
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
                        WeatherWidgetProvider.updateTextSizes(this@MainActivity)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        parent.addView(valueLabel)
        parent.addView(seekBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = 6
        })
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
                    .putFloat("lon", coords.second.toFloat()).apply()
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
                loadCoordinates(best.latitude, best.longitude, "Locația mea")
            } else {
                val provider = if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) LocationManager.NETWORK_PROVIDER else LocationManager.GPS_PROVIDER
                locationManager.requestLocationUpdates(provider, 1000L, 0f, object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        locationManager.removeUpdates(this)
                        loadCoordinates(location.latitude, location.longitude, "Locația mea")
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

    private fun loadCoordinates(lat: Double, lon: Double, label: String) {
        status.text = "Încarc prognoza pentru poziția ta…"
        thread {
            try {
                val cityName = WeatherRepository.reverseGeocode(this, lat, lon) ?: label
                val snapshot = WeatherRepository.load(this, lat, lon, cityName)
                prefs.edit().putString("city", cityName).putFloat("lat", lat.toFloat())
                    .putFloat("lon", lon.toFloat()).apply()
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

    private fun showWeather(w: WeatherSnapshot) {
        status.text = "${w.city}\n${w.temperature}°  •  ${WeatherRepository.symbol(w.code)}  ${WeatherRepository.condition(w.code)}\nSe simte ca ${w.feelsLike}°  |  Umiditate ${w.humidity}%  |  Vânt ${w.wind} km/h\n\nUrmătoarele ore\n${w.hourly}\n\nUrmătoarele 7 zile\n${w.daily}\n\nWidgetul de pe ecranul principal a fost actualizat."
        WeatherWidgetProvider.updateAll(this, w)
    }
}
