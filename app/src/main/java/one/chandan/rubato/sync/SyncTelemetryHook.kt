package one.chandan.rubato.sync

import one.chandan.rubato.util.TelemetryLogger

object SyncTelemetryHook : SyncStateListener {
    override fun onStateChanged(previous: SyncState?, current: SyncState) {
        val wasActive = previous?.active ?: false
        if (!wasActive && current.active) {
            TelemetryLogger.logEvent(
                screen = "sync",
                action = "start",
                detail = current.stage,
                durationMs = 0L,
                source = "sync"
            )
            return
        }
        if (wasActive && !current.active) {
            val startedAt = previous?.startedAt?.takeIf { it > 0 } ?: current.startedAt
            val duration = if (startedAt > 0) {
                System.currentTimeMillis() - startedAt
            } else {
                0L
            }
            TelemetryLogger.logEvent(
                screen = "sync",
                action = "complete",
                detail = previous?.stage ?: current.stage,
                durationMs = duration,
                source = "sync"
            )
        }
    }
}
