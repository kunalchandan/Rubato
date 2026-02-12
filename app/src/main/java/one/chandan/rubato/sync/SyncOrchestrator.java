package one.chandan.rubato.sync;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import one.chandan.rubato.util.MetadataSyncManager;

public final class SyncOrchestrator {
    private static final String WORK_NAME_METADATA_DAILY = "metadata_sync_daily";
    private static final AtomicBoolean HOOKS_READY = new AtomicBoolean(false);

    private SyncOrchestrator() {
    }

    public static void startOnLaunch(Context context) {
        if (context == null) return;
        if (one.chandan.rubato.util.TestRunUtil.isInstrumentationTest()) return;
        ensureHooks();
        scheduleDaily(context);
        MetadataSyncManager.startIfNeeded(context);
    }

    public static void startOnConnectivity(Context context) {
        if (context == null) return;
        if (one.chandan.rubato.util.TestRunUtil.isInstrumentationTest()) return;
        ensureHooks();
        MetadataSyncManager.startIfNeeded(context);
    }

    public static void scheduleDaily(Context context) {
        if (context == null) return;
        if (one.chandan.rubato.util.TestRunUtil.isInstrumentationTest()) return;
        ensureHooks();
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                MetadataSyncWorker.class,
                24, TimeUnit.HOURS
        ).setConstraints(constraints).build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_METADATA_DAILY,
                ExistingPeriodicWorkPolicy.KEEP,
                request
        );
    }

    public static LiveData<SyncState> getSyncState() {
        ensureHooks();
        return SyncStateStore.getState();
    }

    public static SyncState getCurrentState() {
        ensureHooks();
        return SyncStateStore.getCurrentState();
    }

    private static void ensureHooks() {
        if (!HOOKS_READY.compareAndSet(false, true)) return;
        SyncStateStore.addListener(SyncTelemetryHook.INSTANCE);
        SyncStateStore.notifyChanged();
    }
}
