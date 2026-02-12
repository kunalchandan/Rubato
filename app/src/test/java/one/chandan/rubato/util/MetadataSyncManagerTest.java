package one.chandan.rubato.util;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MetadataSyncManagerTest {

    @Test
    public void shouldRunNow_forceOverridesAll() {
        boolean result = MetadataSyncManager.shouldRunNow(
                true,
                true,
                true,
                true,
                System.currentTimeMillis(),
                System.currentTimeMillis()
        );
        assertTrue(result);
    }

    @Test
    public void shouldRunNow_blocksInstrumentation() {
        boolean result = MetadataSyncManager.shouldRunNow(
                false,
                true,
                false,
                false,
                0L,
                System.currentTimeMillis()
        );
        assertFalse(result);
    }

    @Test
    public void shouldRunNow_blocksOfflineAndDataSaving() {
        long now = System.currentTimeMillis();
        assertFalse(MetadataSyncManager.shouldRunNow(false, false, true, false, 0L, now));
        assertFalse(MetadataSyncManager.shouldRunNow(false, false, false, true, 0L, now));
    }

    @Test
    public void shouldRunNow_allowsFirstSync() {
        long now = System.currentTimeMillis();
        assertTrue(MetadataSyncManager.shouldRunNow(false, false, false, false, 0L, now));
    }

    @Test
    public void shouldRunNow_enforcesInterval() {
        long now = System.currentTimeMillis();
        long lastSyncTooRecent = now - MetadataSyncManager.MIN_SYNC_INTERVAL_MS + 1;
        long lastSyncOldEnough = now - MetadataSyncManager.MIN_SYNC_INTERVAL_MS - 1;

        assertFalse(MetadataSyncManager.shouldRunNow(false, false, false, false, lastSyncTooRecent, now));
        assertTrue(MetadataSyncManager.shouldRunNow(false, false, false, false, lastSyncOldEnough, now));
    }
}
