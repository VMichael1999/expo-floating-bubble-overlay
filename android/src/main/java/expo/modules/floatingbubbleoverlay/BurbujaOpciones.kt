package expo.modules.floatingbubbleoverlay

/** Opciones de la burbuja. Llegan desde JS como mapa; los numeros vienen como Double. */
data class BurbujaOpciones(
  /** Diametro de la burbuja en dp. */
  val tamanoDp: Int = 60,
  /** 0.0 a 1.0 */
  val opacidad: Float = 1f,
  /** Nombre de un drawable/mipmap de la app; si no existe se usa el icono de la app. */
  val icono: String? = null,
  /** Distancia en dp a la "X" a partir de la cual soltar cierra la burbuja. */
  val distanciaCerrarDp: Int = 96,
  /** Al soltar, la burbuja se pega al borde lateral mas cercano. */
  val pegarAlBorde: Boolean = true,
  /** Posicion inicial en dp; por defecto borde derecho, a un tercio de la altura. */
  val xInicialDp: Int? = null,
  val yInicialDp: Int? = null,
  /** Notificacion del servicio (obligatoria para un servicio en primer plano). */
  val tituloNotificacion: String? = null,
  val textoNotificacion: String? = null,
) {
  companion object {
    fun desdeMapa(m: Map<String, Any?>): BurbujaOpciones {
      fun entero(k: String) = (m[k] as? Number)?.toInt()
      return BurbujaOpciones(
        tamanoDp = entero("tamano")?.coerceIn(40, 96) ?: 60,
        opacidad = (m["opacidad"] as? Number)?.toFloat()?.coerceIn(0.2f, 1f) ?: 1f,
        icono = (m["icono"] as? String)?.takeIf { it.isNotBlank() },
        distanciaCerrarDp = entero("distanciaCerrar")?.coerceAtLeast(24) ?: 96,
        pegarAlBorde = m["pegarAlBorde"] as? Boolean ?: true,
        xInicialDp = entero("x"),
        yInicialDp = entero("y"),
        tituloNotificacion = m["tituloNotificacion"] as? String,
        textoNotificacion = m["textoNotificacion"] as? String,
      )
    }
  }
}
