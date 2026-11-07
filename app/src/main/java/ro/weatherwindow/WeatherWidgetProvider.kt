package ro.weatherwindow

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews

class WeatherWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.weather_widget)
            views.setTextViewText(R.id.location, "WeatherWindow • România")
            views.setTextViewText(R.id.temperature, "--°")
            views.setTextViewText(R.id.condition, "Widget instalat • prognoza urmează")
            views.setTextViewText(R.id.details, "Se simte: --°   Umiditate: --%")
            manager.updateAppWidget(id, views)
        }
    }
}
