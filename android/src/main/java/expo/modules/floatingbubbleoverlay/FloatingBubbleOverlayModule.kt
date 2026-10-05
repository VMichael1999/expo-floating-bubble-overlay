package expo.modules.floatingbubbleoverlay

import android.content.Context
import expo.modules.kotlin.exception.Exceptions
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

/** Puente con JS. La logica esta en [BurbujaManager]. */
class FloatingBubbleOverlayModule : Module() {
  private val contexto: Context
    get() = appContext.reactContext ?: throw Exceptions.ReactContextLost()

  override fun definition() = ModuleDefinition {
    Name("FloatingBubbleOverlay")

    Events("onTocar", "onCerrar")

    OnCreate {
      BurbujaManager.oyente = object : BurbujaManager.Oyente {
        override fun alTocar() = sendEvent("onTocar", emptyMap<String, Any?>())
        override fun alCerrar() = sendEvent("onCerrar", emptyMap<String, Any?>())
      }
    }

    OnDestroy {
      BurbujaManager.oyente = null
    }

    Function("tienePermiso") {
      BurbujaManager.tienePermiso(contexto)
    }

    Function("abrirAjustesPermiso") {
      BurbujaManager.abrirAjustesPermiso(contexto)
    }

    Function("mostrar") { opciones: Map<String, Any?> ->
      BurbujaManager.mostrar(contexto, BurbujaOpciones.desdeMapa(opciones))
    }

    Function("mantenerActiva") { opciones: Map<String, Any?> ->
      BurbujaManager.mantenerActiva(contexto, BurbujaOpciones.desdeMapa(opciones))
    }

    Function("soltarActiva") {
      BurbujaManager.soltarActiva(contexto)
    }

    Function("ocultar") {
      BurbujaManager.ocultar(contexto)
    }

    Function("abrirApp") {
      BurbujaManager.abrirApp(contexto)
    }

    Function("programarApertura") { segundos: Int ->
      BurbujaManager.programarApertura(contexto, segundos)
    }

    Function("cancelarApertura") {
      BurbujaManager.cancelarApertura()
    }

    Function("estaVisible") {
      BurbujaManager.visible
    }
  }
}
