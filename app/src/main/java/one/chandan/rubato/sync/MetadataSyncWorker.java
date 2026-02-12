package one.chandan.rubato.sync;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import one.chandan.rubato.util.MetadataSyncManager;
import one.chandan.rubato.util.OfflinePolicy;
import one.chandan.rubato.util.Preferences;

public class MetadataSyncWorker extends Worker {
    public MetadataSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        if (OfflinePolicy.isOffline() || Preferences.isDataSavingMode()) {
            return Result.retry();
        }
        MetadataSyncManager.runSyncNow(getApplicationContext(), false);
        return Result.success();
    }
}
