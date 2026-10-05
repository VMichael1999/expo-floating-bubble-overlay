package expo.modules.floatingbubbleoverlay

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import expo.modules.floatingbubbleoverlay.overlay.BubbleView
import expo.modules.floatingbubbleoverlay.service.KeepAliveService

/**
 * Single entry point for the bubble. Once enabled, it shows or hides the bubble by itself
 * as the app moves between foreground and background, according to [BubbleOptions.showWhen].
 * Safe to call from any thread: view work runs on the main thread, in the order it was requested.
 */
object BubbleController {
  internal const val TAG = "FloatingBubble"

  interface Listener {
    fun onPress()
    fun onDismiss()
  }

  @Volatile var listener: Listener? = null

  /** The developer turned the bubble on (it may still be hidden because of [BubbleOptions.showWhen]). */
  @Volatile var isEnabled = false
    private set

  /** The bubble is on screen right now. */
  @Volatile var isVisible = false
    private set

  private val mainHandler = Handler(Looper.getMainLooper())

  // Main-thread state
  private var view: BubbleView? = null
  private var options = BubbleOptions()
  private var lifecycleApp: Application? = null
  private var startedActivities = 0

  private val appInForeground get() = startedActivities > 0

  fun hasOverlayPermission(ctx: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(ctx)

  /** Opens this app's system "Display over other apps" screen. */
  fun openOverlayPermissionSettings(ctx: Context) {
    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
      ctx.startActivity(intent)
    } catch (e: Exception) {
      // Some manufacturers lack the per-app screen: open the app details instead
      ctx.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
      )
    }
  }

  /**
   * Turns the bubble on. From now on it appears and disappears by itself following
   * [BubbleOptions.showWhen]. Calling it again with other options applies them.
   * @return false if the overlay permission is missing.
   */
  fun enable(ctx: Context, options: BubbleOptions): Boolean {
    val app = ctx.applicationContext
    if (!hasOverlayPermission(app)) return false
    isEnabled = true
    mainHandler.post {
      if (!isEnabled) return@post
      watchLifecycle(app)
      if (options != this.options) {
        // Recreate the bubble with the new options
        removeView(app)
        this.options = options
      }
      update(app)
    }
    return true
  }

  /** Turns the bubble off: it is removed and stops following the app state. */
  fun disable(ctx: Context) {
    val app = ctx.applicationContext
    isEnabled = false
    mainHandler.post { removeView(app) }
  }

  /** Shows or hides the bubble for the current app state. */
  private fun update(app: Context) {
    val shouldShow = isEnabled && when (options.showWhen) {
      ShowWhen.BACKGROUND -> !appInForeground
      ShowWhen.FOREGROUND -> appInForeground
      ShowWhen.ALWAYS -> true
    }
    if (shouldShow) addView(app) else removeView(app)
  }

  private fun addView(app: Context) {
    if (view != null) return
    lateinit var newView: BubbleView
    newView = BubbleView(
      app,
      options,
      onPress = { pressed(app) },
      onDismiss = { dismissedByUser(app) },
      // Android 15 only allows starting the service from the background once the window is visible
      onBecameVisible = { if (view === newView) KeepAliveService.start(app, options) },
    )
    try {
      newView.attach()
    } catch (e: Exception) {
      Log.w(TAG, "Could not show the bubble", e)
      return
    }
    view = newView
    isVisible = true
  }

  private fun removeView(app: Context) {
    val current = view ?: return
    current.detach()
    view = null
    isVisible = false
    KeepAliveService.stop(app)
  }

  /**
   * Follows the app between foreground and background with the activity lifecycle.
   * A dialog over the app only pauses the activity, so it does not count as leaving the app.
   */
  private fun watchLifecycle(ctx: Context) {
    val app = ctx as? Application ?: return
    if (lifecycleApp === app) return
    lifecycleApp = app
    // Activities started before registering are not reported: start from the current state
    startedActivities = if (isAppVisible()) 1 else 0
    app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
      override fun onActivityStarted(activity: Activity) {
        startedActivities++
        update(app)
      }

      override fun onActivityStopped(activity: Activity) {
        startedActivities = (startedActivities - 1).coerceAtLeast(0)
        update(app)
      }

      override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
      override fun onActivityResumed(activity: Activity) {}
      override fun onActivityPaused(activity: Activity) {}
      override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
      override fun onActivityDestroyed(activity: Activity) {}
    })
  }

  /** true if any activity of the app is on screen. A foreground service alone does not count. */
  private fun isAppVisible(): Boolean {
    val state = ActivityManager.RunningAppProcessInfo()
    ActivityManager.getMyMemoryState(state)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
        state.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE) {
      return false
    }
    return state.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
  }

  /**
   * Keeps the app alive in the background (foreground service) without needing the bubble or
   * its permission. Call it while the app is on screen: Android does not allow starting a
   * foreground service from the background.
   */
  fun startKeepAlive(ctx: Context, options: BubbleOptions) {
    val app = ctx.applicationContext
    KeepAliveService.keepAlive = true
    mainHandler.post { KeepAliveService.start(app, options) }
  }

  /** Stops keeping the app alive; the service only stays if the bubble needs it. */
  fun stopKeepAlive(ctx: Context) {
    val app = ctx.applicationContext
    KeepAliveService.keepAlive = false
    mainHandler.post { if (!isVisible) KeepAliveService.stop(app) }
  }

  /** Tapping the bubble brings the app back if it is in the background; the bubble stays enabled. */
  private fun pressed(app: Context) {
    if (!appInForeground) bringAppToForeground(app)
    listener?.onPress()
  }

  /** Dropping the bubble on the X turns it off until the app enables it again. */
  private fun dismissedByUser(app: Context) {
    disable(app)
    listener?.onDismiss()
  }

  /**
   * Brings the app to the front the way the launcher would (same task, same state).
   * From the background Android only allows it with the "Display over other apps" permission.
   * @return false if the permission is missing or Android refused it.
   */
  fun bringAppToForeground(ctx: Context): Boolean {
    val app = ctx.applicationContext
    if (!hasOverlayPermission(app)) return false
    val intent = app.packageManager.getLaunchIntentForPackage(app.packageName) ?: return false
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    return try {
      app.startActivity(intent)
      true
    } catch (e: Exception) {
      Log.w(TAG, "Could not bring the app to the foreground", e)
      false
    }
  }

  private var scheduledBringToForeground: Runnable? = null

  /**
   * Brings the app to the foreground after [seconds], using a native main-thread timer.
   * Unlike JavaScript timers (which React Native freezes in the background), this one runs
   * in the background as long as the process is alive.
   */
  fun scheduleBringAppToForeground(ctx: Context, seconds: Int) {
    cancelScheduledBringAppToForeground()
    if (seconds <= 0) {
      bringAppToForeground(ctx)
      return
    }
    val app = ctx.applicationContext
    val r = Runnable {
      scheduledBringToForeground = null
      bringAppToForeground(app)
    }
    scheduledBringToForeground = r
    mainHandler.postDelayed(r, seconds * 1000L)
  }

  /** Cancels any pending scheduled bring-to-foreground. */
  fun cancelScheduledBringAppToForeground() {
    scheduledBringToForeground?.let {
      mainHandler.removeCallbacks(it)
      scheduledBringToForeground = null
    }
  }
}
