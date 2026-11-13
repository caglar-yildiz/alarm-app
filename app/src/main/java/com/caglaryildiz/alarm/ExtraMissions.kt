package com.caglaryildiz.alarm

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager

internal class ExtraMission(val task: AlarmTask, val qrCode: String) {
    val sequence = List(4) { (1..4).random() }
    val phrase = listOf("Bugün yeni bir gün", "Perdeyi aç ve güne başla", "Bir bardak su iç").random()
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

        fun hasAccelerometer(context: Context) =
            context.getSystemService(SensorManager::class.java).getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
    }
}
