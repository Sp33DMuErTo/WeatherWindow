package ro.weatherwindow

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.TypedValue
import android.widget.RemoteViews

class WeatherWidgetProvider : AppWidgetProvider() {
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        super.onAppWidgetOptionsChanged(context, manager, appWidgetId, newOptions)
        updateTextSizes(context)
    }

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
        private val backgroundCache = mutableMapOf<Int, Bitmap>()

        fun updateTextSizes(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WeatherWidgetProvider::class.java))
            ids.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.weather_widget)
                applyTextSizes(context, views, manager.getAppWidgetOptions(id))
                manager.partiallyUpdateAppWidget(id, views)
            }
        }
        fun updateBackgroundOpacity(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WeatherWidgetProvider::class.java))
            ids.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.weather_widget)
                applyBackgroundOpacity(context, views)
                manager.partiallyUpdateAppWidget(id, views)
            }
        }

        private fun applyBackgroundOpacity(context: Context, views: RemoteViews) {
            val opacity = context.getSharedPreferences("weather", Context.MODE_PRIVATE)
                .getInt("background_opacity", 100).coerceIn(0, 100)
            val alpha = (opacity * 255 / 100f).toInt()
            views.setInt(R.id.weather_background, "setImageAlpha", alpha)
            views.setInt(R.id.weather_overlay, "setImageAlpha", alpha)
        }

        fun updateAll(context: Context, weather: WeatherSnapshot) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WeatherWidgetProvider::class.java))
            ids.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.weather_widget)
                views.setTextViewText(R.id.location, weather.city)
                views.setTextViewText(R.id.temperature, "${weather.temperature}°")
                views.setTextViewText(R.id.condition, "${WeatherRepository.symbol(weather.code)}  ${WeatherRepository.condition(weather.code)}")
                views.setTextViewText(R.id.details, "Se simte ${weather.feelsLike}°  •  Umiditate ${weather.humidity}%  •  Vânt ${weather.wind} km/h")
                val hourlyIds = intArrayOf(R.id.hourly_1, R.id.hourly_2, R.id.hourly_3, R.id.hourly_4, R.id.hourly_5, R.id.hourly_6)
                hourlyIds.forEachIndexed { index, id ->
                    views.setTextViewText(id, weather.hourlyColumns.getOrElse(index) { "--:--\n—\n--°\n--%" })
                }
                val dailyIds = intArrayOf(R.id.daily_1, R.id.daily_2, R.id.daily_3, R.id.daily_4, R.id.daily_5, R.id.daily_6)
                dailyIds.forEachIndexed { index, id ->
                    views.setTextViewText(id, weather.dailyColumns.getOrElse(index) { "--\n--°/--°\n—\n--%" })
                }
                applyTextSizes(context, views, manager.getAppWidgetOptions(id))
                weatherBackground(context, weather.code)?.let {
                    views.setImageViewBitmap(R.id.weather_background, it)
                }
                applyBackgroundOpacity(context, views)
                val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                views.setOnClickPendingIntent(R.id.widget_root, open)
                manager.updateAppWidget(id, views)
            }
        }

        private fun applyTextSizes(context: Context, views: RemoteViews, options: Bundle? = null) {
            val prefs = context.getSharedPreferences("weather", Context.MODE_PRIVATE)
            // A four-row widget is the design reference; shorter widgets scale all content.
            val availableHeight = options?.getInt(
                AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 280)
            ) ?: 280
            val scale = (availableHeight / 280f).coerceIn(0.72f, 1f)
            val contentPadding = (11 * scale).toInt()
            views.setViewPadding(R.id.widget_content, contentPadding, contentPadding, contentPadding, contentPadding)
            fun scaled(value: Int) = (value * scale).coerceAtLeast(7f)
            views.setTextViewTextSize(R.id.location, TypedValue.COMPLEX_UNIT_SP, scaled(prefs.getInt("size_location", 22)))
            views.setTextViewTextSize(R.id.temperature, TypedValue.COMPLEX_UNIT_SP, scaled(prefs.getInt("size_temperature", 53)))
            views.setTextViewTextSize(R.id.condition, TypedValue.COMPLEX_UNIT_SP, scaled(prefs.getInt("size_condition", 18)))
            views.setTextViewTextSize(R.id.details, TypedValue.COMPLEX_UNIT_SP, scaled(prefs.getInt("size_details", 16)))
            val hourlySize = scaled(prefs.getInt("size_hourly", 12).coerceIn(8, 16))
            intArrayOf(R.id.hourly_1, R.id.hourly_2, R.id.hourly_3, R.id.hourly_4, R.id.hourly_5, R.id.hourly_6).forEach { id ->
                views.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, hourlySize)
            }
            views.setTextViewTextSize(R.id.hourly_label, TypedValue.COMPLEX_UNIT_SP, hourlySize + scale)
            intArrayOf(R.id.daily_1, R.id.daily_2, R.id.daily_3, R.id.daily_4, R.id.daily_5, R.id.daily_6).forEach { id ->
                views.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, scaled(prefs.getInt("size_daily", 12).coerceIn(8, 16)))
            }
            views.setTextViewTextSize(R.id.daily_label, TypedValue.COMPLEX_UNIT_SP, scaled(prefs.getInt("size_daily", 12).coerceIn(8, 16)) + scale)
        }

        private fun weatherBackground(context: Context, code: Int): Bitmap? {
            val index = WeatherRepository.backgroundIndex(code)
            synchronized(backgroundCache) { backgroundCache[index]?.let { return it } }
            return try {
                val encoded = context.assets.open("weather_sprites.webp.b64").bufferedReader().use { it.readText() }
                val bytes = Base64.decode(encoded, Base64.DEFAULT)
                val sprite = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
                val cellWidth = sprite.width / 3
                val cellHeight = sprite.height / 2
                val bitmap = Bitmap.createBitmap(sprite, (index % 3) * cellWidth, (index / 3) * cellHeight, cellWidth, cellHeight)
                sprite.recycle()
                synchronized(backgroundCache) { backgroundCache[index] = bitmap }
                bitmap
            } catch (_: Exception) { null }
        }

        private fun updatePlaceholders(context: Context, ids: IntArray) {
            ids.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.weather_widget)
                views.setTextViewText(R.id.location, "WeatherWindow")
                views.setTextViewText(R.id.temperature, "--°")
                views.setTextViewText(R.id.condition, "Deschide aplicația și alege orașul sau GPS")
                views.setTextViewText(R.id.details, "Prognoză meteo Open-Meteo")
                intArrayOf(R.id.hourly_1, R.id.hourly_2, R.id.hourly_3, R.id.hourly_4, R.id.hourly_5, R.id.hourly_6).forEach { id ->
                    views.setTextViewText(id, "--:--\n—\n--°\n--%")
                }
                intArrayOf(R.id.daily_1, R.id.daily_2, R.id.daily_3, R.id.daily_4, R.id.daily_5, R.id.daily_6).forEach { id ->
                    views.setTextViewText(id, "--\n--°/--°\n—\n--%")
                }
                applyTextSizes(context, views, manager.getAppWidgetOptions(id))
                weatherBackground(context, 3)?.let { views.setImageViewBitmap(R.id.weather_background, it) }
                applyBackgroundOpacity(context, views)
                val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                views.setOnClickPendingIntent(R.id.widget_root, open)
                AppWidgetManager.getInstance(context).updateAppWidget(id, views)
            }
        }
    }
}
