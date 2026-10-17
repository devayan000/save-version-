package com.jarvis.assistant.security

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.ImageFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import android.util.Size
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * DEV STYLE — On-Guard mode.
 * Watches the accelerometer: when the phone is suddenly lifted from a flat
 * resting position, JARVIS silently captures a front-camera selfie of whoever
 * lifted it, saves it, and announces the alert.
 */
class ThiefGuardService : Service(), SensorEventListener {

    companion object {
        private const val TAG = "ThiefGuard"
        private const val CHANNEL_ID = "thief_guard_channel"
        private const val NOTIFICATION_ID = 5150
        private const val TRIGGER_COOLDOWN_MS = 45_000L
    }

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var flatAt: Long = 0L
    private var lastTriggerAt: Long = 0L
    private var cameraThread: HandlerThread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        Log.i(TAG, "On-Guard active")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        runCatching { sensorManager.unregisterListener(this) }
        cameraThread?.quitSafely()
        cameraThread = null
        super.onDestroy()
    }

    private fun startAsForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "On Guard", NotificationManager.IMPORTANCE_LOW))
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("JARVIS On-Guard")
            .setContentText("Watching for unauthorized phone lifts")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        ) {
            runCatching {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
                return
            }
        }
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitude = sqrt(x * x + y * y + z * z)
        val now = System.currentTimeMillis()

        val isFlat = abs(z) > 8.5f && magnitude in 8.0f..11.0f
        if (isFlat) {
            flatAt = now
            return
        }
        // Lifted within 4 seconds of resting flat, with a strong movement signature
        val liftedRecently = now - flatAt in 1..4_000L
        val moving = magnitude > 12.5f || magnitude < 5.0f || z < 6.5f
        if (liftedRecently && moving && now - lastTriggerAt > TRIGGER_COOLDOWN_MS) {
            lastTriggerAt = now
            flatAt = 0L
            onLiftDetected()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun onLiftDetected() {
        Log.w(TAG, "Unauthorized lift detected - capturing silently")
        val prefs = (application as? com.jarvis.assistant.JarvisApp)?.preferences
        if (prefs != null) {
            prefs.thiefGuardUnseenCaptures = prefs.thiefGuardUnseenCaptures + 1
        }
        captureSelfie()
    }

    private fun captureSelfie() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return
        try {
            val cm = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val frontId = cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
            } ?: return
            val mainHandler = Handler(Looper.getMainLooper())
            val bgThread = HandlerThread("thiefCam").also { it.start() }
            cameraThread = bgThread
            val bgHandler = Handler(bgThread.looper)
            cm.openCamera(frontId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    try {
                        val sizes = cm.getCameraCharacteristics(frontId)
                            .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
                            .getOutputSizes(ImageFormat.JPEG)
                        val size: Size = sizes.maxByOrNull { it.width * it.height } ?: Size(1080, 1920)
                        val reader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 1)
                        reader.setOnImageAvailableListener({ r ->
                            val img = r.acquireLatestImage()
                            if (img != null) {
                                try {
                                    val buf = img.planes[0].buffer
                                    val bytes = ByteArray(buf.remaining())
                                    buf.get(bytes)
                                    val saved = savePhoto(bytes)
                                    Log.i(TAG, "Intruder selfie saved: " + saved)
                                } finally {
                                    img.close()
                                }
                            }
                            runCatching { camera.close() }
                            bgThread.quitSafely()
                        }, bgHandler)
                        camera.createCaptureSession(listOf(reader.surface), object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                try {
                                    val req = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                                        addTarget(reader.surface)
                                        set(CaptureRequest.JPEG_ORIENTATION, 270)
                                    }.build()
                                    session.capture(req, null, bgHandler)
                                } catch (_: Exception) {
                                    runCatching { camera.close() }
                                    bgThread.quitSafely()
                                }
                            }

                            override fun onConfigureFailed(session: CameraCaptureSession) {
                                runCatching { camera.close() }
                                bgThread.quitSafely()
                            }
                        }, bgHandler)
                    } catch (_: Exception) {
                        runCatching { camera.close() }
                        bgThread.quitSafely()
                    }
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    bgThread.quitSafely()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    bgThread.quitSafely()
                }
            }, mainHandler)
        } catch (_: Exception) {
        }
    }

    private fun savePhoto(bytes: ByteArray): String {
        val dir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "JarvisGuard")
        if (!dir.exists()) dir.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "intruder_" + stamp + ".jpg")
        file.outputStream().use { it.write(bytes) }
        return file.absolutePath
    }
}
