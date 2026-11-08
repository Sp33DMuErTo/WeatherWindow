package ro.weatherwindow

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.graphics.Bitmap
import android.graphics.Color
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
        WeatherUpdateWorker.schedule(context)
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
                    // Keep the last successful forecast visible during temporary network/API failures.
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
                val options = manager.getAppWidgetOptions(id)
                val views = RemoteViews(context.packageName, widgetLayout(options))
                applyTextSizes(context, views, options)
                manager.partiallyUpdateAppWidget(id, views)
            }
        }
        fun updateTextColor(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WeatherWidgetProvider::class.java))
            ids.forEach { id ->
                val options = manager.getAppWidgetOptions(id)
                val views = RemoteViews(context.packageName, widgetLayout(options))
                applyTextColor(context, views)
                manager.partiallyUpdateAppWidget(id, views)
            }
        }

        fun updateBackgroundOpacity(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WeatherWidgetProvider::class.java))
            ids.forEach { id ->
                val views = RemoteViews(context.packageName, widgetLayout(manager.getAppWidgetOptions(id)))
                applyBackgroundOpacity(context, views)
                manager.partiallyUpdateAppWidget(id, views)
            }
        }

        private fun widgetLayout(options: Bundle?): Int {
            val height = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 280) ?: 280
            return if (height < 250) R.layout.weather_widget_compact else R.layout.weather_widget
        }

        private fun applyBackgroundOpacity(context: Context, views: RemoteViews) {
            val opacity = context.getSharedPreferences("weather", Context.MODE_PRIVATE)
                .getInt("background_opacity", 100).coerceIn(0, 100)
            val glassAlpha = (opacity / 100f * 0.85f * 255).toInt()
            views.setViewVisibility(R.id.weather_background, android.view.View.GONE)
            views.setInt(R.id.weather_overlay, "setImageAlpha", glassAlpha)
        }

        fun updateAll(context: Context, weather: WeatherSnapshot) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WeatherWidgetProvider::class.java))
            ids.forEach { id ->
                val options = manager.getAppWidgetOptions(id)
                val views = RemoteViews(context.packageName, widgetLayout(options))
                views.setTextViewText(R.id.location, weather.city)
                views.setTextViewText(R.id.temperature, "${weather.temperature}°")
                views.setTextViewText(R.id.condition, WeatherRepository.condition(weather.code))
                views.setTextViewText(R.id.precipitation_probability, "${weather.precipitationProbability}%")
                val gpsSelected = context.getSharedPreferences("weather", Context.MODE_PRIVATE).getBoolean("is_gps", false)
                views.setViewVisibility(R.id.gps_indicator, if (gpsSelected) android.view.View.VISIBLE else android.view.View.GONE)
                views.setImageViewBitmap(R.id.weather_animation, WeatherAnimationFrames.render(weather.code, 0))
                context.getSharedPreferences("weather", Context.MODE_PRIVATE).edit()
                    .putInt("weather_code", weather.code)
                    .putString("sunrise", weather.sunrise)
                    .putString("sunset", weather.sunset)
                    .putString("timezone", weather.timezone)
                    .apply()
                views.setTextViewText(R.id.details, "Resimțită: ${weather.feelsLike}°  •  Vânt ${weather.wind} km/h  •  Răsărit ${weather.sunrise}  •  Apus ${weather.sunset}")
                val hourlyIds = intArrayOf(R.id.hourly_1, R.id.hourly_2, R.id.hourly_3, R.id.hourly_4, R.id.hourly_5, R.id.hourly_6)
                hourlyIds.forEachIndexed { index, id ->
                    views.setTextViewText(id, weather.hourlyColumns.getOrElse(index) { "--:--\n--°\n— --%" })
                }
                val dailyIds = intArrayOf(R.id.daily_1, R.id.daily_2, R.id.daily_3, R.id.daily_4, R.id.daily_5, R.id.daily_6)
                dailyIds.forEachIndexed { index, id ->
                    views.setTextViewText(id, weather.dailyColumns.getOrElse(index) { "--\n--°/--°\n— --%" })
                }
                applyTextSizes(context, views, options)
                applyTextColor(context, views)
                applyBackgroundOpacity(context, views)
                val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                views.setOnClickPendingIntent(R.id.widget_root, open)
                manager.updateAppWidget(id, views)
            }
        }

        private fun applyTextColor(context: Context, views: RemoteViews) {
            val prefs = context.getSharedPreferences("weather", Context.MODE_PRIVATE)
            val enabled = prefs.getBoolean("text_color_enabled", false)
            val selected = prefs.getInt("text_color", Color.WHITE)
            val defaults = mapOf(
                R.id.location to Color.rgb(247, 250, 252),
                R.id.temperature to Color.WHITE,
                R.id.condition to Color.rgb(220, 234, 244),
                R.id.precipitation_probability to Color.rgb(133, 205, 242),
                R.id.details to Color.rgb(194, 212, 226),
                R.id.hourly_label to Color.rgb(175, 200, 218),
                R.id.daily_label to Color.rgb(175, 200, 218),
                R.id.hourly_1 to Color.rgb(240, 245, 248),
                R.id.hourly_2 to Color.rgb(240, 245, 248),
                R.id.hourly_3 to Color.rgb(240, 245, 248),
                R.id.hourly_4 to Color.rgb(240, 245, 248),
                R.id.hourly_5 to Color.rgb(240, 245, 248),
                R.id.hourly_6 to Color.rgb(240, 245, 248),
                R.id.daily_1 to Color.rgb(240, 245, 248),
                R.id.daily_2 to Color.rgb(240, 245, 248),
                R.id.daily_3 to Color.rgb(240, 245, 248),
                R.id.daily_4 to Color.rgb(240, 245, 248),
                R.id.daily_5 to Color.rgb(240, 245, 248),
                R.id.daily_6 to Color.rgb(240, 245, 248)
            )
            defaults.forEach { (id, defaultColor) ->
                views.setInt(id, "setTextColor", if (enabled) selected else defaultColor)
            }
        }

        private fun applyTextSizes(context: Context, views: RemoteViews, options: Bundle? = null) {
            val prefs = context.getSharedPreferences("weather", Context.MODE_PRIVATE)
            // A four-row widget is the design reference; shorter widgets scale all content.
            // MIN_HEIGHT is the host-reported lower bound for the widget's current
            // placement. MAX_HEIGHT can remain at the previous larger size on some launchers.
            val availableHeight = options?.getInt(
                AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,
                280
            ) ?: 280
            val compact = availableHeight < 250
            // Keep the compact 5x3 typography exactly as tuned. When the widget
            // expands to 5x4, enlarge every text element automatically.
            val fontScale = if (compact) 1.0f else 1.15f
            val contentPadding = if (compact) 5 else 11
            views.setViewPadding(R.id.widget_content, contentPadding, contentPadding, contentPadding, contentPadding)
            views.setTextViewTextSize(R.id.location, TypedValue.COMPLEX_UNIT_SP, prefs.getInt("shared_size_location", prefs.getInt("size_location_5x4", 22)) * fontScale)
            views.setTextViewTextSize(R.id.gps_indicator, TypedValue.COMPLEX_UNIT_SP, 16f * fontScale)
            views.setTextViewTextSize(R.id.temperature, TypedValue.COMPLEX_UNIT_SP, prefs.getInt("shared_size_temperature", prefs.getInt("size_temperature_5x4", 53)) * fontScale)
            views.setTextViewTextSize(R.id.condition, TypedValue.COMPLEX_UNIT_SP, prefs.getInt("shared_size_condition", prefs.getInt("size_condition_5x4", 18)) * fontScale)
            views.setTextViewTextSize(R.id.precipitation_probability, TypedValue.COMPLEX_UNIT_SP, 11f * fontScale)
            views.setTextViewTextSize(R.id.details, TypedValue.COMPLEX_UNIT_SP, prefs.getInt("shared_size_details", prefs.getInt("size_details_5x4", 16)) * fontScale)
            val hourlySize = prefs.getInt("shared_size_hourly", prefs.getInt("size_hourly_5x4", 12)).coerceIn(8, 16) * fontScale
            intArrayOf(R.id.hourly_1, R.id.hourly_2, R.id.hourly_3, R.id.hourly_4, R.id.hourly_5, R.id.hourly_6).forEach { id ->
                views.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, hourlySize)
            }
            views.setTextViewTextSize(R.id.hourly_label, TypedValue.COMPLEX_UNIT_SP, hourlySize + fontScale)
            val dailySize = prefs.getInt("shared_size_daily", prefs.getInt("size_daily_5x4", 12)).coerceIn(8, 16) * fontScale
            intArrayOf(R.id.daily_1, R.id.daily_2, R.id.daily_3, R.id.daily_4, R.id.daily_5, R.id.daily_6).forEach { id ->
                views.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, dailySize)
            }
            views.setTextViewTextSize(R.id.daily_label, TypedValue.COMPLEX_UNIT_SP, dailySize + fontScale)
        }

        private fun weatherBackground(
            context: Context,
            code: Int,
            frame: Int = 0,
            sunrise: String = "06:00",
            sunset: String = "18:00",
            timezone: String = "Europe/Bucharest"
        ): Bitmap? = try {
            SceneBackground.render(code, frame, sunrise, sunset, timezone)
        } catch (_: Exception) { null }

        private fun updatePlaceholders(context: Context, ids: IntArray) {
            ids.forEach { id ->
                val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(id)
                val views = RemoteViews(context.packageName, widgetLayout(options))
                views.setTextViewText(R.id.location, "WeatherWindow")
                views.setViewVisibility(R.id.gps_indicator, android.view.View.GONE)
                views.setTextViewText(R.id.temperature, "--°")
                views.setTextViewText(R.id.condition, "Așteaptă prognoza meteo")
                views.setTextViewText(R.id.precipitation_probability, "--%")
                views.setImageViewBitmap(R.id.weather_animation, WeatherAnimationFrames.render(3, 0))
                views.setTextViewText(R.id.details, "Prognoză meteo Open-Meteo")
                intArrayOf(R.id.hourly_1, R.id.hourly_2, R.id.hourly_3, R.id.hourly_4, R.id.hourly_5, R.id.hourly_6).forEach { id ->
                    views.setTextViewText(id, "--:--\n--°\n— --%")
                }
                intArrayOf(R.id.daily_1, R.id.daily_2, R.id.daily_3, R.id.daily_4, R.id.daily_5, R.id.daily_6).forEach { id ->
                    views.setTextViewText(id, "--\n--°/--°\n— --%")
                }
                applyTextSizes(context, views, options)
                applyTextColor(context, views)
                applyBackgroundOpacity(context, views)
                val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                views.setOnClickPendingIntent(R.id.widget_root, open)
                AppWidgetManager.getInstance(context).updateAppWidget(id, views)
            }
        }
    }
}
