package ro.weatherwindow

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class WeatherSnapshot(
    val city: String,
    val temperature: Int,
    val feelsLike: Int,
    val humidity: Int,
    val wind: Int,
    val code: Int,
    val hourly: String,
    val daily: String
)

object WeatherRepository {
    private fun getJson(address: String): JSONObject {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000
            readTimeout = 12000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "WeatherWindow/1.0")
        }
        return try {
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    fun geocode(city: String): Pair<Double, Double> {
        val q = URLEncoder.encode(city, "UTF-8")
        val root = getJson("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=1&language=ro&format=json")
        val results = root.optJSONArray("results") ?: throw Exception("Nu am găsit orașul. Verifică denumirea.")
        if (results.length() == 0) throw Exception("Nu am găsit orașul. Verifică denumirea.")
        val item = results.getJSONObject(0)
        return Pair(item.getDouble("latitude"), item.getDouble("longitude"))
    }

    fun load(context: Context, lat: Double, lon: Double, cityOverride: String? = null): WeatherSnapshot {
        val address = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m&hourly=temperature_2m,precipitation_probability,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min&forecast_days=7&timezone=auto"
        val root = getJson(address)
        val current = root.getJSONObject("current")
        val hourly = root.getJSONObject("hourly")
        val daily = root.getJSONObject("daily")
        val times = hourly.getJSONArray("time")
        val temps = hourly.getJSONArray("temperature_2m")
        val rain = hourly.getJSONArray("precipitation_probability")
        val now = current.optString("time")
        var start = 0
        for (i in 0 until times.length()) {
            if (times.optString(i) >= now) { start = i; break }
        }
        val hourParts = (start until minOf(start + 24, times.length())).map { i ->
            "${times.optString(i).takeLast(5)} ${temps.optDouble(i).toInt()}° ${rain.optInt(i)}%"
        }
        val dayParts = (0 until minOf(7, daily.getJSONArray("time").length())).map { i ->
            "${daily.getJSONArray("time").optString(i).takeLast(5)}  ${daily.getJSONArray("temperature_2m_max").optDouble(i).toInt()}°/${daily.getJSONArray("temperature_2m_min").optDouble(i).toInt()}°"
        }
        val city = cityOverride ?: context.getSharedPreferences("weather", Context.MODE_PRIVATE).getString("city", "Locație salvată") ?: "Locație salvată"
        return WeatherSnapshot(
            city,
            current.getDouble("temperature_2m").toInt(),
            current.getDouble("apparent_temperature").toInt(),
            current.getInt("relative_humidity_2m"),
            current.getDouble("wind_speed_10m").toInt(),
            current.getInt("weather_code"),
            hourParts.chunked(6).joinToString("\n") { row -> row.joinToString("   •   ") },
            dayParts.joinToString("\n")
        )
    }

    fun condition(code: Int): String = when (code) {
        0 -> "Cer senin"
        1, 2 -> "Parțial noros"
        3 -> "Noros"
        45, 48 -> "Ceață"
        51, 53, 55, 56, 57 -> "Burniță"
        61, 63, 65, 66, 67 -> "Ploaie"
        71, 73, 75, 77 -> "Ninsoare"
        80, 81, 82 -> "Averse"
        85, 86 -> "Averse de ninsoare"
        95, 96, 99 -> "Furtună"
        else -> "Vreme variabilă"
    }
}
