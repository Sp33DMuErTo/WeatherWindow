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
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Color
import android.graphics.Typeface
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var cityInput: EditText
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
        cityInput = EditText(this).apply {
            hint = "Scrie orașul (ex. Ploiești)"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
        }
        val cityButton = Button(this).apply {
            text = "Alege orașul"
            setOnClickListener { loadCity(cityInput.text.toString().trim()) }
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
        setContentView(root)
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
                val snapshot = WeatherRepository.load(this, lat, lon, label)
                prefs.edit().putString("city", label).putFloat("lat", lat.toFloat())
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
        status.text = "${w.city}\n${w.temperature}°  •  ${WeatherRepository.condition(w.code)}\nSe simte ca ${w.feelsLike}°  |  Umiditate ${w.humidity}%  |  Vânt ${w.wind} km/h\n\nUrmătoarele ore\n${w.hourly}\n\nUrmătoarele 7 zile\n${w.daily}\n\nWidgetul de pe ecranul principal a fost actualizat."
        WeatherWidgetProvider.updateAll(this, w)
    }
}
