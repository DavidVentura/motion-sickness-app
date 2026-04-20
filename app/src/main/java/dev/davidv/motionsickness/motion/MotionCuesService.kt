// SPDX-FileCopyrightText: 2026 David Ventura
// SPDX-License-Identifier: GPL-3.0-only

package dev.davidv.motionsickness.motion

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import dev.davidv.motionsickness.MainActivity
import dev.davidv.motionsickness.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Hosts the motion-cue overlay for as long as the user wants it visible. Lives as a foreground
 * service because we need the overlay and sensor stream to survive when the launcher activity
 * is no longer in the foreground (the whole point is to draw *over* other apps).
 */
class MotionCuesService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: CueOverlayView? = null
    private lateinit var motionEstimator: MotionEstimator

    private val scope = CoroutineScope(Dispatchers.Main)
    private var collectJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        motionEstimator = MotionEstimator(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelfCleanly()
            return START_NOT_STICKY
        }

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundWithNotification()
        attachOverlay()
        motionEstimator.start()
        collectJob?.cancel()
        collectJob = scope.launch {
            motionEstimator.motion.collectLatest { overlayView?.setMotion(it) }
        }
        _isRunning.value = true
        return START_STICKY
    }

    private fun attachOverlay() {
        if (overlayView != null) return
        val view = CueOverlayView(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // Android 12+ (API 31) only tags touches as "obscured" — which security-sensitive
            // views reject — if the overlay window's alpha is above ~0.8. Setting it just below
            // that threshold lets taps fall through to apps below. The slight dimming is barely
            // noticeable in practice.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alpha = 0.79f
            }
        }
        windowManager.addView(view, params)
        overlayView = view
    }

    private fun detachOverlay() {
        overlayView?.let {
            runCatching { windowManager.removeView(it) }
            overlayView = null
        }
    }

    private fun stopSelfCleanly() {
        collectJob?.cancel()
        collectJob = null
        motionEstimator.stop()
        detachOverlay()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        _isRunning.value = false
        scope.cancel()
        motionEstimator.stop()
        detachOverlay()
        super.onDestroy()
    }

    private fun startForegroundWithNotification() {
        val channelId = "motion_cues"
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(channelId) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    getString(R.string.motion_cues_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { setShowBadge(false) },
            )
        }
        val openAppPi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopPi = PendingIntent.getService(
            this, 1,
            Intent(this, MotionCuesService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notif: Notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle(getString(R.string.motion_cues_notification_title))
            .setContentText(getString(R.string.motion_cues_notification_text))
            .setContentIntent(openAppPi)
            .addAction(0, getString(R.string.motion_cues_notification_stop), stopPi)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notif)
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "dev.davidv.motionsickness.ACTION_STOP"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, MotionCuesService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, MotionCuesService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
