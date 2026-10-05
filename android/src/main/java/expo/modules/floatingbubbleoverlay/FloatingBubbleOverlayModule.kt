package expo.modules.floatingbubbleoverlay

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class FloatingBubbleOverlayModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("FloatingBubbleOverlay")

    Events("onChange")

    Function("hello") {
      "Hello world! 👋"
    }
  }
}
