package expo.modules.floatingbubbleoverlay

import android.app.ActivityManager
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import org.robolectric.RuntimeEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowWindowManagerImpl
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowSettings
import java.time.Duration

/**
 * Logica de la burbuja sobre un Android simulado (Robolectric).
 * Correr: cd example/android && ./gradlew :expo-floating-bubble-overlay:testDebugUnitTest
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class BurbujaRoboTest {
  private lateinit var app: Application
  private val wm get() = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
  private fun vistas(): List<View> = Shadow.extract<ShadowWindowManagerImpl>(wm).views
  private var tocadas = 0
  private var cerradas = 0

  private fun importancia(valor: Int) {
    val am = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val info = ActivityManager.RunningAppProcessInfo(app.packageName, Process.myPid(), arrayOf(app.packageName))
    info.importance = valor
    shadowOf(am).setProcesses(listOf(info))
  }

  private fun avanzar(ms: Long = 0) {
    ShadowLooper.idleMainLooper(ms, java.util.concurrent.TimeUnit.MILLISECONDS)
    shadowOf(Looper.getMainLooper()).idle()
  }

  private fun toque(v: View, accion: Int, x: Float, y: Float) {
    val t = SystemClock.uptimeMillis()
    val e = MotionEvent.obtain(t, t, accion, x, y, 0)
    v.dispatchTouchEvent(e)
    e.recycle()
  }

  @Before
  fun preparar() {
    app = RuntimeEnvironment.getApplication()
    ShadowSettings.setCanDrawOverlays(true)
    importancia(ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED)
    // Actividad lanzadora para que getLaunchIntentForPackage devuelva algo
    val main = ActivityInfo().apply { packageName = app.packageName; name = "MainActivity" }
    val filtro = IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
    shadowOf(app.packageManager).apply {
      addOrUpdateActivity(main)
      addIntentFilterForActivity(ComponentName(app.packageName, "MainActivity"), filtro)
    }
    tocadas = 0
    cerradas = 0
    BurbujaManager.oyente = object : BurbujaManager.Oyente {
      override fun alTocar() { tocadas++ }
      override fun alCerrar() { cerradas++ }
    }
    BurbujaManager.ocultar(app)
    avanzar()
  }

  @Test
  fun sinPermisoNoSeMuestra() {
    ShadowSettings.setCanDrawOverlays(false)
    assertFalse(BurbujaManager.tienePermiso(app))
    assertFalse(BurbujaManager.mostrar(app, BurbujaOpciones()))
    avanzar()
    assertTrue(vistas().isEmpty())
  }

  @Test
  fun conPermisoYAppEnSegundoPlanoSeMuestraComoOverlay() {
    assertTrue(BurbujaManager.mostrar(app, BurbujaOpciones()))
    avanzar()
    assertEquals(1, vistas().size)
    val lp = vistas()[0].layoutParams as WindowManager.LayoutParams
    assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, lp.type)
    assertTrue(lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
    // 60dp + 2*8dp de margen, densidad 1 por defecto en Robolectric (mdpi)
    val d = app.resources.displayMetrics.density
    assertEquals(((60 + 16) * d).toInt(), lp.width)
    assertTrue(BurbujaManager.visible)
  }

  @Test
  fun conLaAppVisibleEsperaYLuegoNoSeMuestra() {
    importancia(ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE)
    BurbujaManager.mostrar(app, BurbujaOpciones())
    avanzar(300)
    assertTrue("no debe salir sobre la propia app", vistas().isEmpty())
    avanzar(2000)
    assertTrue(vistas().isEmpty())
    assertFalse(BurbujaManager.visible)
  }

  @Test
  fun siLaAppDejaDeVerseDuranteLaEsperaSeMuestra() {
    importancia(ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE)
    BurbujaManager.mostrar(app, BurbujaOpciones())
    avanzar(300)
    assertTrue(vistas().isEmpty())
    importancia(ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED)
    avanzar(300)
    assertEquals(1, vistas().size)
  }

  @Test
  fun ocultarAntesDeQueAparezcaNoDejaNada() {
    BurbujaManager.mostrar(app, BurbujaOpciones())
    BurbujaManager.ocultar(app)
    avanzar(2000)
    assertTrue(vistas().isEmpty())
  }

  @Test
  fun tocarAbreLaAppYOcultaLaBurbuja() {
    BurbujaManager.mostrar(app, BurbujaOpciones())
    avanzar()
    val v = vistas()[0]
    toque(v, MotionEvent.ACTION_DOWN, 30f, 30f)
    toque(v, MotionEvent.ACTION_UP, 30f, 30f)
    avanzar()
    val abierta = shadowOf(app).nextStartedActivity
    assertNotNull("debe abrir la app", abierta)
    assertEquals("MainActivity", abierta.component?.className)
    assertTrue(abierta.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    assertEquals(1, tocadas)
    assertTrue(vistas().isEmpty())
  }

  @Test
  fun arrastrarMuestraLaXYSoltarLejosLaPegaAlBorde() {
    BurbujaManager.mostrar(app, BurbujaOpciones())
    avanzar()
    val v = vistas()[0]
    toque(v, MotionEvent.ACTION_DOWN, 30f, 30f)
    toque(v, MotionEvent.ACTION_MOVE, -200f, 60f) // arrastra hacia la izquierda
    assertEquals("aparece la X", 2, vistas().size)
    toque(v, MotionEvent.ACTION_UP, -200f, 60f)
    avanzar(500)
    assertEquals("la X se va al soltar", 1, vistas().size)
    assertEquals(0, tocadas)
    val lp = vistas()[0].layoutParams as WindowManager.LayoutParams
    assertTrue("pegada a un borde lateral, x=${lp.x}", lp.x == 0 || lp.x + lp.width == app.resources.displayMetrics.widthPixels)
  }

  @Test
  fun soltarSobreLaXLaCierra() {
    BurbujaManager.mostrar(app, BurbujaOpciones())
    avanzar()
    val v = vistas()[0]
    val lp = v.layoutParams as WindowManager.LayoutParams
    toque(v, MotionEvent.ACTION_DOWN, 30f, 30f)
    toque(v, MotionEvent.ACTION_MOVE, 30f, 60f) // empieza a arrastrar: aparece la X
    val x = vistas().first { it !== v }
    val lpx = x.layoutParams as WindowManager.LayoutParams
    // Lleva el centro de la burbuja al centro de la X (coordenadas crudas = de pantalla en el evento)
    val dx = (lpx.x + lpx.width / 2f) - (lp.x + lp.width / 2f)
    val dy = (lpx.y + lpx.height / 2f) - (lp.y + lp.height / 2f)
    toque(v, MotionEvent.ACTION_MOVE, 30f + dx, 30f + dy)
    toque(v, MotionEvent.ACTION_UP, 30f + dx, 30f + dy)
    avanzar()
    assertEquals(1, cerradas)
    assertEquals(0, tocadas)
    assertTrue(vistas().isEmpty())
    assertFalse(BurbujaManager.visible)
  }

  @Test
  fun servicioPasaAPrimerPlanoConSuNotificacion() {
    BurbujaManager.mostrar(app, BurbujaOpciones(tituloNotificacion = "Viaje en curso"))
    avanzar()
    val controller = Robolectric.buildService(
      BurbujaServicio::class.java,
      Intent(app, BurbujaServicio::class.java).putExtra("titulo", "Viaje en curso"),
    ).create().startCommand(0, 1)
    val servicio = controller.get()
    val n = shadowOf(servicio).lastForegroundNotification
    assertNotNull(n)
    assertEquals("Viaje en curso", n.extras.getString("android.title"))
    assertFalse("sigue vivo mientras la burbuja se ve", shadowOf(servicio).isStoppedBySelf)
  }

  @Test
  fun servicioQueArrancaConLaBurbujaYaOcultaSeDetieneSolo() {
    BurbujaManager.ocultar(app)
    avanzar()
    val servicio = Robolectric.buildService(BurbujaServicio::class.java, Intent(app, BurbujaServicio::class.java))
      .create().startCommand(0, 1).get()
    assertNotNull("primero startForeground", shadowOf(servicio).lastForegroundNotification)
    assertTrue("y luego se detiene", shadowOf(servicio).isStoppedBySelf)
  }

  @Test
  fun servicioMantenidoNoSeDetieneSinBurbuja() {
    BurbujaServicio.mantener = true
    try {
      BurbujaManager.ocultar(app)
      avanzar()
      val servicio = Robolectric.buildService(BurbujaServicio::class.java, Intent(app, BurbujaServicio::class.java))
        .create().startCommand(0, 1).get()
      assertNotNull(shadowOf(servicio).lastForegroundNotification)
      assertFalse("vive sin burbuja mientras este conectado", shadowOf(servicio).isStoppedBySelf)
    } finally {
      BurbujaServicio.mantener = false
    }
  }

  @Test
  fun conServicioEnPrimerPlanoYAppMinimizadaSeMuestra() {
    importancia(ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE)
    assertTrue(BurbujaManager.mostrar(app, BurbujaOpciones()))
    avanzar()
    assertEquals(1, vistas().size)
    assertTrue(BurbujaManager.visible)
  }

  @Test
  fun abrirAppConPermisoTraeLaAppAlFrente() {
    assertTrue(BurbujaManager.abrirApp(app))
    val abierta = shadowOf(app).nextStartedActivity
    assertEquals("MainActivity", abierta.component?.className)
    assertTrue(abierta.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
  }

  @Test
  fun abrirAppSinPermisoNoHaceNada() {
    ShadowSettings.setCanDrawOverlays(false)
    assertFalse(BurbujaManager.abrirApp(app))
    assertEquals(null, shadowOf(app).nextStartedActivity)
  }

  @Test
  fun programarAperturaAbreLaAppTrasElTiempo() {
    BurbujaManager.programarApertura(app, 3)
    avanzar(1000)
    assertEquals(null, shadowOf(app).nextStartedActivity)
    avanzar(2000)
    val abierta = shadowOf(app).nextStartedActivity
    assertNotNull(abierta)
    assertEquals("MainActivity", abierta.component?.className)
  }

  @Test
  fun cancelarAperturaEvitaQueSeAbra() {
    BurbujaManager.programarApertura(app, 3)
    avanzar(1000)
    BurbujaManager.cancelarApertura()
    avanzar(3000)
    assertEquals(null, shadowOf(app).nextStartedActivity)
  }
}
