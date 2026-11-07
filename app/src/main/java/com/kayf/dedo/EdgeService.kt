package com.kayf.dedo

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ServiceInfo
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.os.UserManager
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast

/**
 * A thin strip on the thumb's edge, drawn over every app, that turns a flick into an app launch so
 * nothing has to go home first. Holding an overlay window is also what lets it start activities
 * from the background.
 *
 * It logs every stroke under the tag dedo-edge, including the ones the system takes away for its
 * back gesture, because finding out how often that happens is the point of this version.
 */
class EdgeService : Service() {
    private lateinit var windows: WindowManager
    private var strip: Strip? = null

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        windows = getSystemService(WindowManager::class.java)
        // The overlay has to be showing before startForeground: that's what exempts it from the
        // limits on starting a foreground service from the background, as after a restart.
        attach()
    }

    /** Every startForegroundService, including repeats while running, must be answered with startForeground. */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        foreground()
        return START_STICKY
    }

    override fun onDestroy() {
        strip?.let { windows.removeView(it) }
        strip = null
        super.onDestroy()
    }

    private fun attach() {
        val density = resources.displayMetrics.density
        val onLeft = Store(this).railOnLeft
        val view = Strip(onLeft)
        val params = WindowManager.LayoutParams(
            (WIDTH_DP * density).toInt(),
            (HEIGHT_DP * density).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or if (onLeft) Gravity.START else Gravity.END
            y = (BOTTOM_DP * density).toInt()
        }
        windows.addView(view, params)
        strip = view
    }

    private fun foreground() {
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL, "Edge strip", NotificationManager.IMPORTANCE_MIN)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_d)
            .setContentTitle("edge strip on")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION, notification)
        }
    }

    private fun flick(slot: Int) {
        if (slot == Edge.LEADER) {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val store = Store(this)
        val key = store.pins.getOrNull(slot)
        if (key == null) {
            Toast.makeText(this, "no pin ${slot + 1}", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            launch(key)
            store.launched(key)
        } catch (e: RuntimeException) {
            Log.w(TAG, "can't open $key", e)
            Toast.makeText(this, "can't open pin ${slot + 1}", Toast.LENGTH_SHORT).show()
        }
    }

    /** Pins are stored as "package/activity@user serial", so no app list is needed to open one. */
    private fun launch(key: String) {
        val component = ComponentName.unflattenFromString(key.substringBefore('@'))
            ?: throw IllegalArgumentException("bad key")
        val user = getSystemService(UserManager::class.java)
            .getUserForSerialNumber(key.substringAfter('@').toLong())
            ?: throw IllegalArgumentException("no such user")
        getSystemService(LauncherApps::class.java).startMainActivity(component, user, null, null)
    }

    @SuppressLint("ViewConstructor")
    private inner class Strip(private val onLeft: Boolean) : View(this@EdgeService) {
        private val density = resources.displayMetrics.density
        private val threshold = THRESHOLD_DP * density
        private val line = Paint().apply { color = 0x40FFFFFF }
        private var downX = 0f
        private var downY = 0f
        private var downAt = 0L
        private var upper = false
        private var done = false

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            // Only about 200dp per edge is honoured, which is why the strip is no taller than that.
            systemGestureExclusionRects = listOf(Rect(0, 0, width, height))
        }

        override fun onDraw(canvas: Canvas) {
            val w = 3 * density
            val left = if (onLeft) 0f else width - w
            canvas.drawRect(left, 0f, left + w, height.toFloat(), line)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    downAt = SystemClock.uptimeMillis()
                    upper = event.y < height / 2f
                    done = false
                }
                MotionEvent.ACTION_MOVE -> if (!done) {
                    val dx = event.rawX - downX
                    val inward = if (onLeft) dx else -dx
                    when (val result = Edge.classify(inward, event.rawY - downY, upper, threshold)) {
                        is Edge.Flick.Slot -> {
                            done = true
                            performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            Log.i(TAG, "flick slot=${result.index} upper=$upper ms=${elapsed()}")
                            flick(result.index)
                        }
                        Edge.Flick.Rejected -> {
                            done = true
                            performHapticFeedback(HapticFeedbackConstants.REJECT)
                            Log.i(TAG, "rejected upper=$upper ms=${elapsed()}")
                        }
                        Edge.Flick.Pending -> {}
                    }
                }
                MotionEvent.ACTION_UP -> if (!done) Log.i(TAG, "too short upper=$upper ms=${elapsed()}")
                // The system took the stroke, most likely for the back gesture.
                MotionEvent.ACTION_CANCEL -> if (!done) Log.i(TAG, "stolen upper=$upper ms=${elapsed()}")
            }
            return true
        }

        private fun elapsed() = SystemClock.uptimeMillis() - downAt
    }

    companion object {
        const val TAG = "dedo-edge"
        private const val CHANNEL = "edge"
        private const val NOTIFICATION = 1
        private const val WIDTH_DP = 20
        private const val HEIGHT_DP = 200
        private const val BOTTOM_DP = 96
        private const val THRESHOLD_DP = 28
    }
}
