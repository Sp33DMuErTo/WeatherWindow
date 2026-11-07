package ro.weatherwindow

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class WeatherWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val prefs = context.getSharedPreferences("weather", Context.MODE_PRIVATE)
        if (prefs.contains("lat")) {
            val lat = prefs.getFloat("lat", 0f).toDouble()
            val lon = prefs.getFloat("lon", 0f).toDouble()
            val city = prefs.getString("city", "Locația mea") ?: "Locația mea"
            val pendingResult = goAsync()
            kotlin.concurrent.thread {
                try {
                    val weather = WeatherRepository.load(context, lat, lon, city)
                    updateAll(context, weather)
                } catch (_: Exception) {
                    updatePlaceholders(context, ids)
                } finally {
                    pendingResult.finish()
                }
            }
        } else updatePlaceholders(context, ids)
    }

    companion object {
        fun updateAll(context: Context, weather: WeatherSnapshot) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WeatherWidgetProvider::class.java))
            ids.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.weather_widget)
                views.setTextViewText(R.id.location, weather.city)
                views.setTextViewText(R.id.temperature, "${weather.temperature}°")
                views.setTextViewText(R.id.condition, "${WeatherRepository.symbol(weather.code)}  ${WeatherRepository.condition(weather.code)}")
                views.setTextViewText(R.id.details, "Se simte ${weather.feelsLike}°  •  Umiditate ${weather.humidity}%  •  Vânt ${weather.wind} km/h")
                views.setTextViewText(R.id.hourly, weather.hourly)
                views.setTextViewText(R.id.daily, weather.daily)
                val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                views.setOnClickPendingIntent(R.id.widget_root, open)
                manager.updateAppWidget(id, views)
            }
        }

        private fun updatePlaceholders(context: Context, ids: IntArray) {
            ids.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.weather_widget)
                views.setTextViewText(R.id.location, "WeatherWindow")
                views.setTextViewText(R.id.temperature, "--°")
                views.setTextViewText(R.id.condition, "Deschide aplicația și alege orașul sau GPS")
                views.setTextViewText(R.id.details, "Prognoză meteo Open-Meteo")
                views.setTextViewText(R.id.hourly, "Prognoza orară va apărea aici")
                views.setTextViewText(R.id.daily, "Prognoza pe 7 zile va apărea aici")
                val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                views.setOnClickPendingIntent(R.id.widget_root, open)
                AppWidgetManager.getInstance(context).updateAppWidget(id, views)
            }
        }
    }
}