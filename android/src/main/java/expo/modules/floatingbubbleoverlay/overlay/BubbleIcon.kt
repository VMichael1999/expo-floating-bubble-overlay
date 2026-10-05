package expo.modules.floatingbubbleoverlay.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import expo.modules.floatingbubbleoverlay.BubbleController
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Where the bubble image comes from, parsed from the `icon` option. */
sealed class BubbleIcon {
  /** No `icon`: the app's own launcher icon (default). */
  object AppIcon : BubbleIcon()

  /** Name of a native drawable/mipmap, e.g. "ic_bubble" (also what `require()` resolves to in release builds). */
  data class Resource(val name: String) : BubbleIcon()

  /** http(s) URL, e.g. a remote image or the Metro dev server in development builds. */
  data class Remote(val url: String) : BubbleIcon()

  /** Local image file (file:// URI or absolute path). */
  data class LocalFile(val path: String) : BubbleIcon()

  /** Base64 data URI, e.g. "data:image/png;base64,...". */
  data class Base64Data(val data: String) : BubbleIcon()

  companion object {
    fun parse(icon: String?): BubbleIcon {
      val value = icon?.trim()
      return when {
        value.isNullOrEmpty() -> AppIcon
        value.startsWith("http://") || value.startsWith("https://") -> Remote(value)
        value.startsWith("file://") -> LocalFile(value.removePrefix("file://"))
        value.startsWith("/") -> LocalFile(value)
        value.startsWith("data:") -> Base64Data(value.substringAfter("base64,", ""))
        else -> Resource(value)
      }
    }

    /** The app's launcher icon; the generic system one if the app has none (never crashes). */
    fun appIcon(ctx: Context): Drawable =
      runCatching { ctx.packageManager.getApplicationIcon(ctx.packageName) as Drawable? }.getOrNull()
        ?: ctx.getDrawable(android.R.drawable.sym_def_app_icon)!!

    /** A native resource, or null if it does not exist. Looks in drawable, then mipmap. */
    fun resource(ctx: Context, name: String): Drawable? {
      val res = ctx.resources
      val id = res.getIdentifier(name, "drawable", ctx.packageName)
        .takeIf { it != 0 } ?: res.getIdentifier(name, "mipmap", ctx.packageName)
      return if (id != 0) runCatching { ctx.getDrawable(id) }.getOrNull() else null
    }

    private const val MAX_BYTES = 5 * 1024 * 1024
    private const val TIMEOUT_MS = 10_000

    private val worker = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Loads an image that is not a native resource off the main thread and delivers it on the
     * main thread, downsampled to about [sizePx]. Delivers null if it cannot be loaded.
     */
    fun load(ctx: Context, icon: BubbleIcon, sizePx: Int, onLoaded: (Drawable?) -> Unit) {
      worker.execute {
        val bitmap = runCatching { decode(bytes(icon), sizePx) }
          .onFailure { Log.w(BubbleController.TAG, "Could not load the bubble icon", it) }
          .getOrNull()
        mainHandler.post { onLoaded(bitmap?.let { BitmapDrawable(ctx.resources, it) }) }
      }
    }

    private fun bytes(icon: BubbleIcon): ByteArray? = when (icon) {
      is Remote -> {
        val conn = URL(icon.url).openConnection() as HttpURLConnection
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        try {
          if (conn.responseCode !in 200..299) null
          else conn.inputStream.use { readLimited(it) }
        } finally {
          conn.disconnect()
        }
      }
      is LocalFile -> File(icon.path).takeIf { it.isFile && it.length() <= MAX_BYTES }?.readBytes()
      is Base64Data -> Base64.decode(icon.data, Base64.DEFAULT)
      else -> null
    }

    /** Reads the whole stream, or null if it is larger than [MAX_BYTES]. Works on every API level. */
    private fun readLimited(input: InputStream): ByteArray? {
      val out = ByteArrayOutputStream()
      val buffer = ByteArray(16 * 1024)
      while (true) {
        val n = input.read(buffer)
        if (n < 0) return out.toByteArray()
        if (out.size() + n > MAX_BYTES) return null
        out.write(buffer, 0, n)
      }
    }

    /** Decodes downsampled so that a large image does not use more memory than the bubble needs. */
    private fun decode(bytes: ByteArray?, sizePx: Int): Bitmap? {
      if (bytes == null || bytes.isEmpty()) return null
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
      if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
      var sample = 1
      while (bounds.outWidth / (sample * 2) >= sizePx && bounds.outHeight / (sample * 2) >= sizePx) sample *= 2
      return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }
  }
}
