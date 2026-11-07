package ro.weatherwindow

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.RemoteViews
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class WeatherAnimationService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var frame = 0
    private val ticker = object : Runnable {
        override fun run() {
            val prefs = getSharedPreferences("weather", Context.MODE_PRIVATE)
            val code = prefs.getInt("weather_code", 3)
            val currentFrame = frame++
            val bitmap = WeatherAnimationFrames.render(code, currentFrame)
            val scene = if (currentFrame % 5 == 0) SceneBackground.render(
                code,
                currentFrame,
                prefs.getString("sunrise", "06:00") ?: "06:00",
                prefs.getString("sunset", "18:00") ?: "18:00",
                prefs.getString("timezone", "Europe/Bucharest") ?: "Europe/Bucharest"
            ) else null
            val manager = AppWidgetManager.getInstance(this@WeatherAnimationService)
            val ids = manager.getAppWidgetIds(ComponentName(this@WeatherAnimationService, WeatherWidgetProvider::class.java))
            ids.forEach { id ->
                val height = manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 280)
                val layout = if (height < 250) R.layout.weather_widget_compact else R.layout.weather_widget
                val views = RemoteViews(packageName, layout)
                views.setImageViewBitmap(R.id.weather_animation, bitmap)
                scene?.let { views.setImageViewBitmap(R.id.weather_background, it) }
                val opacity = prefs.getInt("background_opacity", 100).coerceIn(0, 100)
                views.setInt(R.id.weather_background, "setImageAlpha", (opacity * 255 / 100f).toInt())
                views.setInt(R.id.weather_overlay, "setImageAlpha", (opacity * 255 / 100f).toInt())
                manager.partiallyUpdateAppWidget(id, views)
            }
            handler.postDelayed(this, 100L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val open = PendingIntent.getActivity(
            this, 7102, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }.setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("WeatherWindow activ")
            .setContentText("Animația meteo rulează continuu în widget.")
            .setContentIntent(open)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        handler.post(ticker)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "WeatherWindow – animație", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Notificare permanentă pentru animația meteo din widget"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "weather_animation"
        private const val NOTIFICATION_ID = 7101

        fun start(context: Context) {
            val intent = Intent(context, WeatherAnimationService::class.java)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
            else context.startService(intent)
        }
    }
}

internal object WeatherAnimationFrames {
    private const val W = 360
    private const val H = 220
    private val white = Color.rgb(245, 250, 255)

    fun render(code: Int, frame: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val phase = frame % 48
        val drift = sin(phase * 2.0 * PI / 48.0).toFloat()
        val type = when {
            code == 0 -> 0
            code == 1 || code == 2 -> 1
            code == 3 || code == 45 || code == 48 -> 2
            code in 51..67 || code in 80..82 -> 3
            code in 71..77 || code in 85..86 -> 4
            code in 95..99 -> 5
            else -> 2
        }

        if (type == 0 || type == 1) {
            p.shader = RadialGradient(180f, 78f, 82f, intArrayOf(0x66FFD76A, 0x00FFD76A), null, Shader.TileMode.CLAMP)
            canvas.drawCircle(180f, 78f, 82f, p)
            p.shader = null
            val sunX = if (type == 1) 118f else 180f
            val sunY = 76f + drift * 2f
            p.color = Color.rgb(255, 194, 77)
            p.strokeWidth = 8f
            p.strokeCap = Paint.Cap.ROUND
            for (i in 0 until 12) {
                val a = i * PI / 6.0 + phase * 0.035
                val x1 = sunX + cos(a).toFloat() * 39f
                val y1 = sunY + sin(a).toFloat() * 39f
                val x2 = sunX + cos(a).toFloat() * 53f
                val y2 = sunY + sin(a).toFloat() * 53f
                canvas.drawLine(x1, y1, x2, y2, p)
            }
            p.color = Color.rgb(255, 211, 103)
            canvas.drawCircle(sunX, sunY, 31f, p)
        }

        if (type != 0) {
            val cx = if (type == 1) 218f + drift * 8f else 180f + drift * 7f
            val cy = if (type == 1) 112f else 92f + drift * 2f
            drawCloud(canvas, p, cx, cy, if (type == 4) 0xFFE3EDF7.toInt() else 0xFFE8F1F8.toInt())
        }

        when (type) {
            2 -> {
                p.color = 0x99D9E8F3.toInt()
                p.strokeWidth = 7f
                p.strokeCap = Paint.Cap.ROUND
                for (i in 0 until 3) {
                    val y = 151f + i * 19f
                    val shift = ((phase * 3 + i * 45) % 110) - 55f
                    canvas.drawLine(75f + shift, y, 275f + shift, y, p)
                }
            }
            3 -> {
                p.color = 0xFF8ED7FF.toInt()
                p.strokeWidth = 6f
                p.strokeCap = Paint.Cap.ROUND
                for (i in 0 until 12) {
                    val x = 82f + i * 18f
                    val y = 132f + ((phase * 8 + i * 29) % 100)
                    canvas.drawLine(x, y, x - 11f, y + 23f, p)
                }
            }
            4 -> {
                p.color = white
                for (i in 0 until 15) {
                    val x = 72f + ((i * 47 + phase * 3) % 220)
                    val y = 122f + ((i * 31 + phase * 5) % 88)
                    val r = 3f + (i % 3)
                    canvas.drawCircle(x.toFloat(), y.toFloat(), r, p)
                }
            }
            5 -> {
                p.color = if (phase % 12 < 3) 0xFFFFE27A.toInt() else 0x88FFE27A.toInt()
                p.strokeWidth = 11f
                p.strokeJoin = Paint.Join.ROUND
                val bolt = Path().apply {
                    moveTo(190f, 116f); lineTo(158f, 166f); lineTo(183f, 166f)
                    lineTo(166f, 207f); lineTo(218f, 151f); lineTo(191f, 151f); close()
                }
                canvas.drawPath(bolt, p)
                p.color = 0x66FFE27A
                p.strokeWidth = 3f
                canvas.drawLine(190f, 116f, 158f, 166f, p)
            }
        }
        return bitmap
    }

    private fun drawCloud(canvas: Canvas, p: Paint, cx: Float, cy: Float, color: Int) {
        p.shader = RadialGradient(cx - 12f, cy - 19f, 110f, intArrayOf(0xFFFFFFFF.toInt(), color), null, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx - 38f, cy + 4f, 36f, p)
        canvas.drawCircle(cx + 2f, cy - 20f, 48f, p)
        canvas.drawCircle(cx + 43f, cy + 1f, 34f, p)
        canvas.drawRoundRect(cx - 70f, cy - 4f, cx + 73f, cy + 35f, 22f, 22f, p)
        p.shader = null
    }
}
