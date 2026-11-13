package com.caglaryildiz.alarm

import android.app.Activity
import android.content.Intent
import android.hardware.Camera
import android.os.Bundle
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.LinearLayout
import com.google.zxing.BinaryBitmap
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader

@Suppress("DEPRECATION")
class QrScannerActivity : AlarmScreen(), SurfaceHolder.Callback {
    private var camera: Camera? = null
    private val reader = QRCodeReader()
    private lateinit var surface: SurfaceView
    private lateinit var status: android.widget.TextView
    private var lastFrame = 0L
    private var found = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.hasExtra("ringToken")) setShowOnLockScreen()
        layout("QR kod okut")
        status = label("Kodu kameraya göster.")
        surface = SurfaceView(this)
        panel.addView(surface, LinearLayout.LayoutParams(-1, dp(330)))
        surface.holder.addCallback(this)
        button("Vazgeç", primary = false) { finish() }
    }

    private fun setShowOnLockScreen() {
        if (android.os.Build.VERSION.SDK_INT >= 27) setShowWhenLocked(true)
        else window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
    }

    override fun surfaceCreated(holder: SurfaceHolder) = Unit
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        closeCamera()
        try {
            val cameraInfo = Camera.CameraInfo()
            val cameraId = (0 until Camera.getNumberOfCameras()).first { id ->
                Camera.getCameraInfo(id, cameraInfo)
                cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK
            }
            val opened = Camera.open(cameraId)
            camera = opened
            val rotation = when (windowManager.defaultDisplay.rotation) {
                Surface.ROTATION_90 -> 90
                Surface.ROTATION_180 -> 180
                Surface.ROTATION_270 -> 270
                else -> 0
            }
            opened.setDisplayOrientation((cameraInfo.orientation - rotation + 360) % 360)
            opened.setPreviewDisplay(holder)
            val parameters = opened.parameters
            parameters.setPreviewFormat(android.graphics.ImageFormat.NV21)
            if (Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE in parameters.supportedFocusModes.orEmpty()) {
                parameters.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
            }
            opened.parameters = parameters
            val size = opened.parameters.previewSize
            opened.setPreviewCallback { data, _ ->
                val now = android.os.SystemClock.elapsedRealtime()
                if (found || data == null || now - lastFrame < 250) return@setPreviewCallback
                lastFrame = now
                val source = PlanarYUVLuminanceSource(data, size.width, size.height, 0, 0,
                    size.width, size.height, false)
                val result = try {
                    reader.decode(BinaryBitmap(HybridBinarizer(source))).text
                } catch (_: ReaderException) {
                    return@setPreviewCallback
                }
                found = true
                setResult(Activity.RESULT_OK, resultIntent().putExtra(CODE, result))
                finish()
            }
            opened.startPreview()
        } catch (error: Exception) {
            closeCamera()
            status.text = "Kamera açılamadı."
            setResult(RESULT_CAMERA_UNAVAILABLE, resultIntent())
            finish()
        }
    }

    private fun resultIntent() = Intent().putExtra("ringToken", intent.getStringExtra("ringToken"))

    override fun surfaceDestroyed(holder: SurfaceHolder) = closeCamera()
    override fun onStop() {
        closeCamera()
        super.onStop()
    }

    private fun closeCamera() {
        val opened = camera ?: return
        camera = null
        opened.setPreviewCallback(null)
        runCatching { opened.stopPreview() }
        opened.release()
    }

    companion object {
        internal const val CODE = "qrCode"
        internal const val RESULT_CAMERA_UNAVAILABLE = Activity.RESULT_FIRST_USER
    }
}
