package expo.modules.floatingbubbleoverlay

import android.content.Context
import expo.modules.kotlin.exception.Exceptions
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

/** Bridge with JS. The logic lives in [BubbleController]. */
class FloatingBubbleOverlayModule : Module() {
  private val context: Context
    get() = appContext.reactContext ?: throw Exceptions.ReactContextLost()

  override fun definition() = ModuleDefinition {
    Name("FloatingBubbleOverlay")

    Events("onTocar", "onCerrar")

    OnCreate {
      BubbleController.listener = object : BubbleController.Listener {
        override fun onPress() = sendEvent("onTocar", emptyMap<String, Any?>())
        override fun onDismiss() = sendEvent("onCerrar", emptyMap<String, Any?>())
      }
    }

    OnDestroy {
      BubbleController.listener = null
    }

    Function("tienePermiso") {
      BubbleController.hasOverlayPermission(context)
    }

    Function("abrirAjustesPermiso") {
      BubbleController.openOverlayPermissionSettings(context)
    }

    Function("mostrar") { options: Map<String, Any?> ->
      BubbleController.show(context, BubbleOptions.fromMap(options))
    }

    Function("mantenerActiva") { options: Map<String, Any?> ->
      BubbleController.startKeepAlive(context, BubbleOptions.fromMap(options))
    }

    Function("soltarActiva") {
      BubbleController.stopKeepAlive(context)
    }

    Function("ocultar") {
      BubbleController.hide(context)
    }

    Function("abrirApp") {
      BubbleController.bringAppToForeground(context)
    }

    Function("programarApertura") { seconds: Int ->
      BubbleController.scheduleBringAppToForeground(context, seconds)
    }

    Function("cancelarApertura") {
      BubbleController.cancelScheduledBringAppToForeground()
    }

    Function("estaVisible") {
      BubbleController.isVisible
    }
  }
}
