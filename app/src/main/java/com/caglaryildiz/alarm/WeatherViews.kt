package com.caglaryildiz.alarm

import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Draws the weather symbols in the app's soft, night-sky style, without image assets. */
internal object WeatherArt {
    private val path = Path()
    private val cutout = Path()

    fun draw(canvas: Canvas, paint: Paint, sky: Sky, night: Boolean, cx: Float, cy: Float, size: Float) {
        val s = size
        when (sky) {
            Sky.CLEAR -> celestial(canvas, paint, night, cx, cy, s * .26f)
            Sky.PARTLY -> {
                celestial(canvas, paint, night, cx - s * .14f, cy - s * .14f, s * .2f)
                cloud(canvas, paint, cx + s * .06f, cy + s * .05f, s * .62f, night, wet = false)
            }
            Sky.CLOUDY -> {
                paint.alpha = 150
                cloud(canvas, paint, cx - s * .13f, cy - s * .13f, s * .48f, night = true, wet = false, keepAlpha = true)
                paint.alpha = 255
                cloud(canvas, paint, cx + s * .05f, cy + s * .04f, s * .68f, night, wet = false)
            }
            Sky.FOG -> {
                cloud(canvas, paint, cx, cy - s * .12f, s * .62f, night, wet = false)
                fog(canvas, paint, cx, cy + s * .18f, s)
            }
            Sky.RAIN, Sky.SLEET, Sky.SNOW, Sky.THUNDER -> {
                val top = cy + s * .13f
                when (sky) {
                    Sky.RAIN -> drops(canvas, paint, cx, top, s, listOf(-1, 0, 1))
                    Sky.SLEET -> {
                        drops(canvas, paint, cx, top, s, listOf(-1, 1))
                        flakes(canvas, paint, cx, top, s, listOf(0))
                    }
                    Sky.SNOW -> flakes(canvas, paint, cx, top, s, listOf(-1, 0, 1))
                    else -> bolt(canvas, paint, cx, top, s)
                }
                cloud(canvas, paint, cx, cy - s * .14f, s * .7f, night, wet = true)
            }
        }
        paint.shader = null
        paint.alpha = 255
        paint.style = Paint.Style.FILL
    }

    private fun celestial(canvas: Canvas, paint: Paint, night: Boolean, x: Float, y: Float, r: Float) {
        if (night) moon(canvas, paint, x, y, r) else sun(canvas, paint, x, y, r)
    }

    private fun sun(canvas: Canvas, paint: Paint, x: Float, y: Float, r: Float) {
        paint.shader = RadialGradient(x, y, r * 2.1f, intArrayOf(0x66FFD98C, 0x00FFD98C), null, Shader.TileMode.CLAMP)
        canvas.drawCircle(x, y, r * 2.1f, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = r * .16f
        paint.color = 0xFFFFD98C.toInt()
        for (ray in 0 until 8) {
            val angle = ray * Math.PI / 4
            val c = cos(angle).toFloat()
            val sn = sin(angle).toFloat()
            canvas.drawLine(x + c * r * 1.3f, y + sn * r * 1.3f, x + c * r * 1.62f, y + sn * r * 1.62f, paint)
        }
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(x - r * .3f, y - r * .35f, r * 1.3f,
            intArrayOf(0xFFFFF6D2.toInt(), 0xFFFFCF73.toInt(), 0xFFF5A04E.toInt()),
            floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(x, y, r, paint)
        paint.shader = null
    }

    private fun moon(canvas: Canvas, paint: Paint, x: Float, y: Float, r: Float) {
        paint.shader = RadialGradient(x, y, r * 2f, intArrayOf(0x55C5BAFF, 0x00C5BAFF), null, Shader.TileMode.CLAMP)
        canvas.drawCircle(x, y, r * 2f, paint)
        path.reset()
        cutout.reset()
        path.addCircle(x, y, r, Path.Direction.CW)
        cutout.addCircle(x + r * .52f, y - r * .34f, r * .86f, Path.Direction.CW)
        path.op(cutout, Path.Op.DIFFERENCE)
        paint.shader = LinearGradient(x - r, y - r, x + r, y + r,
            0xFFFBF8FF.toInt(), 0xFFB9ADF5.toInt(), Shader.TileMode.CLAMP)
        canvas.drawPath(path, paint)
        paint.shader = null
        paint.color = 0xCCFFFFFF.toInt()
        canvas.drawCircle(x + r * 1.05f, y + r * .35f, r * .08f, paint)
        canvas.drawCircle(x + r * .7f, y + r * .95f, r * .05f, paint)
    }

    private fun cloud(
        canvas: Canvas, paint: Paint, x: Float, y: Float, w: Float,
        night: Boolean, wet: Boolean, keepAlpha: Boolean = false
    ) {
        path.reset()
        path.addRoundRect(x - w * .5f, y - w * .02f, x + w * .5f, y + w * .28f, w * .15f, w * .15f, Path.Direction.CW)
        path.addCircle(x - w * .22f, y + w * .02f, w * .2f, Path.Direction.CW)
        path.addCircle(x + w * .04f, y - w * .07f, w * .28f, Path.Direction.CW)
        path.addCircle(x + w * .29f, y + w * .07f, w * .17f, Path.Direction.CW)
        val (top, bottom) = when {
            wet -> 0xFFE4E7F7.toInt() to 0xFF9EA6CC.toInt()
            night -> 0xFFE2DDFA.toInt() to 0xFF9890C9.toInt()
            else -> 0xFFFFFFFF.toInt() to 0xFFCED6F4.toInt()
        }
        val alpha = paint.alpha
        paint.shader = LinearGradient(x, y - w * .36f, x, y + w * .28f, top, bottom, Shader.TileMode.CLAMP)
        if (!keepAlpha) paint.alpha = 255 else paint.alpha = alpha
        canvas.drawPath(path, paint)
        paint.shader = null
    }

    private fun drops(canvas: Canvas, paint: Paint, cx: Float, top: Float, s: Float, columns: List<Int>) {
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = s * .045f
        paint.color = 0xFF8FD0FF.toInt()
        for (column in columns) {
            val x = cx + column * s * .17f
            val offset = if (column == 0) s * .05f else 0f
            canvas.drawLine(x + s * .03f, top + offset, x - s * .03f, top + offset + s * .14f, paint)
        }
        paint.style = Paint.Style.FILL
    }

    private fun flakes(canvas: Canvas, paint: Paint, cx: Float, top: Float, s: Float, columns: List<Int>) {
        paint.color = 0xFFF4F7FF.toInt()
        for (column in columns) {
            val x = cx + column * s * .17f
            val y = top + s * .07f + if (column == 0) s * .05f else 0f
            canvas.drawCircle(x, y, s * .038f, paint)
        }
    }

    private fun bolt(canvas: Canvas, paint: Paint, cx: Float, top: Float, s: Float) {
        path.reset()
        path.moveTo(cx + s * .04f, top - s * .02f)
        path.lineTo(cx - s * .09f, top + s * .15f)
        path.lineTo(cx, top + s * .15f)
        path.lineTo(cx - s * .05f, top + s * .3f)
        path.lineTo(cx + s * .1f, top + s * .1f)
        path.lineTo(cx + s * .01f, top + s * .1f)
        path.close()
        paint.color = 0xFFFFD98C.toInt()
        canvas.drawPath(path, paint)
    }

    private fun fog(canvas: Canvas, paint: Paint, cx: Float, y: Float, s: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = s * .045f
        paint.color = 0xCCD4D8F0.toInt()
        canvas.drawLine(cx - s * .3f, y, cx + s * .22f, y, paint)
        canvas.drawLine(cx - s * .2f, y + s * .11f, cx + s * .3f, y + s * .11f, paint)
        paint.style = Paint.Style.FILL
    }
}

/** A single weather symbol. With [floating] it drifts up and down slowly while it is on screen. */
internal class WeatherGlyph(
    context: Context,
    private val sky: Sky,
    private val night: Boolean,
    floating: Boolean = false
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var drift: ObjectAnimator? = null

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        if (floating) {
            val distance = 4 * resources.displayMetrics.density
            drift = ObjectAnimator.ofFloat(this, "translationY", -distance, distance).apply {
                duration = 2800
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = AccelerateDecelerateInterpolator()
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        drift?.start()
    }

    override fun onDetachedFromWindow() {
        drift?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val size = minOf(width, height).toFloat()
        if (size <= 0f) return
        WeatherArt.draw(canvas, paint, sky, night, width / 2f, height / 2f, size)
    }
}

/** The next hours as a scrollable strip: time, symbol, a smooth temperature curve and rain amounts. */
internal class HourlyChart(context: Context, private val hours: List<WeatherHour>) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val curve = Path()
    private val fill = Path()
    private val hourFormat = DateTimeFormatter.ofPattern("HH:mm")
    private val column = 60 * density
    private val chartHeight = 178 * density
    private val curveTop = 98 * density
    private val curveBottom = 132 * density

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun sp(value: Float) =
        android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension((column * hours.size).roundToInt(), chartHeight.roundToInt())
    }

    override fun onDraw(canvas: Canvas) {
        if (hours.isEmpty()) return
        // Keep at least a 6° window so a flat day draws a calm line in the middle, not at the bottom.
        val middle = (hours.minOf { it.temp } + hours.maxOf { it.temp }) / 2
        val range = (hours.maxOf { it.temp } - hours.minOf { it.temp }).coerceAtLeast(6.0)
        val low = middle - range / 2
        val points = hours.mapIndexed { index, hour ->
            val x = column * (index + .5f)
            val y = curveBottom - ((hour.temp - low) / range).toFloat() * (curveBottom - curveTop)
            x to y
        }

        curve.reset()
        points.forEachIndexed { index, (x, y) ->
            if (index == 0) {
                curve.moveTo(x, y)
            } else {
                val (px, py) = points[index - 1]
                val mid = (px + x) / 2
                curve.cubicTo(mid, py, mid, y, x, y)
            }
        }
        fill.reset()
        fill.addPath(curve)
        fill.lineTo(points.last().first, curveBottom + 26 * density)
        fill.lineTo(points.first().first, curveBottom + 26 * density)
        fill.close()
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(0f, curveTop, 0f, curveBottom + 26 * density,
            0x66C5BAFF, 0x00C5BAFF, Shader.TileMode.CLAMP)
        canvas.drawPath(fill, paint)
        paint.shader = LinearGradient(0f, 0f, width.toFloat(), 0f,
            0xFFC5BAFF.toInt(), 0xFF9BDFFF.toInt(), Shader.TileMode.CLAMP)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f * density
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawPath(curve, paint)
        paint.shader = null
        paint.style = Paint.Style.FILL

        hours.forEachIndexed { index, hour ->
            val (x, y) = points[index]
            val first = index == 0
            text.textSize = sp(12.5f)
            text.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            text.color = if (first) accentColor else mutedColor
            val label = if (first) context.getString(R.string.weather_now) else hourFormat.format(hour.time)
            canvas.drawText(label, x, 18 * density, text)

            WeatherArt.draw(canvas, paint, hour.sky, hour.night, x, 46 * density, 34 * density)

            text.textSize = sp(14.5f)
            text.color = android.graphics.Color.WHITE
            canvas.drawText("${hour.temp.roundToInt()}°", x, y - 10 * density, text)

            paint.color = if (first) goldColor else 0xFFEDE8FF.toInt()
            canvas.drawCircle(x, y, (if (first) 4.5f else 3.2f) * density, paint)

            if (hour.precip >= 0.1) {
                text.textSize = sp(11.5f)
                text.typeface = Typeface.DEFAULT
                text.color = 0xFF8FD0FF.toInt()
                canvas.drawText(formatPrecip(hour.precip), x, 172 * density, text)
            }
        }
    }
}

/** One day's low-to-high band on a track shared by the whole week. */
internal class TemperatureRange(
    context: Context,
    private val weekLow: Double,
    private val weekHigh: Double,
    private val low: Double,
    private val high: Double
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(canvas: Canvas) {
        val thickness = 6 * density
        val y = height / 2f
        val radius = thickness / 2
        paint.shader = null
        paint.color = 0x3D8C8CD8
        canvas.drawRoundRect(0f, y - radius, width.toFloat(), y + radius, radius, radius, paint)
        val span = (weekHigh - weekLow).coerceAtLeast(1.0)
        val start = ((low - weekLow) / span * width).toFloat()
        val end = ((high - weekLow) / span * width).toFloat().coerceAtLeast(start + thickness)
        paint.shader = LinearGradient(start, 0f, end, 0f, tempColor(low), tempColor(high), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(start, y - radius, end, y + radius, radius, radius, paint)
        paint.shader = null
    }

    companion object {
        private val evaluator = ArgbEvaluator()
        private val stops = listOf(
            -5.0 to 0xFF8FB8FF.toInt(),
            8.0 to 0xFF9BDFFF.toInt(),
            18.0 to 0xFF9BE8C0.toInt(),
            26.0 to 0xFFFFD98C.toInt(),
            34.0 to 0xFFFF9F7A.toInt()
        )

        fun tempColor(temp: Double): Int {
            if (temp <= stops.first().first) return stops.first().second
            for (index in 1 until stops.size) {
                val (t1, c1) = stops[index]
                if (temp <= t1) {
                    val (t0, c0) = stops[index - 1]
                    return evaluator.evaluate(((temp - t0) / (t1 - t0)).toFloat(), c0, c1) as Int
                }
            }
            return stops.last().second
        }
    }
}

internal fun formatPrecip(mm: Double): String =
    if (mm >= 10) "${mm.roundToInt()} mm" else String.format(java.util.Locale.getDefault(), "%.1f mm", mm)
