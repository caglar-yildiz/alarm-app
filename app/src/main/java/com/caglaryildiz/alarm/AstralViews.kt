package com.caglaryildiz.alarm

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.View
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

internal class Starfield : Drawable() {
    private data class Star(val x: Float, val y: Float, val size: Float)
    private val stars = Random(87).run { List(94) { Star(nextFloat(), nextFloat(), nextFloat()) } }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun draw(canvas: Canvas) {
        val width = bounds.width().toFloat()
        val height = bounds.height().toFloat()
        if (width <= 0f || height <= 0f) return
        canvas.drawColor(0xFF0B1021.toInt())
        paint.shader = RadialGradient(width * .94f, height * .72f, width * .86f,
            intArrayOf(0x783E1B66, 0x003E1B66), null, Shader.TileMode.CLAMP)
        canvas.drawRect(bounds, paint)
        paint.shader = RadialGradient(width * .13f, height * .12f, width * .61f,
            intArrayOf(0x68302C76, 0x00302C76), null, Shader.TileMode.CLAMP)
        canvas.drawRect(bounds, paint)
        paint.shader = null
        for (star in stars) {
            paint.color = if (star.size > .9f) 0xA7C6C6FF.toInt() else 0x49C6C6FF
            canvas.drawCircle(star.x * width, star.y * height, (1f + star.size) * width / 530f, paint)
        }
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    @Deprecated("Required by Drawable")
    override fun getOpacity() = PixelFormat.OPAQUE
}

internal class AstralPlanet(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(canvas: Canvas) {
        val x = width / 2f
        val y = height / 2f
        val radius = minOf(x, y) * .51f
        if (radius <= 0f) return
        paint.shader = RadialGradient(x + radius * .42f, y - radius * .48f, radius * 1.6f,
            intArrayOf(0xFFB593FF.toInt(), 0xFF514291.toInt(), 0xFF131730.toInt()),
            floatArrayOf(0f, .43f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(x, y, radius, paint)
        paint.shader = null
        canvas.save()
        canvas.rotate(-25f, x, y)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = radius * .047f
        paint.color = 0xB7C3BBFF.toInt()
        canvas.drawOval(x - radius * 1.76f, y - radius * .34f, x + radius * 1.76f, y + radius * .34f, paint)
        paint.style = Paint.Style.FILL
        canvas.restore()
    }
}

internal class OrbitTimeView(
    context: Context,
    private val onTimeChanged: (LocalTime) -> Unit
) : View(context) {
    var time: LocalTime = LocalTime.MIDNIGHT
        set(value) {
            field = value
            updateDescription()
            invalidate()
        }
    var selectingMinutes = false
        set(value) {
            field = value
            updateDescription()
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val density = resources.displayMetrics.density
    private val centerX get() = width / 2f
    private val centerY get() = height / 2f
    private val outerRadius get() = minOf(width, height) * .40f
    private val innerRadius get() = outerRadius * .66f

    init {
        isFocusable = true
        updateDescription()
    }

    private fun updateDescription() {
        contentDescription = if (selectingMinutes) "Dakika: ${time.minute}" else "Saat: ${time.hour}"
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.isScrollable = true
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        return when (action) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> adjustTime(1)
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> adjustTime(-1)
            else -> super.performAccessibilityAction(action, arguments)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_RIGHT -> adjustTime(1)
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT -> adjustTime(-1)
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun adjustTime(delta: Int): Boolean {
        time = if (selectingMinutes) time.withMinute(Math.floorMod(time.minute + delta, 60))
            else time.plusHours(delta.toLong())
        onTimeChanged(time)
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val outer = outerRadius
        if (outer <= 0f) return
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = density
        paint.color = 0x70626EBC
        canvas.drawCircle(centerX, centerY, outer, paint)
        canvas.drawCircle(centerX, centerY, innerRadius, paint)
        canvas.drawCircle(centerX, centerY, outer * 1.13f, paint)
        paint.style = Paint.Style.FILL

        val selected = if (selectingMinutes) time.minute else time.hour % 12
        val count = if (selectingMinutes) 60 else 12
        val selectedRadius = if (!selectingMinutes && time.hour >= 12) innerRadius else outer
        val (x, y) = point(selected, count, selectedRadius)
        paint.color = 0x605CCFFF
        canvas.drawLine(centerX, centerY, x, y, paint)
        paint.shader = RadialGradient(x, y, 24f * density,
            intArrayOf(0xD698A5FF.toInt(), 0x0098A5FF), null, Shader.TileMode.CLAMP)
        canvas.drawCircle(x, y, 24f * density, paint)
        paint.shader = null
        paint.color = 0xFF6066AB.toInt()
        canvas.drawCircle(x, y, 13f * density, paint)

        if (selectingMinutes) {
            for (minute in 0 until 60) {
                if (minute % 5 == 0) {
                    number(canvas, "%02d".format(java.util.Locale.ROOT, minute), minute, 60, outer)
                } else {
                    val (tickX, tickY) = point(minute, 60, outer)
                    paint.color = if (minute == selected) 0xFFFFFFFF.toInt() else 0x876CA7F1.toInt()
                    canvas.drawCircle(tickX, tickY, if (minute == selected) 3f * density else density, paint)
                }
            }
        } else {
            for (hour in 0 until 12) {
                number(canvas, hour.toString(), hour, 12, outer)
                number(canvas, (hour + 12).toString(), hour, 12, innerRadius)
            }
        }
        paint.color = 0xFFD6D9FF.toInt()
        canvas.drawCircle(centerX, centerY, 3f * density, paint)
    }

    private fun number(canvas: Canvas, text: String, index: Int, count: Int, radius: Float) {
        val (x, y) = point(index, count, radius)
        paint.textSize = 13f * density
        paint.color = 0xFFF2F2FF.toInt()
        canvas.drawText(text, x, y - (paint.ascent() + paint.descent()) / 2f, paint)
    }

    private fun point(index: Int, count: Int, radius: Float): Pair<Float, Float> {
        val angle = 2 * PI * index / count
        return (centerX + radius * sin(angle)).toFloat() to (centerY - radius * cos(angle)).toFloat()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val distance = hypot(event.x - centerX, event.y - centerY)
            val nearOuter = abs(distance - outerRadius) <= 26 * density
            val nearInner = !selectingMinutes && abs(distance - innerRadius) <= 22 * density
            return nearOuter || nearInner
        }
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            val x = event.x - centerX
            val y = event.y - centerY
            val distance = hypot(x, y)
            if (distance < innerRadius * .65f || distance > outerRadius + 30 * density) return true
            val turns = atan2(x, -y) / (2 * PI)
            time = if (selectingMinutes) {
                time.withMinute(Math.floorMod((turns * 60).roundToInt(), 60))
            } else {
                val hour = Math.floorMod((turns * 12).roundToInt(), 12)
                val useInner = distance < (innerRadius + outerRadius) / 2
                time.withHour(hour + if (useInner) 12 else 0)
            }
            performClick()
            onTimeChanged(time)
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
