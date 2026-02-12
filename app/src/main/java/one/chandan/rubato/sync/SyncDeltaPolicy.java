package one.chandan.rubato.sync;

public final class SyncDeltaPolicy {
    private static final long FULL_REFRESH_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000L;

    private SyncDeltaPolicy() {
    }

    public static boolean shouldForceFull(long lastFullSync) {
        if (lastFullSync <= 0) return true;
        return System.currentTimeMillis() - lastFullSync >= FULL_REFRESH_INTERVAL_MS;
    }
}
