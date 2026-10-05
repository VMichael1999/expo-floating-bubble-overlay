package expo.modules.floatingbubbleoverlay.overlay

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
import expo.modules.floatingbubbleoverlay.BubbleOptions
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * The bubble and its dismiss target, drawn with WindowManager over any app.
 *
 * - Tap: [onPress]
 * - Drag: the "X" appears at the bottom; releasing close to it calls [onDismiss]
 * - Release away from the "X": the bubble snaps to the nearest side edge
 *
 * Everything is called on the main thread.
 */
internal class BubbleView(
  private val ctx: Context,
  private val options: BubbleOptions,
  private val onPress: () -> Unit,
  private val onDismiss: () -> Unit,
  /** Called once, when the bubble window is actually visible (Android 15 requirement). */
  private val onBecameVisible: () -> Unit,
) {
  private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
  private val density = ctx.resources.displayMetrics.density
  private fun dp(v: Int) = (v * density).roundToInt()

  private val windowType =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

  // Padding around the bubble so the window does not clip its shadow
  private val padding = dp(8)
  private val diameter = dp(options.sizeDp)
  private val side = diameter + padding * 2
  private val touchSlop = ViewConfiguration.get(ctx).scaledTouchSlop
  private val topInset = dp(48)
  private val bottomInset = dp(96)

  private val bubble = ImageView(ctx).apply {
    setImageDrawable(icon())
    scaleType = ImageView.ScaleType.FIT_CENTER
    background = GradientDrawable().apply {
      shape = GradientDrawable.OVAL
      setColor(Color.WHITE)
    }
    outlineProvider = ViewOutlineProvider.BACKGROUND
    clipToOutline = true
    elevation = dp(6).toFloat()
    alpha = options.opacity
    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
  }

  private var reportedVisible = false

  /** FrameLayout that reports when its window becomes visible and when the screen rotates. */
  private inner class Container : FrameLayout(ctx) {
    override fun onWindowVisibilityChanged(visibility: Int) {
      super.onWindowVisibilityChanged(visibility)
      if (visibility == View.VISIBLE && !reportedVisible) {
        reportedVisible = true
        onBecameVisible()
      }
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
      super.onConfigurationChanged(newConfig)
      // New orientation or size: keep the bubble inside the screen
      post { if (attached) settle() }
    }
  }

  private val container = Container().apply {
    clipToPadding = false
    setPadding(padding, padding, padding, padding)
    addView(bubble, FrameLayout.LayoutParams(diameter, diameter))
    contentDescription = "Return to ${appName()}"
    // TalkBack: a double tap opens the app just like a tap
    setOnClickListener { onPress() }
  }

  private val params = WindowManager.LayoutParams(
    side,
    side,
    windowType,
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
    PixelFormat.TRANSLUCENT,
  ).apply { gravity = Gravity.TOP or Gravity.START }

  // Dismiss target: dark circle with an X, bottom center. It does not receive touches.
  private val dismissSize = dp(64)
  private val dismissTarget = FrameLayout(ctx).apply {
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
  private val dismissParams = WindowManager.LayoutParams(
    dismissSize + padding * 2,
    dismissSize + padding * 2,
    windowType,
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
      WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
      WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
    PixelFormat.TRANSLUCENT,
  ).apply { gravity = Gravity.TOP or Gravity.START }
  private val dismissContainer = FrameLayout(ctx).apply {
    clipToPadding = false
    setPadding(padding, padding, padding, padding)
    addView(dismissTarget, FrameLayout.LayoutParams(dismissSize, dismissSize))
  }

  private var animator: ValueAnimator? = null
  private var attached = false
  private var dismissVisible = false

  // Gesture state
  private var touchX = 0f
  private var touchY = 0f
  private var startX = 0
  private var startY = 0
  private var dragging = false
  private var magnetized = false

  /** Adds the bubble to the screen. Throws if the overlay permission is missing. */
  fun attach() {
    if (attached) return
    val s = screenSize()
    params.x = options.initialXDp?.let { dp(it) } ?: (s.x - side)
    params.y = options.initialYDp?.let { dp(it) } ?: (s.y / 3)
    clamp(s)
    container.setOnTouchListener(::onTouch)
    wm.addView(container, params)
    attached = true
    // Entrance: grows from the center
    bubble.scaleX = 0.4f
    bubble.scaleY = 0.4f
    bubble.animate().scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(OvershootInterpolator()).start()
  }

  fun detach() {
    animator?.cancel()
    hideDismissTarget()
    if (attached) {
      runCatching { wm.removeViewImmediate(container) }
      attached = false
    }
  }

  @SuppressLint("ClickableViewAccessibility")
  private fun onTouch(v: View, e: MotionEvent): Boolean {
    when (e.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        animator?.cancel()
        touchX = e.rawX
        touchY = e.rawY
        startX = params.x
        startY = params.y
        dragging = false
        magnetized = false
        bubble.animate().scaleX(0.92f).scaleY(0.92f).setDuration(90).start()
      }
      MotionEvent.ACTION_MOVE -> {
        val dx = e.rawX - touchX
        val dy = e.rawY - touchY
        if (!dragging && hypot(dx, dy) > touchSlop) {
          dragging = true
          showDismissTarget()
        }
        if (dragging) moveTo(startX + dx.roundToInt(), startY + dy.roundToInt())
      }
      MotionEvent.ACTION_UP -> {
        bubble.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
        when {
          !dragging -> v.performClick()
          magnetized -> {
            hideDismissTarget()
            onDismiss()
          }
          else -> {
            hideDismissTarget()
            settle()
          }
        }
      }
      MotionEvent.ACTION_CANCEL -> {
        bubble.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
        hideDismissTarget()
        settle()
      }
    }
    return true
  }

  /** Moves the bubble with the finger; near the X it sticks to it. */
  private fun moveTo(x: Int, y: Int) {
    val centerX = x + side / 2f
    val centerY = y + side / 2f
    val targetX = dismissParams.x + dismissParams.width / 2f
    val targetY = dismissParams.y + dismissParams.height / 2f
    val near = dismissVisible && hypot(centerX - targetX, centerY - targetY) < dp(options.dismissDistanceDp)

    if (near != magnetized) {
      magnetized = near
      val scale = if (near) 1.2f else 1f
      dismissTarget.animate().scaleX(scale).scaleY(scale).setDuration(120).start()
      if (near) container.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    if (magnetized) {
      params.x = (targetX - side / 2f).roundToInt()
      params.y = (targetY - side / 2f).roundToInt()
    } else {
      params.x = x
      params.y = y
    }
    updateLayout()
  }

  /** On release: to the nearest side edge (or just back inside the screen). */
  private fun settle() {
    val s = screenSize()
    val fromX = params.x
    val fromY = params.y
    val toX = when {
      !options.snapToEdge -> fromX.coerceIn(0, s.x - side)
      fromX + side / 2 < s.x / 2 -> 0
      else -> s.x - side
    }
    val toY = fromY.coerceIn(topInset, (s.y - side - bottomInset).coerceAtLeast(topInset))
    animator = ValueAnimator.ofFloat(0f, 1f).apply {
      duration = 260
      interpolator = OvershootInterpolator(0.8f)
      addUpdateListener {
        val t = it.animatedValue as Float
        params.x = (fromX + (toX - fromX) * t).roundToInt()
        params.y = (fromY + (toY - fromY) * t).roundToInt()
        updateLayout()
      }
      start()
    }
  }

  private fun showDismissTarget() {
    if (dismissVisible) return
    val s = screenSize()
    dismissParams.x = (s.x - dismissParams.width) / 2
    dismissParams.y = s.y - dismissParams.height - dp(56)
    runCatching {
      wm.addView(dismissContainer, dismissParams)
      dismissVisible = true
      dismissTarget.scaleX = 0.6f
      dismissTarget.scaleY = 0.6f
      dismissTarget.alpha = 0f
      dismissTarget.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(160).start()
    }
  }

  private fun hideDismissTarget() {
    if (!dismissVisible) return
    runCatching { wm.removeViewImmediate(dismissContainer) }
    dismissVisible = false
    magnetized = false
  }

  private fun updateLayout() {
    if (attached && container.isAttachedToWindow) runCatching { wm.updateViewLayout(container, params) }
  }

  private fun clamp(s: Point) {
    params.x = params.x.coerceIn(0, (s.x - side).coerceAtLeast(0))
    params.y = params.y.coerceIn(topInset, (s.y - side - bottomInset).coerceAtLeast(topInset))
  }

  private fun screenSize(): Point =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      val b = wm.currentWindowMetrics.bounds
      Point(b.width(), b.height())
    } else {
      val m = DisplayMetrics()
      @Suppress("DEPRECATION")
      wm.defaultDisplay.getRealMetrics(m)
      Point(m.widthPixels, m.heightPixels)
    }

  private fun icon(): Drawable {
    val name = options.icon
    if (name != null) {
      val res = ctx.resources
      val id = res.getIdentifier(name, "drawable", ctx.packageName)
        .takeIf { it != 0 } ?: res.getIdentifier(name, "mipmap", ctx.packageName)
      if (id != 0) runCatching { return ctx.getDrawable(id)!! }
    }
    // Fallback: without an app icon (rare) use the system generic one, never crash
    return runCatching { ctx.packageManager.getApplicationIcon(ctx.packageName) as Drawable? }.getOrNull()
      ?: ctx.getDrawable(android.R.drawable.sym_def_app_icon)!!
  }

  private fun appName(): String = ctx.packageManager.getApplicationLabel(ctx.applicationInfo).toString()
}
