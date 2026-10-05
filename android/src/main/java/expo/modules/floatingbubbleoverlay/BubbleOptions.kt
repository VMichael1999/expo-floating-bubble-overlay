package expo.modules.floatingbubbleoverlay

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
) {
  companion object {
    fun fromMap(m: Map<String, Any?>): BubbleOptions {
      fun int(k: String) = (m[k] as? Number)?.toInt()
      return BubbleOptions(
        sizeDp = int("tamano")?.coerceIn(40, 96) ?: 60,
        opacity = (m["opacidad"] as? Number)?.toFloat()?.coerceIn(0.2f, 1f) ?: 1f,
        icon = (m["icono"] as? String)?.takeIf { it.isNotBlank() },
        dismissDistanceDp = int("distanciaCerrar")?.coerceAtLeast(24) ?: 96,
        snapToEdge = m["pegarAlBorde"] as? Boolean ?: true,
        initialXDp = int("x"),
        initialYDp = int("y"),
        notificationTitle = m["tituloNotificacion"] as? String,
        notificationText = m["textoNotificacion"] as? String,
      )
    }
  }
}
