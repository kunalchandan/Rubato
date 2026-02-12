package one.chandan.rubato.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.appcompat.content.res.AppCompatResources
import com.bumptech.glide.Glide
import one.chandan.rubato.R
import one.chandan.rubato.util.AutoLog
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale

class AutoArtworkProvider : ContentProvider() {
    companion object {
        const val AUTHORITY = "one.chandan.rubato.auto.artwork"
        private const val TYPE_MIX = "mix"
        private const val TYPE_COVER = "cover"
        private const val DEFAULT_SIZE = 512

        @JvmStatic
        fun buildMixUri(baseUri: Uri?, size: Int = DEFAULT_SIZE): Uri? {
            val raw = baseUri?.toString()?.takeIf { it.isNotBlank() } ?: return null
            return Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .appendPath(TYPE_MIX)
                .appendQueryParameter("art", raw)
                .appendQueryParameter("size", size.toString())
                .build()
        }

        @JvmStatic
        fun buildCoverUri(baseUri: Uri?, size: Int = DEFAULT_SIZE): Uri? {
            if (baseUri == null) return null
            if (baseUri.scheme == "content" && baseUri.authority == AUTHORITY) {
                return baseUri
            }
            val raw = baseUri.toString().takeIf { it.isNotBlank() } ?: return null
            return Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .appendPath(TYPE_COVER)
                .appendQueryParameter("art", raw)
                .appendQueryParameter("size", size.toString())
                .build()
        }
    }

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = "image/png"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        if (!mode.contains("r")) {
            throw FileNotFoundException("Read-only provider")
        }
        val context = context ?: throw FileNotFoundException("Missing context")
        val type = uri.pathSegments.firstOrNull() ?: throw FileNotFoundException("Missing type")
        val art = uri.getQueryParameter("art") ?: throw FileNotFoundException("Missing art")
        val size = uri.getQueryParameter("size")?.toIntOrNull()?.coerceIn(128, 2048) ?: DEFAULT_SIZE
        if (!isAllowedScheme(art)) {
            AutoLog.warn("auto_art_rejected", mapOf("type" to type, "art" to art))
            throw FileNotFoundException("Unsupported art uri")
        }
        val cacheFile = cacheFile(context, type, art, size)
        if (!cacheFile.exists()) {
            val generated = when (type) {
                TYPE_MIX -> generateMixArtwork(context, Uri.parse(art), size)
                TYPE_COVER -> generateCoverArtwork(context, Uri.parse(art), size)
                else -> null
            }
            if (generated != null) {
                saveBitmap(cacheFile, generated)
            } else {
                AutoLog.warn("auto_art_missing", mapOf("type" to type, "art" to art))
                throw FileNotFoundException("Artwork unavailable")
            }
        } else {
            AutoLog.event("auto_art_cache_hit", mapOf("type" to type, "size" to size))
        }
        AutoLog.event("auto_art_request", mapOf("type" to type, "size" to size))
        return ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    private fun generateMixArtwork(context: Context, artUri: Uri, size: Int): Bitmap? {
        val base = loadBitmap(context, artUri, size) ?: return null
        val overlay = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(overlay)
        canvas.drawBitmap(base, 0f, 0f, null)

        val badgeRadius = size * 0.18f
        val badgeMargin = size * 0.06f
        val cx = size - badgeMargin - badgeRadius
        val cy = size - badgeMargin - badgeRadius
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xB3000000.toInt() }
        canvas.drawOval(
            RectF(
                cx - badgeRadius,
                cy - badgeRadius,
                cx + badgeRadius,
                cy + badgeRadius
            ),
            badgePaint
        )

        val icon = AppCompatResources.getDrawable(context, R.drawable.ic_mix_from_here)
        if (icon != null) {
            val iconSize = (badgeRadius * 1.1f).toInt()
            val half = iconSize / 2
            icon.setBounds(
                (cx - half).toInt(),
                (cy - half).toInt(),
                (cx + half).toInt(),
                (cy + half).toInt()
            )
            icon.colorFilter = PorterDuffColorFilter(0xFFFFFFFF.toInt(), PorterDuff.Mode.SRC_IN)
            icon.draw(canvas)
        }
        AutoLog.event("auto_art_generated", mapOf("type" to TYPE_MIX, "size" to size))
        return overlay
    }

    private fun generateCoverArtwork(context: Context, artUri: Uri, size: Int): Bitmap? {
        val base = loadBitmap(context, artUri, size) ?: return null
        AutoLog.event("auto_art_generated", mapOf("type" to TYPE_COVER, "size" to size))
        return base
    }

    private fun loadBitmap(context: Context, uri: Uri, size: Int): Bitmap? {
        if (uri.scheme == "android.resource") {
            val resourceBitmap = loadResourceBitmap(context, uri, size)
            if (resourceBitmap != null) return resourceBitmap
        }
        return try {
            Glide.with(context)
                .asBitmap()
                .load(uri)
                .centerCrop()
                .submit(size, size)
                .get()
        } catch (e: Exception) {
            AutoLog.warn("auto_art_load_failed", mapOf("uri" to uri.toString()), e)
            null
        }
    }

    private fun loadResourceBitmap(context: Context, uri: Uri, size: Int): Bitmap? {
        val drawable = loadResourceDrawable(context, uri) ?: return null
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return bitmap
    }

    private fun loadResourceDrawable(context: Context, uri: Uri): android.graphics.drawable.Drawable? {
        val authority = uri.authority ?: context.packageName
        val segments = uri.pathSegments ?: return null
        val res = context.resources
        val resId = when {
            segments.size >= 2 -> res.getIdentifier(segments[1], segments[0], authority)
            segments.size == 1 -> {
                val raw = segments[0]
                raw.toIntOrNull() ?: res.getIdentifier(raw, "drawable", authority)
            }
            else -> 0
        }
        if (resId == 0) return null
        return AppCompatResources.getDrawable(context, resId)
    }

    private fun cacheFile(context: Context, type: String, art: String, size: Int): File {
        val dir = File(context.cacheDir, "auto-artwork")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val key = hashKey("$type|$size|$art")
        return File(dir, "auto_${type}_$key.png")
    }

    private fun saveBitmap(file: File, bitmap: Bitmap) {
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (e: Exception) {
            AutoLog.warn("auto_art_save_failed", mapOf("file" to file.absolutePath), e)
        }
    }

    private fun hashKey(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        val builder = StringBuilder()
        for (byte in digest) {
            builder.append(String.format(Locale.US, "%02x", byte))
        }
        return builder.toString()
    }

    private fun isAllowedScheme(raw: String): Boolean {
        val scheme = runCatching { Uri.parse(raw).scheme }.getOrNull() ?: return false
        return scheme == "http" || scheme == "https" || scheme == "content" ||
            scheme == "android.resource" || scheme == "file"
    }
}
