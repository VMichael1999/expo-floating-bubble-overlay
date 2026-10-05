package expo.modules.floatingbubbleoverlay

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * La burbuja y su zona de cierre, dibujadas con WindowManager sobre cualquier app.
 *
 * - Tocar: [alTocar]
 * - Arrastrar: aparece la "X" abajo; si se suelta cerca, [alCerrar]
 * - Soltar lejos de la "X": se pega al borde lateral mas cercano
 *
 * Todo se llama en el hilo principal.
 */
internal class BurbujaVista(
  private val ctx: Context,
  private val op: BurbujaOpciones,
  private val alTocar: () -> Unit,
  private val alCerrar: () -> Unit,
  /** Una sola vez, cuando la ventana de la burbuja ya es visible (requisito de Android 15). */
  private val alHacerseVisible: () -> Unit,
) {
  private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
  private val densidad = ctx.resources.displayMetrics.density
  private fun dp(v: Int) = (v * densidad).roundToInt()

  private val tipoVentana =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

  // Margen alrededor de la burbuja para que la sombra no se recorte con la ventana
  private val margen = dp(8)
  private val diametro = dp(op.tamanoDp)
  private val lado = diametro + margen * 2
  private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
  private val margenSuperior = dp(48)
  private val margenInferior = dp(96)

  private val burbuja = ImageView(ctx).apply {
    setImageDrawable(icono())
    scaleType = ImageView.ScaleType.FIT_CENTER
    background = GradientDrawable().apply {
      shape = GradientDrawable.OVAL
      setColor(Color.WHITE)
    }
    outlineProvider = ViewOutlineProvider.BACKGROUND
    clipToOutline = true
    elevation = dp(6).toFloat()
    alpha = op.opacidad
    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
  }

  private var avisoVisible = false

  /** FrameLayout que avisa cuando su ventana se ve y cuando gira la pantalla. */
  private inner class Contenedor : FrameLayout(ctx) {
    override fun onWindowVisibilityChanged(visibility: Int) {
      super.onWindowVisibilityChanged(visibility)
      if (visibility == View.VISIBLE && !avisoVisible) {
        avisoVisible = true
        alHacerseVisible()
      }
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
      super.onConfigurationChanged(newConfig)
      // Nueva orientacion o tamano: volver a dejarla dentro de la pantalla
      post { if (agregada) asentar() }
    }
  }

  private val contenedor = Contenedor().apply {
    clipToPadding = false
    setPadding(margen, margen, margen, margen)
    addView(burbuja, FrameLayout.LayoutParams(diametro, diametro))
    contentDescription = "Volver a ${nombreApp()}"
    // TalkBack: doble toque abre la app igual que un toque
    setOnClickListener { alTocar() }
  }

  private val params = WindowManager.LayoutParams(
    lado,
    lado,
    tipoVentana,
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
    PixelFormat.TRANSLUCENT,
  ).apply { gravity = Gravity.TOP or Gravity.START }

  // Zona de cierre: circulo oscuro con una X, abajo al centro. No recibe toques.
  private val tamCierre = dp(64)
  private val cierre = FrameLayout(ctx).apply {
    background = GradientDrawable().apply {
      shape = GradientDrawable.OVAL
      setColor(0xE6111519.toInt())
    }
    elevation = dp(4).toFloat()
    addView(
      TextView(ctx).apply {
        text = "✕"
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
        gravity = Gravity.CENTER
      },
      FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
    )
    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
  }
  private val paramsCierre = WindowManager.LayoutParams(
    tamCierre + margen * 2,
    tamCierre + margen * 2,
    tipoVentana,
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
      WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
      WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
    PixelFormat.TRANSLUCENT,
  ).apply { gravity = Gravity.TOP or Gravity.START }
  private val contenedorCierre = FrameLayout(ctx).apply {
    clipToPadding = false
    setPadding(margen, margen, margen, margen)
    addView(cierre, FrameLayout.LayoutParams(tamCierre, tamCierre))
  }

  private var animador: ValueAnimator? = null
  private var agregada = false
  private var cierreVisible = false

  // Estado del gesto
  private var toqueX = 0f
  private var toqueY = 0f
  private var inicioX = 0
  private var inicioY = 0
  private var arrastrando = false
  private var imantada = false

  /** Agrega la burbuja a la pantalla. Lanza si falta el permiso de superposicion. */
  fun agregar() {
    if (agregada) return
    val p = pantalla()
    params.x = op.xInicialDp?.let { dp(it) } ?: (p.x - lado)
    params.y = op.yInicialDp?.let { dp(it) } ?: (p.y / 3)
    limitar(p)
    contenedor.setOnTouchListener(::alTocarPantalla)
    wm.addView(contenedor, params)
    agregada = true
    // Entrada: crece desde el centro
    burbuja.scaleX = 0.4f
    burbuja.scaleY = 0.4f
    burbuja.animate().scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(OvershootInterpolator()).start()
  }

  fun quitar() {
    animador?.cancel()
    ocultarCierre()
    if (agregada) {
      runCatching { wm.removeViewImmediate(contenedor) }
      agregada = false
    }
  }

  @SuppressLint("ClickableViewAccessibility")
  private fun alTocarPantalla(v: View, e: MotionEvent): Boolean {
    when (e.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        animador?.cancel()
        toqueX = e.rawX
        toqueY = e.rawY
        inicioX = params.x
        inicioY = params.y
        arrastrando = false
        imantada = false
        burbuja.animate().scaleX(0.92f).scaleY(0.92f).setDuration(90).start()
      }
      MotionEvent.ACTION_MOVE -> {
        val dx = e.rawX - toqueX
        val dy = e.rawY - toqueY
        if (!arrastrando && hypot(dx, dy) > slop) {
          arrastrando = true
          mostrarCierre()
        }
        if (arrastrando) mover(inicioX + dx.roundToInt(), inicioY + dy.roundToInt())
      }
      MotionEvent.ACTION_UP -> {
        burbuja.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
        when {
          !arrastrando -> v.performClick()
          imantada -> {
            ocultarCierre()
            alCerrar()
          }
          else -> {
            ocultarCierre()
            asentar()
          }
        }
      }
      MotionEvent.ACTION_CANCEL -> {
        burbuja.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
        ocultarCierre()
        asentar()
      }
    }
    return true
  }

  /** Mueve la burbuja siguiendo el dedo; cerca de la X se queda pegada a ella. */
  private fun mover(x: Int, y: Int) {
    val centroX = x + lado / 2f
    val centroY = y + lado / 2f
    val cierreX = paramsCierre.x + paramsCierre.width / 2f
    val cierreY = paramsCierre.y + paramsCierre.height / 2f
    val cerca = cierreVisible && hypot(centroX - cierreX, centroY - cierreY) < dp(op.distanciaCerrarDp)

    if (cerca != imantada) {
      imantada = cerca
      val escala = if (cerca) 1.2f else 1f
      cierre.animate().scaleX(escala).scaleY(escala).setDuration(120).start()
      if (cerca) contenedor.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    if (imantada) {
      params.x = (cierreX - lado / 2f).roundToInt()
      params.y = (cierreY - lado / 2f).roundToInt()
    } else {
      params.x = x
      params.y = y
    }
    actualizar()
  }

  /** Al soltar: al borde lateral mas cercano (o solo dentro de la pantalla). */
  private fun asentar() {
    val p = pantalla()
    val desdeX = params.x
    val desdeY = params.y
    val haciaX = when {
      !op.pegarAlBorde -> desdeX.coerceIn(0, p.x - lado)
      desdeX + lado / 2 < p.x / 2 -> 0
      else -> p.x - lado
    }
    val haciaY = desdeY.coerceIn(margenSuperior, (p.y - lado - margenInferior).coerceAtLeast(margenSuperior))
    animador = ValueAnimator.ofFloat(0f, 1f).apply {
      duration = 260
      interpolator = OvershootInterpolator(0.8f)
      addUpdateListener {
        val t = it.animatedValue as Float
        params.x = (desdeX + (haciaX - desdeX) * t).roundToInt()
        params.y = (desdeY + (haciaY - desdeY) * t).roundToInt()
        actualizar()
      }
      start()
    }
  }

  private fun mostrarCierre() {
    if (cierreVisible) return
    val p = pantalla()
    paramsCierre.x = (p.x - paramsCierre.width) / 2
    paramsCierre.y = p.y - paramsCierre.height - dp(56)
    runCatching {
      wm.addView(contenedorCierre, paramsCierre)
      cierreVisible = true
      cierre.scaleX = 0.6f
      cierre.scaleY = 0.6f
      cierre.alpha = 0f
      cierre.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(160).start()
    }
  }

  private fun ocultarCierre() {
    if (!cierreVisible) return
    runCatching { wm.removeViewImmediate(contenedorCierre) }
    cierreVisible = false
    imantada = false
  }

  private fun actualizar() {
    if (agregada && contenedor.isAttachedToWindow) runCatching { wm.updateViewLayout(contenedor, params) }
  }

  private fun limitar(p: Point) {
    params.x = params.x.coerceIn(0, (p.x - lado).coerceAtLeast(0))
    params.y = params.y.coerceIn(margenSuperior, (p.y - lado - margenInferior).coerceAtLeast(margenSuperior))
  }

  private fun pantalla(): Point =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      val b = wm.currentWindowMetrics.bounds
      Point(b.width(), b.height())
    } else {
      val m = DisplayMetrics()
      @Suppress("DEPRECATION")
      wm.defaultDisplay.getRealMetrics(m)
      Point(m.widthPixels, m.heightPixels)
    }

  private fun icono(): Drawable {
    val nombre = op.icono
    if (nombre != null) {
      val res = ctx.resources
      val id = res.getIdentifier(nombre, "drawable", ctx.packageName)
        .takeIf { it != 0 } ?: res.getIdentifier(nombre, "mipmap", ctx.packageName)
      if (id != 0) runCatching { return ctx.getDrawable(id)!! }
    }
    // Respaldo: sin icono de la app (raro) se usa el generico del sistema, nunca se cae
    return runCatching { ctx.packageManager.getApplicationIcon(ctx.packageName) as Drawable? }.getOrNull()
      ?: ctx.getDrawable(android.R.drawable.sym_def_app_icon)!!
  }

  private fun nombreApp(): String = ctx.packageManager.getApplicationLabel(ctx.applicationInfo).toString()
}
