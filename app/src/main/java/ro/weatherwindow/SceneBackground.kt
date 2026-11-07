package ro.weatherwindow

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * A layered, time-aware landscape used behind the widget content.
 * The cottage and tree remain fixed while the sky, sun/moon and weather
 * effects respond independently to local time and the current weather code.
 */
internal object SceneBackground {
    private const val W = 720
    private const val H = 440
    private const val HORIZON = 292f

    fun render(code: Int, frame: Int, sunrise: String, sunset: String, timezone: String): Bitmap {
        val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val phase = frame % 120
        val drift = sin(phase * 2.0 * PI / 120.0).toFloat()
        val now = try { LocalTime.now(ZoneId.of(timezone)) } catch (_: Exception) { LocalTime.now() }
        val rise = parseTime(sunrise, LocalTime.of(6, 0))
        val set = parseTime(sunset, LocalTime.of(18, 0))
        val dayLength = max(1, set.toSecondOfDay() - rise.toSecondOfDay())
        val elapsed = now.toSecondOfDay() - rise.toSecondOfDay()
        val daylight = elapsed in 0..dayLength
        val progress = (elapsed.toFloat() / dayLength).coerceIn(0f, 1f)
        val minutesFromRise = (now.toSecondOfDay() - rise.toSecondOfDay()) / 60
        val minutesToSet = (set.toSecondOfDay() - now.toSecondOfDay()) / 60
        val twilight = !daylight && (minutesFromRise in -55..0 || minutesToSet in 0..55)
        val night = !daylight && !twilight

        val weather = when {
            code in 71..77 || code in 85..86 -> 4
            code in 51..67 || code in 80..82 -> 3
            code in 95..99 -> 5
            code == 45 || code == 48 -> 6
            code == 3 -> 2
            code == 1 || code == 2 -> 1
            else -> 0
        }

        val skyTop: Int
        val skyBottom: Int
        when {
            night -> { skyTop = Color.rgb(13, 25, 48); skyBottom = Color.rgb(54, 76, 99) }
            twilight -> {
                val dawn = minutesFromRise in -55..0
                skyTop = if (dawn) Color.rgb(65, 79, 125) else Color.rgb(76, 51, 83)
                skyBottom = if (dawn) Color.rgb(247, 151, 111) else Color.rgb(245, 126, 83)
            }
            progress < 0.22f -> { skyTop = Color.rgb(69, 104, 155); skyBottom = Color.rgb(255, 177, 126) }
            progress > 0.78f -> { skyTop = Color.rgb(81, 94, 143); skyBottom = Color.rgb(255, 163, 103) }
            else -> { skyTop = Color.rgb(75, 153, 210); skyBottom = Color.rgb(188, 221, 231) }
        }
        if (weather == 2 || weather == 3 || weather == 4 || weather == 5 || weather == 6) {
            val shade = when (weather) { 5 -> 0.48f; 4 -> 0.78f; 6 -> 0.68f; 3 -> 0.72f; else -> 0.84f }
            p.color = blend(skyTop, Color.rgb(60, 73, 91), shade)
            val top = p.color
            p.color = blend(skyBottom, Color.rgb(95, 108, 121), shade * 0.72f)
            val bottom = p.color
            p.shader = LinearGradient(0f, 0f, 0f, H.toFloat(), top, bottom, Shader.TileMode.CLAMP)
        } else {
            p.shader = LinearGradient(0f, 0f, 0f, H.toFloat(), skyTop, skyBottom, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
        p.shader = null

        // Soft atmospheric glow at sunrise and sunset.
        if (daylight && (progress < 0.24f || progress > 0.76f) || twilight) {
            val sunX = if (daylight) 62f + progress * 596f else if (minutesFromRise in -55..0) 92f else 628f
            val glow = if (minutesFromRise in -55..0 || progress < 0.24f) 0x66FFB16E else 0x66FF8A58
            p.shader = RadialGradient(sunX, HORIZON - 5f, 230f, intArrayOf(glow, 0x00FFB16E), null, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, W.toFloat(), HORIZON + 30f, p)
            p.shader = null
        }

        // Sun follows the real daylight arc between the supplied sunrise and sunset.
        if (daylight) {
            val sunX = 52f + progress * 616f
            val sunY = HORIZON - 12f - sin(progress * PI).toFloat() * 174f
            p.shader = RadialGradient(sunX, sunY, 102f, intArrayOf(0x66FFE7A0, 0x00FFE7A0), null, Shader.TileMode.CLAMP)
            canvas.drawCircle(sunX, sunY, 102f, p)
            p.shader = null
            p.color = Color.rgb(255, 220, 137)
            canvas.drawCircle(sunX, sunY, 27f, p)
            p.color = 0x66FFF1C6
            canvas.drawCircle(sunX - 7f, sunY - 8f, 18f, p)
        } else if (night) {
            val moonX = 550f
            val moonY = 84f
            p.color = 0xFFFFE8B4.toInt()
            canvas.drawCircle(moonX, moonY, 25f, p)
            p.color = 0xFF263B5A.toInt()
            canvas.drawCircle(moonX + 11f, moonY - 8f, 24f, p)
            p.color = 0x99FFFFFF.toInt()
            for (i in 0 until 34) {
                val x = ((i * 137 + 29) % 700 + 10).toFloat()
                val y = ((i * 71 + 17) % 178 + 12).toFloat()
                val radius = if (i % 5 == 0) 2.6f else 1.3f
                canvas.drawCircle(x, y, radius, p)
            }
        }

        // Distant hills and layered meadow.
        p.color = if (night) 0xFF293E43.toInt() else 0xFF648C75.toInt()
        val hill = Path().apply {
            moveTo(0f, 286f); cubicTo(145f, 238f, 220f, 280f, 340f, 260f)
            cubicTo(475f, 235f, 565f, 275f, 720f, 245f); lineTo(720f, 440f); lineTo(0f, 440f); close()
        }
        canvas.drawPath(hill, p)
        p.shader = LinearGradient(0f, 300f, 0f, 440f,
            if (night) 0xFF243C39.toInt() else 0xFF477D56.toInt(),
            if (night) 0xFF172D2B.toInt() else 0xFF254E3C.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 300f, 720f, 440f, p)
        p.shader = null

        // Snow blanket over the lawn in wintry conditions.
        if (weather == 4) {
            p.color = if (night) 0xFFD4E1E8.toInt() else 0xFFE8F1F2.toInt()
            val snow = Path().apply {
                moveTo(0f, 347f); quadTo(180f, 326f, 350f, 350f)
                quadTo(535f, 370f, 720f, 338f); lineTo(720f, 440f); lineTo(0f, 440f); close()
            }
            canvas.drawPath(snow, p)
        }

        // Cottage body, roof and chimney.
        p.color = 0xFF594A42.toInt()
        canvas.drawRect(78f, 222f, 112f, 292f, p)
        p.color = 0xFF8D654E.toInt()
        canvas.drawRect(102f, 210f, 300f, 356f, p)
        p.color = 0xFFB98562.toInt()
        canvas.drawRect(112f, 220f, 290f, 348f, p)
        val roof = Path().apply {
            moveTo(65f, 222f); lineTo(195f, 130f); lineTo(326f, 222f); close()
        }
        p.color = 0xFF493B3B.toInt()
        canvas.drawPath(roof, p)
        p.color = 0xFF76524A.toInt()
        val roofLight = Path().apply {
            moveTo(195f, 130f); lineTo(326f, 222f); lineTo(298f, 222f); lineTo(195f, 151f); close()
        }
        canvas.drawPath(roofLight, p)
        p.color = 0xFF58413B.toInt()
        canvas.drawRect(270f, 145f, 292f, 194f, p)
        p.color = 0xFF8A6252.toInt()
        canvas.drawRect(267f, 140f, 295f, 150f, p)

        // Front door and warm windows; windows glow after sunset.
        p.color = 0xFF49362F.toInt()
        canvas.drawRoundRect(174f, 274f, 225f, 356f, 4f, 4f, p)
        canvas.drawCircle(215f, 316f, 3f, p)
        val windowColor = if (night || twilight) 0xFFFFD77C.toInt() else 0xFFB7D9E5.toInt()
        p.color = windowColor
        canvas.drawRoundRect(126f, 245f, 164f, 281f, 3f, 3f, p)
        canvas.drawRoundRect(240f, 245f, 276f, 281f, 3f, 3f, p)
        p.color = 0xFF64483A.toInt()
        canvas.drawRect(143f, 245f, 147f, 281f, p)
        canvas.drawRect(126f, 261f, 164f, 265f, p)
        canvas.drawRect(256f, 245f, 260f, 281f, p)
        canvas.drawRect(240f, 261f, 276f, 265f, p)
        if (night || twilight) {
            p.shader = RadialGradient(145f, 263f, 75f, intArrayOf(0x55FFD36A, 0x00FFD36A), null, Shader.TileMode.CLAMP)
            canvas.drawCircle(145f, 263f, 75f, p)
            canvas.drawCircle(258f, 263f, 75f, p)
            p.shader = null
            p.color = 0xFFFFD36A.toInt()
            canvas.drawCircle(145f, 263f, 4f, p)
            canvas.drawCircle(258f, 263f, 4f, p)
        }

        // Tree trunk and layered foliage.
        p.color = 0xFF594334.toInt()
        canvas.drawRoundRect(510f, 236f, 536f, 376f, 8f, 8f, p)
        p.color = if (weather == 4) 0xFF49634E.toInt() else if (night) 0xFF1E453B.toInt() else 0xFF2D6847.toInt()
        val leaves = arrayOf(
            floatArrayOf(523f, 165f, 65f), floatArrayOf(480f, 205f, 53f),
            floatArrayOf(559f, 207f, 57f), floatArrayOf(523f, 225f, 66f),
            floatArrayOf(460f, 244f, 39f), floatArrayOf(580f, 245f, 42f)
        )
        for ((x, y, radius) in leaves) canvas.drawCircle(x, y, radius, p)
        p.color = if (weather == 4) 0xFFEAF1F0.toInt() else if (night) 0xFF2C5848.toInt() else 0xFF4B8759.toInt()
        canvas.drawCircle(505f, 170f, 30f, p)
        canvas.drawCircle(548f, 192f, 25f, p)

        if (weather == 4) {
            // Snow rests on the roof ridge, branches and ground.
            p.color = 0xFFF4F7F7.toInt()
            val roofSnow = Path().apply {
                moveTo(66f, 222f); lineTo(195f, 130f); lineTo(326f, 222f)
                lineTo(308f, 218f); lineTo(195f, 145f); lineTo(82f, 226f); close()
            }
            canvas.drawPath(roofSnow, p)
            canvas.drawArc(445f, 112f, 600f, 255f, 195f, 150f, false, p.apply { style = Paint.Style.STROKE; strokeWidth = 10f })
            p.style = Paint.Style.FILL
            canvas.drawCircle(482f, 158f, 7f, p)
            canvas.drawCircle(550f, 145f, 8f, p)
            canvas.drawCircle(585f, 218f, 6f, p)
        }

        // Atmospheric fog layer.
        if (weather == 6) {
            p.color = 0x66DCE7E9
            for (i in 0..3) {
                val y = 255f + i * 33f
                val offset = ((phase * 2 + i * 67) % 120) - 60f
                canvas.drawRoundRect(offset, y, 420f + offset, y + 15f, 10f, 10f, p)
            }
        }

        // Rain, snow or storm particles are separate from the day/night scene.
        when (weather) {
            3 -> {
                p.color = 0xAA9BDCF4.toInt()
                p.strokeWidth = 3.5f
                p.strokeCap = Paint.Cap.ROUND
                for (i in 0 until 42) {
                    val x = ((i * 83 + phase * 7) % 760 - 20).toFloat()
                    val y = ((i * 47 + phase * 11) % 450 - 10).toFloat()
                    canvas.drawLine(x, y, x - 8f, y + 18f, p)
                }
            }
            4 -> {
                p.color = 0xFFEAF5FA.toInt()
                for (i in 0 until 55) {
                    val x = ((i * 97 + phase * 3) % 720).toFloat()
                    val y = ((i * 59 + phase * 5) % 440).toFloat()
                    val r = 2.2f + (i % 3) * 1.1f
                    canvas.drawCircle(x, y, r, p)
                }
            }
            5 -> {
                p.color = if (phase % 30 < 4) 0xFFFFE58A.toInt() else 0x88FFE58A.toInt()
                val bolt = Path().apply {
                    moveTo(410f, 102f); lineTo(380f, 168f); lineTo(408f, 168f)
                    lineTo(389f, 215f); lineTo(442f, 151f); lineTo(415f, 151f); close()
                }
                canvas.drawPath(bolt, p)
                p.color = 0x889BDCF4.toInt()
                p.strokeWidth = 3f
                for (i in 0 until 24) {
                    val x = ((i * 113 + phase * 8) % 740).toFloat()
                    val y = ((i * 61 + phase * 12) % 430).toFloat()
                    canvas.drawLine(x, y, x - 9f, y + 20f, p)
                }
            }
        }

        // Subtle edge vignette helps keep white widget text readable.
        p.shader = RadialGradient(360f, 210f, 520f,
            intArrayOf(0x00000000, 0x44081722), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
        p.shader = null
        return bitmap
    }

    private fun parseTime(value: String, fallback: LocalTime): LocalTime =
        try { LocalTime.parse(value) } catch (_: Exception) { fallback }

    private fun blend(a: Int, b: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        val r = (Color.red(a) * (1f - t) + Color.red(b) * t).toInt()
        val g = (Color.green(a) * (1f - t) + Color.green(b) * t).toInt()
        val bl = (Color.blue(a) * (1f - t) + Color.blue(b) * t).toInt()
        return Color.rgb(r, g, bl)
    }
}
