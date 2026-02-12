package one.chandan.rubato.sync

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import one.chandan.rubato.util.OfflinePolicy
import one.chandan.rubato.util.Preferences

class CoverArtPrefetchWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {
    override fun doWork(): Result {
        if (OfflinePolicy.isOffline()) return Result.retry()
        if (Preferences.isDataSavingMode()) return Result.retry()
        val remaining = CoverArtPrefetchQueue.processQueue(applicationContext)
        return if (remaining > 0) Result.retry() else Result.success()
    }
}
