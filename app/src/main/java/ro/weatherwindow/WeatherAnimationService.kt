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
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.RemoteViews
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class WeatherAnimationService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var frame = 0
    @Volatile private var forceBackgroundRefresh = true
    private val ticker = object : Runnable {
        override fun run() {
            val prefs = getSharedPreferences("weather", Context.MODE_PRIVATE)
            val code = prefs.getInt("weather_code", 3)
            val currentFrame = frame++
            val sunrise = prefs.getString("sunrise", "06:00") ?: "06:00"
            val sunset = prefs.getString("sunset", "18:00") ?: "18:00"
            val timezone = prefs.getString("timezone", "Europe/Bucharest") ?: "Europe/Bucharest"
            val bitmap = WeatherAnimationFrames.render(code, currentFrame, sunrise, sunset, timezone)
            val manager = AppWidgetManager.getInstance(this@WeatherAnimationService)
            val ids = manager.getAppWidgetIds(ComponentName(this@WeatherAnimationService, WeatherWidgetProvider::class.java))
            ids.forEach { id ->
                val height = manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 280)
                val layout = if (height < 250) R.layout.weather_widget_compact else R.layout.weather_widget
                val views = RemoteViews(packageName, layout)
                views.setImageViewBitmap(R.id.weather_animation, bitmap)
                if (currentFrame % 50 == 0 || forceBackgroundRefresh) {
                    forceBackgroundRefresh = false
                    val background = WeatherAnimationFrames.renderBackground(code, currentFrame, sunrise, sunset, timezone)
                    views.setImageViewBitmap(R.id.weather_background, background)
                }
                val opacity = prefs.getInt("background_opacity", 100).coerceIn(0, 100)
                views.setViewVisibility(R.id.weather_background, android.view.View.VISIBLE)
                views.setViewVisibility(R.id.weather_overlay, android.view.View.GONE)
                views.setInt(R.id.weather_background, "setImageAlpha", (opacity * 255 / 100f).toInt())
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A screen-unlock broadcast can arrive after Android has throttled the
        // animation loop during Doze. Force an immediate fresh frame using the
        // current local time, rather than waiting for the old ticker cadence.
        forceBackgroundRefresh = true
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        return START_STICKY
    }

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

        fun start(context: Context, action: String? = null) {
            val intent = Intent(context, WeatherAnimationService::class.java).apply {
                this.action = action
            }
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
            else context.startService(intent)
        }
    }
}

/**
 * Requests an immediate animation redraw when the user unlocks the phone.
 * This receiver also restarts the foreground animation service if Android
 * reclaimed its process while the display was off.
 */
class WeatherScreenUnlockReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_USER_PRESENT) {
            // If WorkManager deferred the periodic job during Doze, request a
            // weather refresh after unlock when the last successful update is stale.
            WeatherUpdateWorker.refreshIfStale(context)
            try {
                WeatherAnimationService.start(context, Intent.ACTION_USER_PRESENT)
            } catch (_: Exception) {
                // The next normal widget/service update can recover if startup is restricted.
            }
        }
    }
}

internal object WeatherAnimationFrames {
    private const val W = 720
    private const val H = 300

    private const val BG_H = 440

    fun renderBackground(code: Int, frame: Int, sunrise: String, sunset: String, timezone: String): Bitmap {
        val bitmap = Bitmap.createBitmap(W, BG_H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val now = try { LocalTime.now(ZoneId.of(timezone)) } catch (_: Exception) { LocalTime.now() }
        val rise = try { LocalTime.parse(sunrise).toSecondOfDay() } catch (_: Exception) { 21600 }
        val set = try { LocalTime.parse(sunset).toSecondOfDay() } catch (_: Exception) { 64800 }
        val sec = now.toSecondOfDay()
        val dayLength = ((set - rise + 86400) % 86400).coerceAtLeast(1)
        val nightLength = (86400 - dayLength).coerceAtLeast(1)
        val isDay = if (rise < set) sec in rise..set else sec >= rise || sec <= set
        val dayElapsed = (sec - rise + 86400) % 86400
        val nightElapsed = (sec - set + 86400) % 86400
        val toRise = (rise - sec + 86400) % 86400
        val toSet = (set - sec + 86400) % 86400
        val transition = 2700f

        val nightTop = Color.rgb(5, 15, 38)
        val nightBottom = Color.rgb(17, 34, 66)
        val dawnTop = Color.rgb(82, 91, 143)
        val dawnBottom = Color.rgb(246, 142, 108)
        val dayTop = Color.rgb(20, 76, 135)
        val dayBottom = Color.rgb(52, 111, 163)
        val duskTop = Color.rgb(112, 72, 111)
        val duskBottom = Color.rgb(244, 125, 83)

        val top: Int
        val bottom: Int
        when {
            !isDay && toRise <= transition -> {
                val t = 1f - toRise / transition
                top = mix(nightTop, dawnTop, t)
                bottom = mix(nightBottom, dawnBottom, t)
            }
            isDay && dayElapsed <= transition -> {
                val t = dayElapsed / transition
                top = mix(dawnTop, dayTop, t)
                bottom = mix(dawnBottom, dayBottom, t)
            }
            isDay && toSet <= transition -> {
                val t = 1f - toSet / transition
                top = mix(dayTop, duskTop, t)
                bottom = mix(dayBottom, duskBottom, t)
            }
            !isDay && nightElapsed <= transition -> {
                val t = nightElapsed / transition
                top = mix(duskTop, nightTop, t)
                bottom = mix(duskBottom, nightBottom, t)
            }
            isDay -> { top = dayTop; bottom = dayBottom }
            else -> { top = nightTop; bottom = nightBottom }
        }

        val weather = when {
            code in 71..77 || code in 85..86 -> 4
            code in 51..67 || code in 80..82 -> 3
            code in 95..99 -> 5
            code == 45 || code == 48 -> 6
            code == 3 -> 2
            code == 1 || code == 2 -> 1
            else -> 0
        }
        val shade = when (weather) { 2 -> .24f; 3 -> .48f; 4 -> .18f; 5 -> .58f; 6 -> .30f; else -> 0f }
        p.shader = LinearGradient(
            0f, 0f, 0f, BG_H.toFloat(),
            mix(top, Color.rgb(9, 19, 35), shade),
            mix(bottom, Color.rgb(30, 42, 57), shade * .55f),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, W.toFloat(), BG_H.toFloat(), p)
        p.shader = null

        // A restrained lower vignette keeps the forecast text legible without a separate panel.
        p.shader = LinearGradient(0f, 255f, 0f, BG_H.toFloat(), 0x00081722, 0x66081722, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W.toFloat(), BG_H.toFloat(), p)
        p.shader = null
        return bitmap
    }

    fun render(code: Int, frame: Int, sunrise: String, sunset: String, timezone: String): Bitmap {
        val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val now = try { LocalTime.now(ZoneId.of(timezone)) } catch (_: Exception) { LocalTime.now() }
        val rise = try { LocalTime.parse(sunrise).toSecondOfDay() } catch (_: Exception) { 21600 }
        val set = try { LocalTime.parse(sunset).toSecondOfDay() } catch (_: Exception) { 64800 }
        val sec = now.toSecondOfDay()
        val dayLength = ((set - rise + 86400) % 86400).coerceAtLeast(1)
        val nightLength = 86400 - dayLength
        val isDay = if (rise < set) sec in rise..set else sec >= rise || sec <= set
        val dayElapsed = (sec - rise + 86400) % 86400
        val nightElapsed = (sec - set + 86400) % 86400
        val dayT = (dayElapsed.toFloat() / dayLength).coerceIn(0f, 1f)
        val nightT = (nightElapsed.toFloat() / nightLength.coerceAtLeast(1)).coerceIn(0f, 1f)
        val toRise = (rise - sec + 86400) % 86400
        val toSet = (set - sec + 86400) % 86400
        val dawn = !isDay && toRise <= 1800
        val dusk = isDay && toSet <= 1800
        val dawnMix = (1f - toRise / 1800f).coerceIn(0f, 1f)
        val duskMix = (1f - toSet / 1800f).coerceIn(0f, 1f)
        val phase = frame % 120
        val drift = kotlin.math.sin(phase * 2.0 * PI / 120.0).toFloat()
        val weather = when {
            code in 71..77 || code in 85..86 -> 4
            code in 51..67 || code in 80..82 -> 3
            code in 95..99 -> 5
            code == 45 || code == 48 -> 6
            code == 3 -> 2
            code == 1 || code == 2 -> 1
            else -> 0
        }
        val starAlpha = when { !isDay -> 255; dawn -> (255 * (1f - dawnMix)).toInt(); dusk -> (255 * duskMix).toInt(); else -> 0 }
        if (starAlpha > 0) {
            p.color = Color.argb(starAlpha, 225, 239, 255)
            for (i in 0 until 48) {
                val x = ((i * 137 + 29) % 700 + 10).toFloat()
                val y = ((i * 71 + 17) % 175 + 8).toFloat()
                c.drawCircle(x, y, if (i % 7 == 0) 2.2f else 1.1f, p)
            }
        }

        val left = 72f; val right = 648f; val base = 238f; val peak = -86f
        val arc = Path().apply { moveTo(left, base); quadTo(360f, peak, right, base) }
        p.style = Paint.Style.STROKE; p.strokeWidth = 5f; p.strokeCap = Paint.Cap.ROUND
        p.shader = LinearGradient(left, 0f, right, 0f,
            intArrayOf(0xFFFFB65E.toInt(), 0xFFFFD46F.toInt(), 0xFF9ADFFF.toInt(), 0xFF62B7FF.toInt()), null, Shader.TileMode.CLAMP)
        c.drawPath(arc, p); p.shader = null; p.style = Paint.Style.FILL
        fun point(t0: Float): Pair<Float, Float> {
            val t = t0.coerceIn(0f, 1f); val u = 1f - t
            return Pair(u*u*left + 2*u*t*360f + t*t*right, u*u*base + 2*u*t*peak + t*t*base)
        }
        fun sun(t: Float, alpha: Int) {
            if (alpha <= 0) return
            val (x,y)=point(t); p.alpha=alpha
            p.shader=RadialGradient(x,y,82f,intArrayOf(0x99FFD76A.toInt(),0x00FFD76A),null,Shader.TileMode.CLAMP)
            c.drawCircle(x,y,82f,p); p.shader=null
            p.color=Color.argb(alpha,255,190,67); p.strokeWidth=4f; p.strokeCap=Paint.Cap.ROUND
            for(i in 0 until 12){ val a=i*PI/6.0+frame*.003; c.drawLine(x+kotlin.math.cos(a).toFloat()*32f,y+kotlin.math.sin(a).toFloat()*32f,x+kotlin.math.cos(a).toFloat()*43f,y+kotlin.math.sin(a).toFloat()*43f,p) }
            p.color=Color.argb(alpha,255,207,91); c.drawCircle(x,y,27f,p)
            p.color=Color.argb(alpha,255,239,183); c.drawCircle(x-7f,y-8f,10f,p); p.alpha=255
        }
        fun moon(t: Float, alpha: Int) {
            if(alpha<=0)return
            val(x,y)=point(t); p.alpha=alpha
            p.shader=RadialGradient(x,y,60f,intArrayOf(0x668CCBFF,0x008CCBFF),null,Shader.TileMode.CLAMP)
            c.drawCircle(x,y,60f,p); p.shader=null
            p.color=Color.argb(alpha,207,226,255); c.drawCircle(x,y,29f,p)
            p.color=Color.argb(alpha,153,181,218); c.drawCircle(x-9f,y-8f,6f,p); c.drawCircle(x+11f,y+8f,8f,p); c.drawCircle(x+5f,y-15f,4f,p); p.alpha=255
        }
        if(isDay){ sun(dayT,if(dusk) (255*(1f-duskMix)).toInt() else 255); if(dusk) moon(0f,(255*duskMix).toInt()) }
        else { moon(nightT,if(dawn) (255*(1f-dawnMix)).toInt() else 255); if(dawn) sun(0f,(255*dawnMix).toInt()) }

        when(weather){
            1 -> cloud(c,p,470f+drift*18f,184f,.75f,0xFFE1EAF4.toInt())
            2 -> { cloud(c,p,245f+drift*16f,180f,.82f,0xFFD8E4EF.toInt()); cloud(c,p,530f-drift*12f,188f,.78f,0xFFBFCFDF.toInt()) }
            3,4 -> cloud(c,p,500f+drift*10f,178f,.92f,if(weather==4) 0xFFE2EAF2.toInt() else 0xFF8299B0.toInt())
            5 -> { cloud(c,p,245f,178f,.9f,0xFF586B82.toInt()); cloud(c,p,510f,180f,.94f,0xFF465A72.toInt()) }
            6 -> { cloud(c,p,370f+drift*10f,188f,.82f,0xFFB8C9D8.toInt()); p.color=0x559EB7CB; for(i in 0..3){val y=205f+i*22f;val x=((phase*2+i*41)%90)-45f;c.drawRoundRect(20f+x,y,700f+x,y+8f,8f,8f,p)} }
        }
        when(weather){
            3 -> { p.color=0xCC9BDCF4.toInt();p.strokeWidth=2.8f;p.strokeCap=Paint.Cap.ROUND;for(i in 0 until 42){val x=((i*83+phase*7)%760-20).toFloat();val y=190f+((i*47+phase*11)%112);c.drawLine(x,y,x-7f,y+17f,p)} }
            4 -> { p.color=0xFFEAF5FA.toInt();for(i in 0 until 42){val x=((i*97+phase*3)%720).toFloat();val y=175f+((i*59+phase*5)%120);c.drawCircle(x,y,2.2f+(i%3)*1.1f,p)} }
            5 -> { p.color=if(phase%30<4) 0xFFFFE58A.toInt() else 0x88FFE58A.toInt();val bolt=Path().apply{moveTo(420f,174f);lineTo(395f,218f);lineTo(416f,218f);lineTo(401f,254f);lineTo(446f,207f);lineTo(425f,207f);close()};c.drawPath(bolt,p);p.color=0xBB9BDCF4.toInt();p.strokeWidth=2.5f;for(i in 0 until 24){val x=((i*113+phase*8)%740).toFloat();val y=190f+((i*61+phase*12)%110);c.drawLine(x,y,x-7f,y+18f,p)} }
        }
        return bitmap
    }

    private fun cloud(c:Canvas,p:Paint,x:Float,y:Float,s:Float,color:Int){
        p.shader=RadialGradient(x-18f*s,y-18f*s,130f*s,intArrayOf(0xFFF8FCFF.toInt(),color),null,Shader.TileMode.CLAMP)
        c.drawCircle(x-42f*s,y+8f*s,34f*s,p);c.drawCircle(x,y-12f*s,47f*s,p);c.drawCircle(x+43f*s,y+7f*s,33f*s,p)
        c.drawRoundRect(x-76f*s,y+1f*s,x+76f*s,y+38f*s,20f*s,20f*s,p);p.shader=null
        p.color=0x33FFFFFF;c.drawCircle(x-8f*s,y-25f*s,15f*s,p)
    }
    private fun mix(a:Int,b:Int,t0:Float):Int{val t=t0.coerceIn(0f,1f);return Color.rgb((Color.red(a)*(1-t)+Color.red(b)*t).toInt(),(Color.green(a)*(1-t)+Color.green(b)*t).toInt(),(Color.blue(a)*(1-t)+Color.blue(b)*t).toInt())}
}
