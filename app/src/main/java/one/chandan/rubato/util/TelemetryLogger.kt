package one.chandan.rubato.util

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import one.chandan.rubato.database.AppDatabase
import one.chandan.rubato.model.TelemetryEvent
import java.io.File
import java.io.FileWriter
import java.util.concurrent.ExecutorService

object TelemetryLogger {
    private const val TAG = "Telemetry"
    private val executor: ExecutorService = AppExecutors.telemetry()
    private val gson = Gson()

    const val SOURCE_UI = "ui"
    const val SOURCE_LIST = "list"
    const val SOURCE_STARTUP = "startup"

    @JvmStatic
    @JvmOverloads
    fun logEvent(
        screen: String?,
        action: String,
        detail: String? = null,
        durationMs: Long = 0L,
        source: String? = null,
        error: String? = null
    ) {
        if (!Preferences.isLocalTelemetryEnabled()) return

        Log.d(TAG, "screen=$screen action=$action detail=$detail durationMs=$durationMs source=$source error=$error")

        val event = TelemetryEvent(
            timestamp = System.currentTimeMillis(),
            screen = screen,
            action = action,
            detail = detail,
            durationMs = durationMs,
            source = source,
            error = error
        )

        executor.execute {
            AppDatabase.getInstance().telemetryEventDao().insert(event)
        }
    }

    interface ExportCallback {
        fun onComplete(file: File?)
    }

    @JvmStatic
    @JvmOverloads
    fun exportLatest(context: Context, limit: Int = 2000, callback: ExportCallback? = null) {
        executor.execute {
            try {
                val dao = AppDatabase.getInstance().telemetryEventDao()
                val events = dao.getLatest(limit)
                val dir = File(context.getExternalFilesDir(null), "telemetry")
                if (!dir.exists()) {
                    dir.mkdirs()
                }
                val file = File(dir, "telemetry_${System.currentTimeMillis()}.jsonl")
                FileWriter(file).use { writer ->
                    events.forEach { event ->
                        writer.append(gson.toJson(event)).append('\n')
                    }
                }
                callback?.onComplete(file)
            } catch (ex: Exception) {
                Log.e(TAG, "exportLatest failed", ex)
                callback?.onComplete(null)
            }
        }
    }
}
