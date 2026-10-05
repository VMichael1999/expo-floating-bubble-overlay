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

    Events("onPress", "onDismiss")

    OnCreate {
      BubbleController.listener = object : BubbleController.Listener {
        override fun onPress() = sendEvent("onPress", emptyMap<String, Any?>())
        override fun onDismiss() = sendEvent("onDismiss", emptyMap<String, Any?>())
      }
    }

    OnDestroy {
      BubbleController.listener = null
    }

    Function("hasOverlayPermission") {
      BubbleController.hasOverlayPermission(context)
    }

    Function("openOverlayPermissionSettings") {
      BubbleController.openOverlayPermissionSettings(context)
    }

    Function("enable") { options: Map<String, Any?> ->
      BubbleController.enable(context, BubbleOptions.fromMap(options))
    }

    Function("startKeepAlive") { options: Map<String, Any?> ->
      BubbleController.startKeepAlive(context, BubbleOptions.fromMap(options))
    }

    Function("stopKeepAlive") {
      BubbleController.stopKeepAlive(context)
    }

    Function("disable") {
      BubbleController.disable(context)
    }

    Function("bringAppToForeground") {
      BubbleController.bringAppToForeground(context)
    }

    Function("scheduleBringAppToForeground") { seconds: Int ->
      BubbleController.scheduleBringAppToForeground(context, seconds)
    }

    Function("cancelScheduledBringAppToForeground") {
      BubbleController.cancelScheduledBringAppToForeground()
    }

    Function("isEnabled") {
      BubbleController.isEnabled
    }

    Function("isVisible") {
      BubbleController.isVisible
    }
  }
}
