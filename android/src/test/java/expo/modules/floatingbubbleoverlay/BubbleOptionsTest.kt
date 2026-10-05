package expo.modules.floatingbubbleoverlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Options as they arrive from JS (numbers come in as Double). */
class BubbleOptionsTest {
  @Test
  fun readsEveryJsKey() {
    val o = BubbleOptions.fromMap(
      mapOf(
        "size" to 72.0,
        "opacity" to 0.5,
        "icon" to "ic_bubble",
        "dismissDistance" to 120.0,
        "snapToEdge" to false,
        "x" to 10.0,
        "y" to 20.0,
        "notificationTitle" to "Title",
        "notificationText" to "Text",
        "showWhen" to "always",
        "hideOnPress" to false,
      ),
    )
    assertEquals(BubbleOptions(72, 0.5f, "ic_bubble", 120, false, 10, 20, "Title", "Text", ShowWhen.ALWAYS, false), o)
  }

  @Test
  fun readsEveryShowWhenValue() {
    assertEquals(ShowWhen.BACKGROUND, BubbleOptions.fromMap(mapOf("showWhen" to "background")).showWhen)
    assertEquals(ShowWhen.FOREGROUND, BubbleOptions.fromMap(mapOf("showWhen" to "foreground")).showWhen)
    assertEquals(ShowWhen.ALWAYS, BubbleOptions.fromMap(mapOf("showWhen" to "always")).showWhen)
  }

  @Test
  fun unknownShowWhenFallsBackToBackground() {
    assertEquals(ShowWhen.BACKGROUND, BubbleOptions.fromMap(mapOf("showWhen" to "sometimes")).showWhen)
    assertEquals(ShowWhen.BACKGROUND, BubbleOptions.fromMap(mapOf("showWhen" to 1.0)).showWhen)
  }

  @Test
  fun emptyMapUsesTheDefaults() {
    assertEquals(BubbleOptions(), BubbleOptions.fromMap(emptyMap()))
    assertTrue("a tap hides the bubble by default", BubbleOptions().hideOnPress)
  }

  @Test
  fun clampsOutOfRangeValues() {
    val o = BubbleOptions.fromMap(mapOf("size" to 500.0, "opacity" to 0.0, "dismissDistance" to 1.0))
    assertEquals(96, o.sizeDp)
    assertEquals(0.2f, o.opacity)
    assertEquals(24, o.dismissDistanceDp)
  }

  @Test
  fun ignoresBlankIconAndWrongTypes() {
    val o = BubbleOptions.fromMap(mapOf("icon" to " ", "snapToEdge" to "no", "size" to "big"))
    assertNull(o.icon)
    assertTrue(o.snapToEdge)
    assertEquals(60, o.sizeDp)
  }
}
