package com.caglaryildiz.alarm

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager

internal class ExtraMission(val task: AlarmTask, val qrCode: String, val phrase: String) {
    val shapes = ShapeMission()
    val sequence = List(4) { (1..4).random() }
    var progress = 0
        private set
    var complete = false
        private set

    fun tap(number: Int): Boolean {
        if (complete) return true
        if (sequence[progress] != number) {
            progress = 0
            return false
        }
        complete = ++progress == sequence.size
        return true
    }

    fun resetSequence() { if (!complete) progress = 0 }

    fun pickShape(index: Int): Boolean {
        val correct = shapes.pick(index)
        complete = shapes.complete
        return correct
    }

    fun type(text: String): Boolean {
        complete = text.trim() == phrase
        return complete
    }

    fun shake() {
        if (!complete) complete = ++progress == SHAKE_COUNT
    }

    fun scan(text: String): Boolean {
        complete = qrCode.isNotBlank() && text == qrCode
        return complete
    }

    companion object {
        const val SHAKE_COUNT = 12

        fun create(context: Context, task: AlarmTask, qrCode: String) = ExtraMission(task, qrCode,
            context.resources.getStringArray(R.array.typing_phrases).random())

        fun hasAccelerometer(context: Context) =
            context.getSystemService(SensorManager::class.java).getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
    }
}
