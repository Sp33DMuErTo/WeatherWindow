package ro.weatherwindow

import android.content.Context
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
            val lat = prefs.getFloat("lat", 0f).toDouble()
            val lon = prefs.getFloat("lon", 0f).toDouble()
            val city = prefs.getString("city", "Locația mea") ?: "Locația mea"
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
