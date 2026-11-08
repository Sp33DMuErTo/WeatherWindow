package ro.weatherwindow

import android.content.Context
import android.location.Geocoder
import java.util.Locale
import java.time.LocalDate
import java.time.format.TextStyle
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
    val sunset: String,
    val code: Int,
    val hourlyLeft: String,
    val hourlyRight: String,
    val hourlyColumns: List<String>,
    val hourlyFull: String,
    val hourlyForecast: List<HourlyForecast>,
    val dailyColumns: List<String>,
    val daily: String,
    val sunrise: String = "06:00",
    val timezone: String = "Europe/Bucharest",
    val precipitationProbability: Int = 0
)

data class HourlyForecast(
    val time: String,
    val temperature: Int,
    val precipitation: Int,
    val code: Int,
    val feelsLike: Int,
    val humidity: Int,
    val wind: Int,
    val windDirection: Int,
    val windGusts: Int,
    val cloudCover: Int,
    val precipitationMm: Double,
    val uvIndex: Double
)

data class CitySuggestion(
    val name: String,
    val label: String,
    val latitude: Double,
    val longitude: Double
)

object WeatherRepository {
    fun reverseGeocode(context: Context, lat: Double, lon: Double): String? {
        val androidName = if (Geocoder.isPresent()) {
            try {
                val geocoder = Geocoder(context, Locale("ro", "RO"))
                @Suppress("DEPRECATION")
                val address = geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()
                address?.locality ?: address?.subLocality ?: address?.subAdminArea ?: address?.adminArea
            } catch (_: Exception) { null }
        } else null
        if (!androidName.isNullOrBlank()) return androidName
        return try {
            val root = getJson("https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=$lat&lon=$lon&zoom=10&addressdetails=1")
            val address = root.optJSONObject("address") ?: return null
            listOf("city", "town", "village", "municipality", "hamlet", "county")
                .firstNotNullOfOrNull { key -> address.optString(key).takeIf { it.isNotBlank() && it != "null" } }
        } catch (_: Exception) { null }
    }

    private fun getJson(address: String): JSONObject {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000
            readTimeout = 12000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "WeatherWindow/1.2 (Android weather widget)")
            setRequestProperty("Accept-Language", "ro")
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

    fun searchCities(query: String): List<CitySuggestion> {
        val q = URLEncoder.encode(query, "UTF-8")
        val root = getJson("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=8&language=ro&format=json")
        val results = root.optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).mapNotNull { index ->
            val item = results.optJSONObject(index) ?: return@mapNotNull null
            val name = item.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val admin = item.optString("admin1").takeIf { it.isNotBlank() && it != "null" }
            val country = item.optString("country").takeIf { it.isNotBlank() && it != "null" }
            val label = listOfNotNull(name, admin, country).distinct().joinToString(", ")
            CitySuggestion(name, label, item.getDouble("latitude"), item.getDouble("longitude"))
        }
    }

    fun load(context: Context, lat: Double, lon: Double, cityOverride: String? = null): WeatherSnapshot {
        val address = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m&hourly=temperature_2m,apparent_temperature,relative_humidity_2m,precipitation_probability,precipitation,weather_code,cloud_cover,wind_speed_10m,wind_direction_10m,wind_gusts_10m,uv_index&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset&forecast_days=7&timezone=auto"
        val root = getJson(address)
        val current = root.getJSONObject("current")
        val hourly = root.getJSONObject("hourly")
        val daily = root.getJSONObject("daily")
        val sunrise = daily.getJSONArray("sunrise").optString(0).takeLast(5)
        val sunset = daily.getJSONArray("sunset").optString(0).takeLast(5)
        val timezone = root.optString("timezone", "Europe/Bucharest")
        val times = hourly.getJSONArray("time")
        val temps = hourly.getJSONArray("temperature_2m")
        val rain = hourly.getJSONArray("precipitation_probability")
        val hourlyCodes = hourly.getJSONArray("weather_code")
        val feelsLike = hourly.getJSONArray("apparent_temperature")
        val humidity = hourly.getJSONArray("relative_humidity_2m")
        val precipitationMm = hourly.getJSONArray("precipitation")
        val cloudCover = hourly.getJSONArray("cloud_cover")
        val wind = hourly.getJSONArray("wind_speed_10m")
        val windDirection = hourly.getJSONArray("wind_direction_10m")
        val windGusts = hourly.getJSONArray("wind_gusts_10m")
        val uvIndex = hourly.getJSONArray("uv_index")
        val now = current.optString("time")
        var start = 0
        for (i in 0 until times.length()) {
            if (times.optString(i) >= now) { start = i; break }
        }
        val hourParts = (start until minOf(start + 24, times.length())).map { i ->
            "${times.optString(i).takeLast(5)} ${symbol(hourlyCodes.optInt(i))} ${temps.optDouble(i).toInt()}°/${rain.optInt(i)}%"
        }
        val hourlyForecast = (start until minOf(start + 24, times.length())).map { i ->
            HourlyForecast(
                times.optString(i).takeLast(5), temps.optDouble(i).toInt(), rain.optInt(i), hourlyCodes.optInt(i),
                feelsLike.optDouble(i).toInt(), humidity.optInt(i), wind.optDouble(i).toInt(),
                windDirection.optInt(i), windGusts.optDouble(i).toInt(), cloudCover.optInt(i),
                precipitationMm.optDouble(i), uvIndex.optDouble(i)
            )
        }
        val hourlyColumns = (start until minOf(start + 6, times.length())).map { i ->
            "${times.optString(i).takeLast(5)}\n${temps.optDouble(i).toInt()}°\n${symbol(hourlyCodes.optInt(i))} ${rain.optInt(i)}%"
        }
        val dayParts = (1 until minOf(7, daily.getJSONArray("time").length())).map { i ->
            "${dayLabel(daily.getJSONArray("time").optString(i), i)}\n${daily.getJSONArray("temperature_2m_max").optDouble(i).toInt()}°/${daily.getJSONArray("temperature_2m_min").optDouble(i).toInt()}°\n${symbol(daily.getJSONArray("weather_code").optInt(i))} ${daily.getJSONArray("precipitation_probability_max").optInt(i)}%"
        }
        val city = cityOverride ?: context.getSharedPreferences("weather", Context.MODE_PRIVATE).getString("city", "Locație salvată") ?: "Locație salvată"
        return WeatherSnapshot(
            city,
            current.getDouble("temperature_2m").toInt(),
            current.getDouble("apparent_temperature").toInt(),
            current.getInt("relative_humidity_2m"),
            current.getDouble("wind_speed_10m").toInt(),
            sunset,
            current.getInt("weather_code"),
            hourParts.take(3).joinToString("\n"),
            hourParts.drop(3).take(3).joinToString("\n"),
            hourlyColumns,
            hourParts.joinToString("\n"),
            hourlyForecast,
            dayParts,
            dayParts.joinToString("\n"),
            sunrise,
            timezone,
            rain.optInt(start)
        )
    }

    fun backgroundIndex(code: Int): Int = when {
        code == 0 -> 0
        code == 1 || code == 2 -> 1
        code == 3 || code == 45 || code == 48 -> 2
        code in 51..67 || code in 80..82 -> 3
        code in 71..77 || code in 85..86 -> 4
        code in 95..99 -> 5
        else -> 2
    }

    fun symbol(code: Int): String = when (code) {
        0 -> "☀️"
        1, 2 -> "🌤️"
        3 -> "☁️"
        45, 48 -> "🌫️"
        51, 53, 55, 56, 57 -> "🌦️"
        61, 63, 65, 66, 67, 80, 81, 82 -> "🌧️"
        71, 73, 75, 77, 85, 86 -> "❄️"
        95, 96, 99 -> "⛈️"
        else -> "🌡️"
    }

    private fun dayLabel(date: String, index: Int): String {
        if (index == 0) return "Azi"
        return try {
            LocalDate.parse(date).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("ro", "RO"))
                .replace(".", "")
                .replaceFirstChar { it.uppercase(Locale("ro", "RO")) }
        } catch (_: Exception) {
            date.takeLast(5)
        }
    }

    fun windDirection(degrees: Int): String {
        val directions = arrayOf("N", "NE", "E", "SE", "S", "SV", "V", "NV")
        return directions[((degrees % 360 + 360 + 22) / 45) % 8]
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
