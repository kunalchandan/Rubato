package one.chandan.rubato.sync;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SyncDeltaPolicyTest {

    @Test
    public void shouldForceFull_whenNeverSynced() {
        assertTrue(SyncDeltaPolicy.shouldForceFull(0));
    }

    @Test
    public void shouldNotForceFull_whenRecent() {
        long now = System.currentTimeMillis();
        long oneDayAgo = now - (24L * 60 * 60 * 1000L);
        assertFalse(SyncDeltaPolicy.shouldForceFull(oneDayAgo));
    }

    @Test
    public void shouldForceFull_whenStale() {
        long now = System.currentTimeMillis();
        long eightDaysAgo = now - (8L * 24 * 60 * 60 * 1000L);
        assertTrue(SyncDeltaPolicy.shouldForceFull(eightDaysAgo));
    }
}
