package expo.modules.floatingbubbleoverlay

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import expo.modules.floatingbubbleoverlay.overlay.BubbleView
import expo.modules.floatingbubbleoverlay.service.KeepAliveService
import java.util.concurrent.atomic.AtomicInteger

/**
 * Single entry point to show and hide the bubble. Safe to call from any thread:
 * view work runs on the main thread, in the order it was requested.
 */
object BubbleController {
  internal const val TAG = "FloatingBubble"

  interface Listener {
    fun onPress()
    fun onDismiss()
  }

  @Volatile var listener: Listener? = null

  /** What was last requested (the view is created a moment later, on the main thread). */
  @Volatile var isVisible = false
    private set

  private const val RETRY_DELAY_MS = 250L
  private const val MAX_RETRIES = 6

  private val mainHandler = Handler(Looper.getMainLooper())
  private var view: BubbleView? = null
  /** Each show/hide invalidates the pending retries of the previous request. */
  private val lastRequest = AtomicInteger(0)

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

  /** @return false if the permission is missing; true if the bubble will show (or already was). */
  fun show(ctx: Context, options: BubbleOptions): Boolean {
    val app = ctx.applicationContext
    if (!hasOverlayPermission(app)) return false
    isVisible = true
    val request = lastRequest.incrementAndGet()
    mainHandler.post { tryShow(app, options, request, attempts = 0) }
    return true
  }

  /**
   * React Native reports "background" as soon as the activity pauses, which also happens when
   * a system dialog covers the app. The bubble only shows once the app is no longer visible;
   * if it is still visible after ~1.5 s (a dialog), it is not shown.
   */
  private fun tryShow(app: Context, options: BubbleOptions, request: Int, attempts: Int) {
    if (!isVisible || request != lastRequest.get() || view != null) return
    if (isAppVisible()) {
      if (attempts < MAX_RETRIES) {
        mainHandler.postDelayed({ tryShow(app, options, request, attempts + 1) }, RETRY_DELAY_MS)
      } else {
        isVisible = false
      }
      return
    }
    lateinit var newView: BubbleView
    newView = BubbleView(
      app,
      options,
      onPress = { pressed(app) },
      onDismiss = { dismissedByUser(app) },
      // Android 15 only allows starting the service from the background once the window is visible
      onBecameVisible = { if (view === newView && isVisible) KeepAliveService.start(app, options) },
    )
    try {
      newView.attach()
    } catch (e: Exception) {
      Log.w(TAG, "Could not show the bubble", e)
      isVisible = false
      return
    }
    view = newView
  }

  /** true if any activity of the app is still on screen (even if paused). */
  private fun isAppVisible(): Boolean {
    val state = ActivityManager.RunningAppProcessInfo()
    ActivityManager.getMyMemoryState(state)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
        state.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE) {
      return false
    }
    return state.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
  }

  fun hide(ctx: Context) {
    val app = ctx.applicationContext
    isVisible = false
    lastRequest.incrementAndGet()
    mainHandler.post {
      view?.detach()
      view = null
      KeepAliveService.stop(app)
    }
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

  private fun pressed(app: Context) {
    bringAppToForeground(app)
    hide(app)
    listener?.onPress()
  }

  private fun dismissedByUser(app: Context) {
    hide(app)
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
