package com.jarvis.assistant.vision

import android.app.*
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** One projection consent, one display; system revocation and Stop both release all resources. */
class ScreenToolsService : Service() {
    companion object {
        const val STOP = "com.jarvis.assistant.STOP_SCREEN_TOOLS"
        @Volatile var running = false
            private set
        private val mutableStatus = MutableStateFlow("Screenshot and screen recording")
        val status = mutableStatus.asStateFlow()
    }
    private val handler = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var recorder: MediaRecorder? = null
    private var recordingStarted = false
    private var temporary: File? = null
    private var finishing = false
    private val callback = object : MediaProjection.Callback() { override fun onStop() { finishCapture(true) } }
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { finishCapture(true); return START_NOT_STICKY }
        if (running) return START_NOT_STICKY
        val request = intent ?: run { stopSelf(); return START_NOT_STICKY }
        val data = request.getParcelableExtra<Intent>("data")
        if (data == null || request.getIntExtra("resultCode", 0) != Activity.RESULT_OK) { stopSelf(); return START_NOT_STICKY }
        finishing = false
        running = true
        try {
            val notifications = getSystemService(NotificationManager::class.java)
            notifications.createNotificationChannel(NotificationChannel("jarvis_capture", "Jarvis screen capture", NotificationManager.IMPORTANCE_LOW))
            val stop = PendingIntent.getService(this, 801, Intent(this, ScreenToolsService::class.java).setAction(STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val open = PendingIntent.getActivity(this, 802, Intent(this, ScreenToolsActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = Notification.Builder(this, "jarvis_capture").setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle("Jarvis screen capture").setContentText("Screen capture is active. Tap Stop to save.")
                .setContentIntent(open).setOngoing(true).addAction(android.R.drawable.ic_media_pause, "Stop and save", stop).build()
            if (Build.VERSION.SDK_INT >= 29) startForeground(801, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            else startForeground(801, notification)
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(Activity.RESULT_OK, data)
            projection!!.registerCallback(callback, handler)
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
            handler.postDelayed({ if (!finishing) {
                try { if (request.getStringExtra("mode") == "record") startRecording(metrics) else takeScreenshot(metrics) }
                catch (e: Exception) { fail(e) }
            } }, 700)
        } catch (e: Exception) { fail(e) }
        return START_NOT_STICKY
    }
    private fun startRecording(metrics: DisplayMetrics) {
        temporary = File(cacheDir, "jarvis-recording-" + System.currentTimeMillis() + ".mp4")
        @Suppress("DEPRECATION")
        val rec = MediaRecorder()
        recorder = rec
        rec.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        rec.setOutputFile(temporary!!.absolutePath)
        rec.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        val scale = (1920f / maxOf(metrics.widthPixels, metrics.heightPixels)).coerceAtMost(1f)
        val width = (metrics.widthPixels * scale).toInt() and -2
        val height = (metrics.heightPixels * scale).toInt() and -2
        rec.setVideoSize(width, height)
        rec.setVideoFrameRate(30)
        rec.setVideoEncodingBitRate(5_000_000)
        rec.setMaxDuration(600_000)
        rec.setOnInfoListener { _, what, _ -> if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) finishCapture(true) }
        rec.setOnErrorListener { _, _, _ -> fail(IllegalStateException("Android stopped recording.")) }
        rec.prepare()
        display = projection!!.createVirtualDisplay("JarvisRecording", width, height, metrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, rec.surface, null, handler)
        rec.start()
        recordingStarted = true
        mutableStatus.value = "Recording screen. Stop from Jarvis or the notification to save."
    }
    private fun takeScreenshot(metrics: DisplayMetrics) {
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        reader = imageReader
        var blackFrames = 0
        mutableStatus.value = "Taking screenshot..."
        imageReader.setOnImageAvailableListener({ source ->
            if (finishing) return@setOnImageAvailableListener
            val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val plane = image.planes[0]
                val paddedWidth = plane.rowStride / plane.pixelStride
                val padded = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
                try {
                    plane.buffer.rewind()
                    padded.copyPixelsFromBuffer(plane.buffer)
                    val bitmap = Bitmap.createBitmap(padded, 0, 0, width, height)
                    // Black-frame guard: secure/protected content, a locked phone, or an
                    // off screen mirrors as pure black. Detach + reattach the surface to
                    // pull a fresh frame; give up with a clear reason after a few tries.
                    if (isAllBlack(bitmap)) {
                        bitmap.recycle()
                        if (blackFrames >= 4) {
                            fail(IllegalStateException("Screen is coming out black - the screen may be off, locked, or showing protected content."))
                            return@setOnImageAvailableListener
                        }
                        blackFrames++
                        display?.setSurface(null)
                        handler.postDelayed({
                            if (!finishing) {
                                try { display?.setSurface(imageReader.surface) } catch (_: Exception) {}
                            }
                        }, 600)
                        return@setOnImageAvailableListener
                    }
                    try {
                        val file = File(cacheDir, "jarvis-screenshot-" + System.currentTimeMillis() + ".png")
                        temporary = file
                        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                        val destination = save(file, false)
                        mutableStatus.value = "Screenshot saved: " + destination
                        Toast.makeText(this, mutableStatus.value, Toast.LENGTH_LONG).show()
                    } finally { if (bitmap !== padded) bitmap.recycle() }
                } finally { padded.recycle() }
                finishCapture(false)
            } catch (e: Exception) { fail(e) }
            finally { image.close() }
        }, handler)
        display = projection!!.createVirtualDisplay("JarvisScreenshot", width, height, metrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader.surface, null, handler)
        handler.postDelayed({ if (!finishing) fail(IllegalStateException("No screen frame arrived. Please retry.")) }, 8000)
    }
    private fun save(file: File, video: Boolean): String {
        check(file.exists() && file.length() > 0) { "No captured data to save." }
        val directory = if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
        if (Build.VERSION.SDK_INT < 29) {
            val destination = File(getExternalFilesDir(directory), file.name)
            file.copyTo(destination, overwrite = false)
            return destination.absolutePath
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, if (video) "video/mp4" else "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, directory + "/Jarvis")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val uri = checkNotNull(contentResolver.insert(collection, values)) { "Could not create gallery item." }
        try {
            checkNotNull(contentResolver.openOutputStream(uri)).use { output -> file.inputStream().use { it.copyTo(output) } }
            values.clear(); values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
        } catch (e: Exception) { contentResolver.delete(uri, null, null); throw e }
        return directory + "/Jarvis/" + file.name
    }
    // DEV STYLE: true when every sampled pixel is (near) pure black
    private fun isAllBlack(bitmap: Bitmap): Boolean {
        val stepX = maxOf(1, bitmap.width / 64)
        val stepY = maxOf(1, bitmap.height / 48)
        for (y in 0 until bitmap.height step stepY) {
            for (x in 0 until bitmap.width step stepX) {
                val p = bitmap.getPixel(x, y)
                if ((p and 0x00FFFFFF) > 0x00010101) return false
            }
        }
        return true
    }

    private fun fail(error: Exception) {
        mutableStatus.value = "Capture failed: " + (error.message ?: "Try again.")
        Toast.makeText(this, mutableStatus.value, Toast.LENGTH_LONG).show()
        finishCapture(false)
    }
    private fun finishCapture(saveRecording: Boolean) {
        if (finishing) return
        finishing = true
        handler.removeCallbacksAndMessages(null)
        try {
            if (recordingStarted) recorder?.stop()
            if (saveRecording && recordingStarted) {
                val destination = save(checkNotNull(temporary), true)
                mutableStatus.value = "Recording saved: " + destination
                Toast.makeText(this, mutableStatus.value, Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            mutableStatus.value = "Recording could not be saved: " + e.message
            Toast.makeText(this, mutableStatus.value, Toast.LENGTH_LONG).show()
        } finally {
            recordingStarted = false
            recorder?.release(); recorder = null
            display?.release(); display = null
            reader?.close(); reader = null
            projection?.unregisterCallback(callback)
            projection?.stop(); projection = null
            temporary?.delete(); temporary = null
            running = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }
    override fun onDestroy() { finishCapture(true); super.onDestroy() }
}
