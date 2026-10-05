package expo.modules.floatingbubbleoverlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log

/**
 * Servicio en primer plano mientras la burbuja esta visible: evita que Android
 * cierre la app en segundo plano y muestra una notificacion para volver al viaje.
 * Si Android no deja iniciarlo, la burbuja sigue funcionando sin el.
 */
class BurbujaServicio : Service() {

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val titulo = intent?.getStringExtra(EXTRA_TITULO)
      ?: packageManager.getApplicationLabel(applicationInfo).toString()
    val texto = intent?.getStringExtra(EXTRA_TEXTO) ?: "Toca para volver a tu viaje"
    try {
      val notificacion = crearNotificacion(titulo, texto)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        startForeground(ID_NOTIFICACION, notificacion, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
      } else {
        startForeground(ID_NOTIFICACION, notificacion)
      }
      enPrimerPlano = true
    } catch (e: Exception) {
      Log.w(BurbujaManager.TAG, "No se pudo pasar a primer plano", e)
    }
    iniciando = false
    // Si la burbuja se oculto mientras el servicio arrancaba, se detiene recien ahora:
    // detenerlo antes de startForeground cierra la app en varias versiones de Android.
    if (!enPrimerPlano || (!BurbujaManager.visible && !mantener)) stopSelf()
    return START_NOT_STICKY
  }

  override fun onDestroy() {
    enPrimerPlano = false
    iniciando = false
    super.onDestroy()
  }

  /** Si el conductor cierra la app desde recientes, la burbuja se va con ella. */
  override fun onTaskRemoved(rootIntent: Intent?) {
    BurbujaManager.ocultar(this)
    stopSelf()
    super.onTaskRemoved(rootIntent)
  }

  private fun crearNotificacion(titulo: String, texto: String): Notification {
    val abrir = packageManager.getLaunchIntentForPackage(packageName)?.let {
      it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
      PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
    val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      if (nm.getNotificationChannel(CANAL) == null) {
        nm.createNotificationChannel(
          NotificationChannel(CANAL, "Viaje en curso", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Aviso mientras la burbuja para volver al viaje esta visible"
            setShowBadge(false)
          },
        )
      }
      Notification.Builder(this, CANAL)
    } else {
      @Suppress("DEPRECATION")
      Notification.Builder(this)
    }
    return builder
      .setContentTitle(titulo)
      .setContentText(texto)
      // Vector monocromo: el icono de la app en color se ve como una mancha en la barra de estado
      .setSmallIcon(R.drawable.burbuja_notificacion)
      .setOngoing(true)
      .setCategory(Notification.CATEGORY_SERVICE)
      .setContentIntent(abrir)
      .build()
  }

  companion object {
    private const val CANAL = "burbuja_viaje"
    private const val ID_NOTIFICACION = 4101
    private const val EXTRA_TITULO = "titulo"
    private const val EXTRA_TEXTO = "texto"

    // Solo se tocan en el hilo principal (iniciar/detener/onStartCommand)
    private var iniciando = false
    private var enPrimerPlano = false

    /**
     * true mientras el conductor esta conectado: el servicio vive aunque no haya burbuja
     * (cerrarla o no tener su permiso no debe dejar la app sin recibir solicitudes).
     */
    @Volatile var mantener = false

    fun iniciar(ctx: Context, opciones: BurbujaOpciones) {
      val intent = Intent(ctx, BurbujaServicio::class.java)
        .putExtra(EXTRA_TITULO, opciones.tituloNotificacion)
        .putExtra(EXTRA_TEXTO, opciones.textoNotificacion)
      if (iniciando || enPrimerPlano) return
      try {
        iniciando = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(intent)
        else ctx.startService(intent)
      } catch (e: Exception) {
        // p. ej. ForegroundServiceStartNotAllowedException: la burbuja sigue sin servicio
        iniciando = false
        Log.w(BurbujaManager.TAG, "No se pudo iniciar el servicio", e)
      }
    }

    fun detener(ctx: Context) {
      if (mantener) return
      // Aun arrancando: onStartCommand vera la burbuja oculta y se detendra solo
      if (iniciando) return
      if (enPrimerPlano) ctx.stopService(Intent(ctx, BurbujaServicio::class.java))
    }
  }
}
