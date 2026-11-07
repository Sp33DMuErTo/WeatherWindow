package ro.weatherwindow

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val message = TextView(this).apply {
            text = "WeatherWindow\n\nWidgetul meteo este instalat în aplicație.\nAdaugă widgetul pe ecranul principal."
            textSize = 20f
            setPadding(32, 48, 32, 32)
        }
        setContentView(message)
    }
}
