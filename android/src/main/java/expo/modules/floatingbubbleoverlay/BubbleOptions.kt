package expo.modules.floatingbubbleoverlay

/** When the enabled bubble is on screen. */
enum class ShowWhen {
  /** Only while the app is in the background (default). */
  BACKGROUND,
  /** Only while the app is in the foreground. */
  FOREGROUND,
  /** In both states. */
  ALWAYS;

  companion object {
    /** "background" | "foreground" | "always"; anything else falls back to [BACKGROUND]. */
    fun fromJs(value: Any?): ShowWhen = when (value) {
      "foreground" -> FOREGROUND
      "always" -> ALWAYS
      else -> BACKGROUND
    }
  }
}

/** Bubble options. They arrive from JS as a map; numbers come in as Double. */
data class BubbleOptions(
  /** Bubble diameter in dp. */
  val sizeDp: Int = 60,
  /** 0.0 to 1.0 */
  val opacity: Float = 1f,
  /** Name of an app drawable/mipmap; falls back to the app icon if it does not exist. */
  val icon: String? = null,
  /** Distance in dp to the "X" within which releasing the bubble dismisses it. */
  val dismissDistanceDp: Int = 96,
  /** On release, the bubble snaps to the nearest side edge. */
  val snapToEdge: Boolean = true,
  /** Initial position in dp; defaults to the right edge, a third of the way down. */
  val initialXDp: Int? = null,
  val initialYDp: Int? = null,
  /** Foreground service notification (required for a foreground service). */
  val notificationTitle: String? = null,
  val notificationText: String? = null,
  /** When the bubble is on screen while enabled. */
  val showWhen: ShowWhen = ShowWhen.BACKGROUND,
  /** Tapping the bubble hides it until the app next goes to the background. */
  val hideOnPress: Boolean = true,
) {
  companion object {
    fun fromMap(m: Map<String, Any?>): BubbleOptions {
      fun int(k: String) = (m[k] as? Number)?.toInt()
      return BubbleOptions(
        sizeDp = int("size")?.coerceIn(40, 96) ?: 60,
        opacity = (m["opacity"] as? Number)?.toFloat()?.coerceIn(0.2f, 1f) ?: 1f,
        icon = (m["icon"] as? String)?.takeIf { it.isNotBlank() },
        dismissDistanceDp = int("dismissDistance")?.coerceAtLeast(24) ?: 96,
        snapToEdge = m["snapToEdge"] as? Boolean ?: true,
        initialXDp = int("x"),
        initialYDp = int("y"),
        notificationTitle = m["notificationTitle"] as? String,
        notificationText = m["notificationText"] as? String,
        showWhen = ShowWhen.fromJs(m["showWhen"]),
        hideOnPress = m["hideOnPress"] as? Boolean ?: true,
      )
    }
  }
}
