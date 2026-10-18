package com.jarvis.assistant.ui.lighting

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import com.jarvis.assistant.JarvisApp

/**
 * DEV STYLE: shows the pastel rainbow edge-lighting over any screen - including
 * other apps - through a TYPE_APPLICATION_OVERLAY window (non touchable, non
 * focusable). The glow fades in, flows for a few seconds, fades out and the
 * service stops itself. No foreground notification needed: it only lives a few
 * seconds inside an already-running foreground-service process.
 */
class EdgeLightingService : Service() {

    companion object {
        const val ACTION_SHOW = "com.jarvis.assistant.EDGE_LIGHTING_SHOW"
        const val ACTION_STOP = "com.jarvis.assistant.EDGE_LIGHTING_STOP"
        private const val FLOW_DURATION_MS = 6000L
        private const val FADE_MS = 450L

        /** Launch the glow when the setting is on and overlay permission is granted. */
        fun show(context: Context) {
            val app = context.applicationContext
            if (app !is JarvisApp) return
            if (!app.preferences.isEdgeLightingEnabled) return
            if (!Settings.canDrawOverlays(app)) return
            try {
                app.startService(Intent(app, EdgeLightingService::class.java).setAction(ACTION_SHOW))
            } catch (_: Exception) {
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, EdgeLightingService::class.java))
            } catch (_: Exception) {
            }
        }
    }

    private var windowManager: WindowManager? = null
    private var lightingView: EdgeLightingView? = null
    private var added = false
    private var handler: Handler? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            removeAndStop()
            return START_NOT_STICKY
        }
        showGlow()
        return START_NOT_STICKY
    }

    private fun showGlow() {
        if (added) return
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER

        val view = EdgeLightingView(this).apply { alpha = 0f }
        lightingView = view
        try {
            wm.addView(view, params)
        } catch (_: Exception) {
            lightingView = null
            stopSelf()
            return
        }
        added = true
        windowManager = wm
        view.setRunning(true)
        view.animate().alpha(1f).setDuration(FADE_MS).start()

        handler = Handler(Looper.getMainLooper())
        handler?.postDelayed({
            val v = lightingView ?: return@postDelayed
            v.setRunning(false) // freeze the flow so the fade is smooth and battery-safe
            v.animate().alpha(0f).setDuration(FADE_MS).withEndAction { removeAndStop() }.start()
        }, FLOW_DURATION_MS)
    }

    private fun removeAndStop() {
        handler?.removeCallbacksAndMessages(null)
        handler = null
        try {
            if (added) {
                windowManager?.removeView(lightingView)
            }
        } catch (_: Exception) {
        }
        added = false
        lightingView = null
        windowManager = null
        stopSelf()
    }

    override fun onDestroy() {
        removeAndStop()
        super.onDestroy()
    }
}
