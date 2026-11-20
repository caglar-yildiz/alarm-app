package com.caglaryildiz.alarm

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager

internal class ExtraMission(val task: AlarmTask, val qrCode: String, val phrase: String, val stepGoal: Int = StepGoal.DEFAULT) {
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

    /** One step from the step detector. Progress only grows while the alarm screen is open and walking. */
    fun step() {
        if (task == AlarmTask.STEPS && !complete) complete = ++progress >= stepGoal
    }

    fun scan(text: String): Boolean {
        complete = qrCode.isNotBlank() && text == qrCode
        return complete
    }

    companion object {
        const val SHAKE_COUNT = 12

        fun create(context: Context, task: AlarmTask, qrCode: String, stepGoal: Int = StepGoal.DEFAULT) =
            ExtraMission(task, qrCode, context.resources.getStringArray(R.array.typing_phrases).random(), StepGoal.coerce(stepGoal))

        fun hasStepDetector(context: Context) =
            context.getSystemService(SensorManager::class.java).getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) != null

        /** Android 10+ asks the user before an app may count steps. */
        fun canCountSteps(context: Context) = hasStepDetector(context) &&
            (android.os.Build.VERSION.SDK_INT < 29 || context.checkSelfPermission(android.Manifest.permission.ACTIVITY_RECOGNITION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED)

        fun hasAccelerometer(context: Context) =
            context.getSystemService(SensorManager::class.java).getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
    }
}
