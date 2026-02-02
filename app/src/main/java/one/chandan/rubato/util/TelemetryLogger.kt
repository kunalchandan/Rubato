package one.chandan.rubato.util

import android.util.Log
import one.chandan.rubato.database.AppDatabase
import one.chandan.rubato.model.TelemetryEvent
import java.util.concurrent.Executors

object TelemetryLogger {
    private const val TAG = "Telemetry"
    private val executor = Executors.newSingleThreadExecutor()

    @JvmStatic
    fun logEvent(screen: String?, action: String, detail: String? = null, durationMs: Long = 0L) {
        if (!Preferences.isLocalTelemetryEnabled()) return

        Log.d(TAG, "screen=$screen action=$action detail=$detail durationMs=$durationMs")

        val event = TelemetryEvent(
            timestamp = System.currentTimeMillis(),
            screen = screen,
            action = action,
            detail = detail,
            durationMs = durationMs
        )

        executor.execute {
            AppDatabase.getInstance().telemetryEventDao().insert(event)
        }
    }
}
