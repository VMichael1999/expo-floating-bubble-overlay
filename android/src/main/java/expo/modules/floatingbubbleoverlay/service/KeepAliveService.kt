package expo.modules.floatingbubbleoverlay.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import expo.modules.floatingbubbleoverlay.BubbleController
import expo.modules.floatingbubbleoverlay.BubbleOptions
import expo.modules.floatingbubbleoverlay.R

/**
 * Foreground service while the bubble is visible (or keep-alive is on): stops Android from
 * killing the app in the background and shows a notification to return to it.
 * If Android does not allow starting it, the bubble keeps working without it.
 */
class KeepAliveService : Service() {

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    // Texts requested while the service was starting win over the ones in the intent
    val latest = pendingOptions
    pendingOptions = null
    val title = if (latest != null) latest.notificationTitle else intent?.getStringExtra(EXTRA_TITLE)
    val text = if (latest != null) latest.notificationText else intent?.getStringExtra(EXTRA_TEXT)
    try {
      val notification = buildNotification(this, title, text)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
      } else {
        startForeground(NOTIFICATION_ID, notification)
      }
      inForeground = true
    } catch (e: Exception) {
      Log.w(BubbleController.TAG, "Could not move to the foreground", e)
    }
    starting = false
    // If the bubble was hidden while the service was starting, stop only now:
    // stopping it before startForeground crashes the app on several Android versions.
    if (!inForeground || (!BubbleController.isVisible && !keepAlive)) stopSelf()
    return START_NOT_STICKY
  }

  override fun onDestroy() {
    inForeground = false
    starting = false
    pendingOptions = null
    super.onDestroy()
  }

  /** If the user swipes the app away from recents, the bubble goes with it. */
  override fun onTaskRemoved(rootIntent: Intent?) {
    BubbleController.disable(this)
    stopSelf()
    super.onTaskRemoved(rootIntent)
  }

  companion object {
    private const val CHANNEL_ID = "floating_bubble"
    internal const val CHANNEL_NAME = "Floating bubble"
    internal const val CHANNEL_DESCRIPTION = "Shown while the floating bubble keeps the app running"
    internal const val DEFAULT_TEXT = "Tap to return to the app"
    internal const val NOTIFICATION_ID = 4101
    internal const val EXTRA_TITLE = "title"
    internal const val EXTRA_TEXT = "text"

    // Only touched on the main thread (start/stop/onStartCommand)
    private var starting = false
    private var inForeground = false
    /** Texts requested while the service was still starting. */
    private var pendingOptions: BubbleOptions? = null

    /**
     * true while keep-alive is on: the service lives even without the bubble (dismissing it
     * or lacking its permission must not stop the app from staying alive).
     */
    @Volatile var keepAlive = false

    fun start(ctx: Context, options: BubbleOptions) {
      val intent = Intent(ctx, KeepAliveService::class.java)
        .putExtra(EXTRA_TITLE, options.notificationTitle)
        .putExtra(EXTRA_TEXT, options.notificationText)
      if (inForeground) {
        // Already running: only the notification changes (e.g. "Online" -> "Trip in progress")
        updateNotification(ctx, options)
        return
      }
      if (starting) {
        pendingOptions = options
        return
      }
      try {
        starting = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(intent)
        else ctx.startService(intent)
      } catch (e: Exception) {
        // e.g. ForegroundServiceStartNotAllowedException: the bubble keeps working without the service
        starting = false
        Log.w(BubbleController.TAG, "Could not start the service", e)
      }
    }

    fun stop(ctx: Context) {
      if (keepAlive) return
      // Still starting: onStartCommand will see the bubble hidden and stop itself
      if (starting) return
      if (inForeground) ctx.stopService(Intent(ctx, KeepAliveService::class.java))
    }

    private fun updateNotification(ctx: Context, options: BubbleOptions) {
      try {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(ctx, options.notificationTitle, options.notificationText))
      } catch (e: Exception) {
        Log.w(BubbleController.TAG, "Could not update the notification", e)
      }
    }

    private fun buildNotification(ctx: Context, title: String?, text: String?): Notification {
      val open = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.let {
        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        PendingIntent.getActivity(ctx, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
      }
      val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
          nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW).apply {
              description = CHANNEL_DESCRIPTION
              setShowBadge(false)
            },
          )
        }
        Notification.Builder(ctx, CHANNEL_ID)
      } else {
        @Suppress("DEPRECATION")
        Notification.Builder(ctx)
      }
      return builder
        .setContentTitle(title ?: ctx.packageManager.getApplicationLabel(ctx.applicationInfo).toString())
        .setContentText(text ?: DEFAULT_TEXT)
        // Monochrome vector: the full-color app icon renders as a blob in the status bar
        .setSmallIcon(R.drawable.floating_bubble_notification)
        .setOngoing(true)
        .setCategory(Notification.CATEGORY_SERVICE)
        .setContentIntent(open)
        .build()
    }
  }
}
