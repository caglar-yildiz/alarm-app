package com.caglaryildiz.alarm

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout

private val volumeKeys = setOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE)

class MissionPreviewActivity : RingActivity()

open class RingActivity : AlarmScreen() {
    private val isPreview get() = this is MissionPreviewActivity
    private var previewRing: Ring? = null
    private var previewTone: android.media.MediaPlayer? = null
    private val soundTimeout = android.os.Handler(android.os.Looper.getMainLooper())
    private fun currentRing() = if (isPreview) previewRing else RingService.active
    private val sensorManager by lazy { getSystemService(android.hardware.SensorManager::class.java) }
    private var shakeListener: android.hardware.SensorEventListener? = null
    private val memoryHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var failedQrToken: String? = null
    /** Set while the QR scanner, opened from this screen, covers it; leaving for it is not an escape. */
    private var scannerOpen = false
    /** Set once this screen turned the alarm off, so the briefing opens when the service confirms it. */
    private var dismissSent = false
    private val onRingChanged: () -> Unit = {
        if (isPreview && RingService.active != null) {
            stopPreviewSound()
            finish()
        } else render()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isPreview) {
            val task = AlarmTask.entries.firstOrNull { it.name == intent.getStringExtra("task") } ?: AlarmTask.NONE
            previewRing = Ring("preview",
                if (task == AlarmTask.MATH) MathMission(MathSettings.from(intent.getStringExtra("mathDifficulty"), intent.getIntExtra("mathCount", 1))) else null,
                if (task != AlarmTask.MATH && task != AlarmTask.NONE) ExtraMission.create(this, task, intent.getStringExtra("qrCode").orEmpty(),
                    intent.getIntExtra("stepGoal", StepGoal.DEFAULT)) else null,
                intent.getStringExtra("label").orEmpty(), intent.getStringExtra("tone")?.let(Uri::parse))
            return
        }
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onStart() {
        super.onStart()
        scannerOpen = false
        if (!isPreview) RingService.screenVisible = true
        RingService.listeners.add(onRingChanged)
        if (isPreview && RingService.active == null) startPreviewSound()
        render()
    }

    override fun onStop() {
        RingService.listeners.remove(onRingChanged)
        clearMissionCallbacks()
        stopPreviewSound()
        if (!isPreview) {
            RingService.screenVisible = false
            // Going home, to Settings or turning the screen off while the alarm rings: bring the screen back.
            val ring = RingService.active
            if (ring != null && !scannerOpen && !isChangingConfigurations) {
                runCatching {
                    startService(Intent(this, RingService::class.java).setAction(RingService.SCREEN_LEFT)
                        .putExtra("token", ring.token))
                }
            }
        }
        super.onStop()
    }

    private fun startPreviewSound() {
        stopPreviewSound()
        previewTone = playAlarmTone(this, previewRing?.tone)
        if (previewTone != null) {
            soundTimeout.postDelayed(::stopPreviewSound, 30_000)
        } else {
            android.widget.Toast.makeText(this, getString(R.string.tone_play_failed), android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopPreviewSound() {
        soundTimeout.removeCallbacksAndMessages(null)
        previewTone?.release()
        previewTone = null
    }

    private fun render() {
        clearMissionCallbacks()
        val ring = currentRing()
        if (ring == null) {
            if (!isPreview && dismissSent && MorningBriefing(this).enabled) {
                runCatching { startActivity(Intent(this, BriefingActivity::class.java)) }
            }
            finish()
            return
        }
        layout(getString(R.string.app_name))
        val header = card()
        label(getString(if (isPreview) R.string.preview else R.string.alarm_ringing), 22f, header, bold = true).gravity = Gravity.CENTER
        if (ring.label.isNotBlank()) label(ring.label, 16f, header, color = mutedColor).gravity = Gravity.CENTER
        if (ring.resumed) label(getString(R.string.ring_resumed), 14f, header, color = mutedColor).gravity = Gravity.CENTER
        timeLabel(ring.startedAt.format(clockFormat), 60f, header).gravity = Gravity.CENTER
        header.addView(AstralPlanet(this), LinearLayout.LayoutParams(-1, dp(112)))
        if (isPreview) {
            label(getString(R.string.preview_note), 14f, header, color = mutedColor)
            button(getString(R.string.preview_close), header, primary = false) {
                stopPreviewSound()
                finish()
            }
        }
        val controls = card()
        val mission = ring.math
        val extra = ring.extra
        if (extra != null) {
            showExtraMission(ring, extra, controls)
        } else if (mission == null) {
            button(getString(R.string.dismiss_alarm), controls) { send(RingService.DISMISS, ring) }
        } else {
            val progress = label("", 15f, controls, color = mutedColor)
            val question = label("", 32f, controls, bold = true).apply { gravity = Gravity.CENTER }
            val answer = EditText(this).apply {
                hint = getString(R.string.math_answer)
                inputType = InputType.TYPE_CLASS_NUMBER
                styleInput(this)
                controls.addView(this)
            }
            fun showQuestion() {
                progress.text = getString(R.string.math_progress, mission.current, mission.total)
                question.text = mission.question?.let { "${it.expression} = ?" }
                answer.text.clear()
                answer.error = null
            }
            showQuestion()
            button(getString(R.string.math_check), controls) {
                when (mission.submit(answer.text.toString().toIntOrNull())) {
                    MathResult.INCORRECT -> answer.error = getString(R.string.try_again)
                    MathResult.NEXT -> showQuestion()
                    MathResult.COMPLETE -> send(RingService.DISMISS, ring)
                }
            }
        }
        if (!isPreview && Alarms(this).canSchedule()) button(getString(R.string.snooze_5), controls, primary = false) { send(RingService.SNOOZE, ring) }
    }

    private fun shapeName(shape: Int) = getString(ShapeMission.nameRes[shape])

    /** Volume keys must not turn a ringing alarm down; the service also guards the stream. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!isPreview && keyCode in volumeKeys) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (!isPreview && keyCode in volumeKeys) return true
        return super.onKeyUp(keyCode, event)
    }

    private fun clearMissionCallbacks() {
        memoryHandler.removeCallbacksAndMessages(null)
        shakeListener?.let { sensorManager.unregisterListener(it) }
        shakeListener = null
    }

    private fun showExtraMission(ring: Ring, mission: ExtraMission, controls: LinearLayout) {
        label(getString(mission.task.labelRes), 20f, controls, bold = true)
        when (mission.task) {
            AlarmTask.MEMORY -> {
                label(getString(R.string.memory_instructions), 17f, controls)
                val display = label("•", 64f, controls, bold = true).apply {
                    gravity = Gravity.CENTER
                    minHeight = dp(104)
                }
                val status = label(getString(R.string.memory_ready), 16f, controls)
                val row = LinearLayout(this)
                controls.addView(row)
                val keys = (1..4).map { number ->
                    button(number.toString(), row, primary = false) {
                        val correct = mission.tap(number)
                        status.text = if (correct) "${mission.progress} / ${mission.sequence.size}"
                            else getString(R.string.memory_wrong)
                        if (mission.complete) send(RingService.DISMISS, ring)
                    }.apply {
                        textSize = 30f
                        minimumHeight = dp(64)
                        isEnabled = false
                        layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                    }
                }
                button(getString(R.string.memory_show), controls, primary = false) {
                    memoryHandler.removeCallbacksAndMessages(null)
                    mission.resetSequence()
                    status.text = getString(R.string.memory_remember)
                    keys.forEach { it.isEnabled = false }
                    for ((index, number) in mission.sequence.withIndex()) {
                        memoryHandler.postDelayed({ display.text = number.toString() }, index * 800L)
                        memoryHandler.postDelayed({ display.text = "•" }, index * 800L + 550L)
                    }
                    memoryHandler.postDelayed({
                        display.text = "•"
                        status.text = getString(R.string.memory_repeat, mission.progress, mission.sequence.size)
                        keys.forEach { it.isEnabled = true }
                    }, mission.sequence.size * 800L)
                }
            }
            AlarmTask.SHAPES -> {
                val shapes = mission.shapes
                label(getString(R.string.shapes_instructions), 16f, controls)
                label(shapes.targets.joinToString("  ") { ShapeMission.symbols[it] }, 36f, controls).apply {
                    gravity = Gravity.CENTER
                    contentDescription = shapes.targets.joinToString(", ") { shapeName(it) }
                }
                val status = label(getString(R.string.shapes_find, shapes.progress, shapeName(shapes.targets[shapes.progress.coerceAtMost(3)])), 18f, controls)
                repeat(4) { rowIndex ->
                    val row = LinearLayout(this).apply { isBaselineAligned = false }
                    controls.addView(row)
                    repeat(4) { columnIndex ->
                        val index = rowIndex * 4 + columnIndex
                        val tile = button(ShapeMission.symbols[shapes.board[index]], row, primary = false) { }
                        tile.apply {
                            textSize = 32f
                            minWidth = 0
                            minimumHeight = dp(60)
                            contentDescription = getString(R.string.shapes_cell, rowIndex + 1, columnIndex + 1, shapeName(shapes.board[index]))
                            isEnabled = index !in shapes.selected
                            alpha = if (isEnabled) 1f else .35f
                            layoutParams = LinearLayout.LayoutParams(0, dp(64), 1f).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) }
                            setOnClickListener {
                                if (mission.pickShape(index)) {
                                    isEnabled = false
                                    alpha = .35f
                                    if (mission.complete) send(RingService.DISMISS, ring)
                                    else status.text = getString(R.string.shapes_find, shapes.progress, shapeName(shapes.targets[shapes.progress]))
                                } else status.text = getString(R.string.shapes_next, shapeName(shapes.targets[shapes.progress]))
                            }
                        }
                    }
                }
            }
            AlarmTask.TYPING -> {
                label(mission.phrase, 24f, controls)
                val input = EditText(this).apply {
                    hint = getString(R.string.typing_hint)
                    setSingleLine()
                    styleInput(this)
                    controls.addView(this)
                }
                button(getString(R.string.typing_check), controls) {
                    if (mission.type(input.text.toString())) send(RingService.DISMISS, ring)
                    else input.error = getString(R.string.typing_wrong)
                }
            }
            AlarmTask.SHAKE -> showShakeMission(ring, mission, controls)
            AlarmTask.STEPS -> showStepMission(ring, mission, controls)
            AlarmTask.QR -> {
                label(getString(R.string.qr_scan_instructions), parent = controls)
                button(getString(R.string.qr_open_camera), controls) {
                    scannerOpen = true
                    @Suppress("DEPRECATION")
                    startActivityForResult(Intent(this, QrScannerActivity::class.java).putExtra("ringToken", ring.token), 22)
                }
                if (failedQrToken == ring.token) {
                    label(getString(R.string.camera_unavailable), parent = controls)
                    button(getString(R.string.math_fallback), controls, primary = false) { useMathFallback(ring) }
                }
            }
            else -> error("Unsupported mission: ${mission.task}")
        }
    }

    private fun showShakeMission(ring: Ring, mission: ExtraMission, controls: LinearLayout) {
        val progress = label("${mission.progress} / ${ExtraMission.SHAKE_COUNT}", 24f, controls)
        val sensor = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
        val listener = object : android.hardware.SensorEventListener {
            private var lastShake = 0L
            private var aboveThreshold = false

            override fun onSensorChanged(event: android.hardware.SensorEvent) {
                val (x, y, z) = event.values
                val high = x * x + y * y + z * z >= 23f * 23f
                val now = android.os.SystemClock.elapsedRealtime()
                if (high && !aboveThreshold && now - lastShake >= 300 && !mission.complete) {
                    lastShake = now
                    mission.shake()
                    progress.text = "${mission.progress} / ${ExtraMission.SHAKE_COUNT}"
                    if (mission.complete) send(RingService.DISMISS, ring)
                }
                aboveThreshold = high
            }

            override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) = Unit
        }
        if (sensor != null && sensorManager.registerListener(listener, sensor,
                android.hardware.SensorManager.SENSOR_DELAY_GAME)) {
            shakeListener = listener
        } else {
            button(getString(R.string.math_fallback), controls, primary = false) { useMathFallback(ring) }
        }
    }

    /** Walk to turn the alarm off: the step detector only counts real steps, not shaking in bed. */
    private fun showStepMission(ring: Ring, mission: ExtraMission, controls: LinearLayout) {
        label(getString(R.string.steps_instructions), 16f, controls)
        val progress = timeLabel(getString(R.string.steps_progress, mission.progress, mission.stepGoal), 40f, controls).apply {
            gravity = Gravity.CENTER
            accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        val sensor = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_STEP_DETECTOR)
        val listener = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(event: android.hardware.SensorEvent) {
                if (mission.complete) return
                mission.step()
                progress.text = getString(R.string.steps_progress, mission.progress, mission.stepGoal)
                if (mission.complete) send(RingService.DISMISS, ring)
            }

            override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) = Unit
        }
        if (sensor != null && ExtraMission.canCountSteps(this) &&
            sensorManager.registerListener(listener, sensor, android.hardware.SensorManager.SENSOR_DELAY_UI, 0)) {
            shakeListener = listener
        } else {
            button(getString(R.string.math_fallback), controls, primary = false) { useMathFallback(ring) }
        }
    }

    private fun useMathFallback(ring: Ring) {
        ring.extra = null
        ring.math = MathMission(MathSettings())
        render()
    }

    @Deprecated("Receives the locally scanned QR code.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 22) return
        val ring = currentRing() ?: return
        val mission = ring.extra?.takeIf { it.task == AlarmTask.QR } ?: return
        if (data?.getStringExtra("ringToken") != ring.token) return
        when (resultCode) {
            QrScannerActivity.RESULT_CAMERA_UNAVAILABLE -> {
                failedQrToken = ring.token
                if (hasWindowFocus()) render()
            }
            RESULT_OK -> {
                if (mission.scan(data.getStringExtra(QrScannerActivity.CODE).orEmpty())) {
                    send(RingService.DISMISS, ring)
                } else {
                    android.widget.Toast.makeText(this, getString(R.string.qr_wrong_code),
                        android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun send(action: String, ring: Ring) {
        if (isPreview) {
            stopPreviewSound()
            android.widget.Toast.makeText(this, getString(R.string.preview_done), android.widget.Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        // The morning briefing follows alarms, not reminders that ring.
        if (action == RingService.DISMISS) dismissSent = !ring.id.startsWith(Reminders.REMINDER_PREFIX)
        startService(Intent(this, RingService::class.java).setAction(action).putExtra("token", ring.token))
    }
}
