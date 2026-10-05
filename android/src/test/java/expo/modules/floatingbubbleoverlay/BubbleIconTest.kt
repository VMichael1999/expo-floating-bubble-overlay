package expo.modules.floatingbubbleoverlay

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Looper
import android.os.Process
import android.util.Base64
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import expo.modules.floatingbubbleoverlay.overlay.BubbleIcon
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowSettings
import org.robolectric.shadows.ShadowWindowManagerImpl
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

/** Where the bubble image comes from: app icon by default, or what the developer chooses. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class BubbleIconTest {
  private lateinit var app: Application
  private val appIcon: Drawable = ColorDrawable(0xFF00AA00.toInt())
  private var server: ServerSocket? = null

  /** A real 1x1 PNG. */
  private val png: ByteArray = Base64.decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==",
    Base64.DEFAULT,
  )

  @Before
  fun setUp() {
    app = RuntimeEnvironment.getApplication()
    ShadowSettings.setCanDrawOverlays(true)
    val am = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val info = ActivityManager.RunningAppProcessInfo(app.packageName, Process.myPid(), arrayOf(app.packageName))
    info.importance = ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED
    shadowOf(am).setProcesses(listOf(info))
    shadowOf(app.packageManager).setApplicationIcon(app.packageName, appIcon)
    BubbleController.disable(app)
    advance()
  }

  @After
  fun tearDown() {
    server?.close()
    BubbleController.disable(app)
    advance()
  }

  private fun advance() {
    ShadowLooper.idleMainLooper()
    shadowOf(Looper.getMainLooper()).idle()
  }

  private fun shownIcon(): Drawable {
    val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val container = Shadow.extract<ShadowWindowManagerImpl>(wm).views.single() as ViewGroup
    return (container.getChildAt(0) as ImageView).drawable
  }

  /** Background loads finish on another thread: wait until the icon changes or time runs out. */
  private fun awaitIcon(until: (Drawable) -> Boolean): Drawable {
    val deadline = System.currentTimeMillis() + 5_000
    while (System.currentTimeMillis() < deadline) {
      advance()
      if (until(shownIcon())) return shownIcon()
      Thread.sleep(20)
    }
    return shownIcon()
  }

  private fun show(icon: String?) {
    BubbleController.enable(app, BubbleOptions(icon = icon))
    advance()
  }

  // Parsing the `icon` option

  @Test
  fun parsesEveryKindOfSource() {
    assertEquals(BubbleIcon.AppIcon, BubbleIcon.parse(null))
    assertEquals(BubbleIcon.AppIcon, BubbleIcon.parse("  "))
    assertEquals(BubbleIcon.Resource("ic_bubble"), BubbleIcon.parse("ic_bubble"))
    assertEquals(BubbleIcon.Remote("https://example.com/a.png"), BubbleIcon.parse("https://example.com/a.png"))
    assertEquals(BubbleIcon.Remote("http://10.0.2.2:8081/assets/a.png"), BubbleIcon.parse("http://10.0.2.2:8081/assets/a.png"))
    assertEquals(BubbleIcon.LocalFile("/data/a.png"), BubbleIcon.parse("file:///data/a.png"))
    assertEquals(BubbleIcon.LocalFile("/data/a.png"), BubbleIcon.parse("/data/a.png"))
    assertEquals(BubbleIcon.Base64Data("AAAA"), BubbleIcon.parse("data:image/png;base64,AAAA"))
  }

  // What the bubble shows

  @Test
  fun byDefaultItShowsTheAppIcon() {
    show(null)
    assertSame(appIcon, shownIcon())
  }

  @Test
  fun aNativeResourceIsShownRightAway() {
    show("floating_bubble_notification")
    assertEquals(R.drawable.floating_bubble_notification, shadowOf(shownIcon()).createdFromResId)
  }

  @Test
  fun aMissingResourceFallsBackToTheAppIcon() {
    show("does_not_exist")
    assertSame(appIcon, shownIcon())
  }

  @Test
  fun aLocalFileReplacesTheAppIconOnceLoaded() {
    val file = File(app.cacheDir, "bubble.png").apply { writeBytes(png) }
    show("file://${file.absolutePath}")
    assertTrue(awaitIcon { it is BitmapDrawable } is BitmapDrawable)
  }

  @Test
  fun aBase64ImageReplacesTheAppIconOnceLoaded() {
    show("data:image/png;base64," + Base64.encodeToString(png, Base64.NO_WRAP))
    assertTrue(awaitIcon { it is BitmapDrawable } is BitmapDrawable)
  }

  @Test
  fun aRemoteImageReplacesTheAppIconOnceLoaded() {
    val url = serve(200, png)
    show(url)
    assertTrue(awaitIcon { it is BitmapDrawable } is BitmapDrawable)
  }

  @Test
  fun aRemoteImageThatFailsKeepsTheAppIcon() {
    val url = serve(404, ByteArray(0))
    show(url)
    awaitIcon { it !== appIcon }
    assertSame(appIcon, shownIcon())
  }

  @Test
  fun aMissingFileKeepsTheAppIcon() {
    show("file:///does/not/exist.png")
    awaitIcon { it !== appIcon }
    assertSame(appIcon, shownIcon())
  }

  /** Minimal HTTP server: answers every request with [status] and [body]. Returns its URL. */
  private fun serve(status: Int, body: ByteArray): String {
    val s = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))
    server = s
    thread(isDaemon = true) {
      while (!s.isClosed) {
        val client = runCatching { s.accept() }.getOrNull() ?: break
        client.use { c ->
          val input = c.getInputStream().bufferedReader()
          while (input.readLine()?.isNotEmpty() == true) Unit // skip request line and headers
          val head = "HTTP/1.1 $status X\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
          c.getOutputStream().apply {
            write(head.toByteArray())
            write(body)
            flush()
          }
        }
      }
    }
    return "http://127.0.0.1:${s.localPort}/icon.png"
  }
}
