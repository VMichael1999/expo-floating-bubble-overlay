package expo.modules.floatingbubbleoverlay

import android.app.ActivityManager
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import expo.modules.floatingbubbleoverlay.service.KeepAliveService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowSettings
import org.robolectric.shadows.ShadowWindowManagerImpl

/**
 * Bubble logic on a simulated Android (Robolectric).
 * Run: cd example/android && ./gradlew :expo-floating-bubble-overlay:testDebugUnitTest
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class BubbleControllerTest {
  private lateinit var app: Application
  private val wm get() = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
  private fun views(): List<View> = Shadow.extract<ShadowWindowManagerImpl>(wm).views
  private var presses = 0
  private var dismissals = 0

  private fun importance(value: Int) {
    val am = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val info = ActivityManager.RunningAppProcessInfo(app.packageName, Process.myPid(), arrayOf(app.packageName))
    info.importance = value
    shadowOf(am).setProcesses(listOf(info))
  }

  private fun advance(ms: Long = 0) {
    ShadowLooper.idleMainLooper(ms, java.util.concurrent.TimeUnit.MILLISECONDS)
    shadowOf(Looper.getMainLooper()).idle()
  }

  private fun touch(v: View, action: Int, x: Float, y: Float) {
    val t = SystemClock.uptimeMillis()
    val e = MotionEvent.obtain(t, t, action, x, y, 0)
    v.dispatchTouchEvent(e)
    e.recycle()
  }

  @Before
  fun setUp() {
    app = RuntimeEnvironment.getApplication()
    ShadowSettings.setCanDrawOverlays(true)
    importance(ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED)
    // Launcher activity so that getLaunchIntentForPackage returns something
    val main = ActivityInfo().apply { packageName = app.packageName; name = "MainActivity" }
    val filter = IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
    shadowOf(app.packageManager).apply {
      addOrUpdateActivity(main)
      addIntentFilterForActivity(ComponentName(app.packageName, "MainActivity"), filter)
    }
    presses = 0
    dismissals = 0
    BubbleController.listener = object : BubbleController.Listener {
      override fun onPress() { presses++ }
      override fun onDismiss() { dismissals++ }
    }
    BubbleController.hide(app)
    advance()
  }

  @Test
  fun withoutPermissionItIsNotShown() {
    ShadowSettings.setCanDrawOverlays(false)
    assertFalse(BubbleController.hasOverlayPermission(app))
    assertFalse(BubbleController.show(app, BubbleOptions()))
    advance()
    assertTrue(views().isEmpty())
  }

  @Test
  fun withPermissionAndAppInBackgroundItShowsAsOverlay() {
    assertTrue(BubbleController.show(app, BubbleOptions()))
    advance()
    assertEquals(1, views().size)
    val lp = views()[0].layoutParams as WindowManager.LayoutParams
    assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, lp.type)
    assertTrue(lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
    // 60dp + 2*8dp padding, density 1 by default in Robolectric (mdpi)
    val d = app.resources.displayMetrics.density
    assertEquals(((60 + 16) * d).toInt(), lp.width)
    assertTrue(BubbleController.isVisible)
  }

  @Test
  fun withAppVisibleItWaitsAndThenIsNotShown() {
    importance(ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE)
    BubbleController.show(app, BubbleOptions())
    advance(300)
    assertTrue("must not appear over its own app", views().isEmpty())
    advance(2000)
    assertTrue(views().isEmpty())
    assertFalse(BubbleController.isVisible)
  }

  @Test
  fun ifTheAppStopsBeingVisibleWhileWaitingItShows() {
    importance(ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE)
    BubbleController.show(app, BubbleOptions())
    advance(300)
    assertTrue(views().isEmpty())
    importance(ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED)
    advance(300)
    assertEquals(1, views().size)
  }

  @Test
  fun hidingBeforeItAppearsLeavesNothing() {
    BubbleController.show(app, BubbleOptions())
    BubbleController.hide(app)
    advance(2000)
    assertTrue(views().isEmpty())
  }

  @Test
  fun tapBringsTheAppToForegroundAndHidesTheBubble() {
    BubbleController.show(app, BubbleOptions())
    advance()
    val v = views()[0]
    touch(v, MotionEvent.ACTION_DOWN, 30f, 30f)
    touch(v, MotionEvent.ACTION_UP, 30f, 30f)
    advance()
    val started = shadowOf(app).nextStartedActivity
    assertNotNull("must open the app", started)
    assertEquals("MainActivity", started.component?.className)
    assertTrue(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    assertEquals(1, presses)
    assertTrue(views().isEmpty())
  }

  @Test
  fun draggingShowsTheXAndReleasingAwaySnapsToTheEdge() {
    BubbleController.show(app, BubbleOptions())
    advance()
    val v = views()[0]
    touch(v, MotionEvent.ACTION_DOWN, 30f, 30f)
    touch(v, MotionEvent.ACTION_MOVE, -200f, 60f) // drag to the left
    assertEquals("the X appears", 2, views().size)
    touch(v, MotionEvent.ACTION_UP, -200f, 60f)
    advance(500)
    assertEquals("the X goes away on release", 1, views().size)
    assertEquals(0, presses)
    val lp = views()[0].layoutParams as WindowManager.LayoutParams
    assertTrue("snapped to a side edge, x=${lp.x}", lp.x == 0 || lp.x + lp.width == app.resources.displayMetrics.widthPixels)
  }

  @Test
  fun releasingOverTheXDismissesIt() {
    BubbleController.show(app, BubbleOptions())
    advance()
    val v = views()[0]
    val lp = v.layoutParams as WindowManager.LayoutParams
    touch(v, MotionEvent.ACTION_DOWN, 30f, 30f)
    touch(v, MotionEvent.ACTION_MOVE, 30f, 60f) // starts dragging: the X appears
    val x = views().first { it !== v }
    val lpx = x.layoutParams as WindowManager.LayoutParams
    // Move the bubble center onto the X center (raw coordinates = screen coordinates in the event)
    val dx = (lpx.x + lpx.width / 2f) - (lp.x + lp.width / 2f)
    val dy = (lpx.y + lpx.height / 2f) - (lp.y + lp.height / 2f)
    touch(v, MotionEvent.ACTION_MOVE, 30f + dx, 30f + dy)
    touch(v, MotionEvent.ACTION_UP, 30f + dx, 30f + dy)
    advance()
    assertEquals(1, dismissals)
    assertEquals(0, presses)
    assertTrue(views().isEmpty())
    assertFalse(BubbleController.isVisible)
  }

  @Test
  fun serviceGoesForegroundWithItsNotification() {
    BubbleController.show(app, BubbleOptions(notificationTitle = "Viaje en curso"))
    advance()
    val controller = Robolectric.buildService(
      KeepAliveService::class.java,
      Intent(app, KeepAliveService::class.java).putExtra(KeepAliveService.EXTRA_TITLE, "Viaje en curso"),
    ).create().startCommand(0, 1)
    val service = controller.get()
    val n = shadowOf(service).lastForegroundNotification
    assertNotNull(n)
    assertEquals("Viaje en curso", n.extras.getString("android.title"))
    assertFalse("stays alive while the bubble is visible", shadowOf(service).isStoppedBySelf)
  }

  @Test
  fun serviceStartedWithTheBubbleAlreadyHiddenStopsItself() {
    BubbleController.hide(app)
    advance()
    val service = Robolectric.buildService(KeepAliveService::class.java, Intent(app, KeepAliveService::class.java))
      .create().startCommand(0, 1).get()
    assertNotNull("startForeground first", shadowOf(service).lastForegroundNotification)
    assertTrue("then it stops", shadowOf(service).isStoppedBySelf)
  }

  @Test
  fun keepAliveServiceDoesNotStopWithoutTheBubble() {
    KeepAliveService.keepAlive = true
    try {
      BubbleController.hide(app)
      advance()
      val service = Robolectric.buildService(KeepAliveService::class.java, Intent(app, KeepAliveService::class.java))
        .create().startCommand(0, 1).get()
      assertNotNull(shadowOf(service).lastForegroundNotification)
      assertFalse("lives without the bubble while keep-alive is on", shadowOf(service).isStoppedBySelf)
    } finally {
      KeepAliveService.keepAlive = false
    }
  }

  @Test
  fun withForegroundServiceAndAppMinimizedItShows() {
    importance(ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE)
    assertTrue(BubbleController.show(app, BubbleOptions()))
    advance()
    assertEquals(1, views().size)
    assertTrue(BubbleController.isVisible)
  }

  @Test
  fun bringAppToForegroundWithPermissionStartsTheLauncherActivity() {
    assertTrue(BubbleController.bringAppToForeground(app))
    val started = shadowOf(app).nextStartedActivity
    assertEquals("MainActivity", started.component?.className)
    assertTrue(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
  }

  @Test
  fun bringAppToForegroundWithoutPermissionDoesNothing() {
    ShadowSettings.setCanDrawOverlays(false)
    assertFalse(BubbleController.bringAppToForeground(app))
    assertEquals(null, shadowOf(app).nextStartedActivity)
  }

  @Test
  fun scheduledBringToForegroundOpensTheAppAfterTheDelay() {
    BubbleController.scheduleBringAppToForeground(app, 3)
    advance(1000)
    assertEquals(null, shadowOf(app).nextStartedActivity)
    advance(2000)
    val started = shadowOf(app).nextStartedActivity
    assertNotNull(started)
    assertEquals("MainActivity", started.component?.className)
  }

  @Test
  fun cancellingTheScheduledBringToForegroundPreventsIt() {
    BubbleController.scheduleBringAppToForeground(app, 3)
    advance(1000)
    BubbleController.cancelScheduledBringAppToForeground()
    advance(3000)
    assertEquals(null, shadowOf(app).nextStartedActivity)
  }
}
