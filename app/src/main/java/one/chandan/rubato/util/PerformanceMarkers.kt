package one.chandan.rubato.util

import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap

object PerformanceMarkers {
    private val marks = ConcurrentHashMap<String, Long>()

    @JvmStatic
    fun start(key: String) {
        marks[key] = SystemClock.elapsedRealtime()
    }

    @JvmStatic
    fun end(key: String): Long? {
        val start = marks.remove(key) ?: return null
        return SystemClock.elapsedRealtime() - start
    }

    @JvmStatic
    fun endAndLog(
        key: String,
        screen: String?,
        action: String,
        detail: String? = null,
        source: String? = null,
        error: String? = null
    ) {
        val duration = end(key) ?: return
        TelemetryLogger.logEvent(screen, action, detail, duration, source, error)
    }
}
