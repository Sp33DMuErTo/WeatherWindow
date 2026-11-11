package ro.weatherwindow

import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class WeatherUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {

    override fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("weather", Context.MODE_PRIVATE)
        if (!prefs.contains("lat")) return Result.success()

        return try {
            var lat = prefs.getFloat("lat", 0f).toDouble()
            var lon = prefs.getFloat("lon", 0f).toDouble()
            var city = prefs.getString("city", "Locația mea") ?: "Locația mea"

            // When GPS mode is selected, refresh the saved coordinates from the
            // freshest recent system location before requesting the weather.
            val gpsMode = prefs.getBoolean("is_gps", false)
            val backgroundLocationGranted = android.os.Build.VERSION.SDK_INT < 29 ||
                applicationContext.checkSelfPermission(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (gpsMode && backgroundLocationGranted &&
                (applicationContext.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                 applicationContext.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)) {
                try {
                    val locationManager = applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                    val now = System.currentTimeMillis()
                    val newest = locationManager.getProviders(true)
                        .mapNotNull { provider -> locationManager.getLastKnownLocation(provider) }
                        .filter { now - it.time in 0..TimeUnit.HOURS.toMillis(2) }
                        .maxWithOrNull(compareBy<Location> { it.time }.thenBy { -it.accuracy })
                    if (newest != null) {
                        val previous = Location("saved").apply { latitude = lat; longitude = lon }
                        val movedMeters = previous.distanceTo(newest)
                        if (movedMeters >= 750f) {
                            lat = newest.latitude
                            lon = newest.longitude
                            city = WeatherRepository.reverseGeocode(applicationContext, lat, lon) ?: city
                            prefs.edit().putFloat("lat", lat.toFloat()).putFloat("lon", lon.toFloat())
                                .putString("city", city).putLong("last_gps_fix", newest.time).apply()
                        }
                    }
                } catch (_: SecurityException) {
                    // Keep the last saved position if Android does not allow background access.
                } catch (_: Exception) {
                    // A missing location fix must not prevent the weather refresh.
                }
            }

            val weather = WeatherRepository.load(applicationContext, lat, lon, city)
            WeatherWidgetProvider.updateAll(applicationContext, weather)
            Result.success()
        } catch (_: Exception) {
            // Keep the last successful forecast visible during temporary network/API failures.
            Result.retry()
        }
    }

    companion object {
        private const val PERIODIC_WORK = "weather_window_periodic_refresh"
        private const val INITIAL_WORK = "weather_window_initial_refresh"
        private const val MANUAL_REFRESH_WORK = "weather_window_manual_refresh"

        fun refreshNow(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<WeatherUpdateWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                MANUAL_REFRESH_WORK,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val periodic = PeriodicWorkRequestBuilder<WeatherUpdateWorker>(30, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            val manager = WorkManager.getInstance(context.applicationContext)
            manager.enqueueUniquePeriodicWork(
                PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodic
            )

            val initial = OneTimeWorkRequestBuilder<WeatherUpdateWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            manager.enqueueUniqueWork(INITIAL_WORK, ExistingWorkPolicy.KEEP, initial)
        }
    }
}
