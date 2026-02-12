package one.chandan.rubato;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import one.chandan.rubato.ui.activity.MainActivity;
import one.chandan.rubato.util.Preferences;
import one.chandan.rubato.util.TelemetryLogger;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class TelemetryExportTest {
    @Test
    public void exportTelemetryToFile() throws Exception {
        Preferences.setLocalTelemetryEnabled(true);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            TelemetryLogger.logEvent("TelemetryTest", "export", "instrumentation", 10L, TelemetryLogger.SOURCE_UI, null);
            Thread.sleep(800);
        }

        CountDownLatch latch = new CountDownLatch(1);
        final File[] exported = new File[1];
        Context context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getTargetContext();
        TelemetryLogger.exportLatest(context, 100, file -> {
            exported[0] = file;
            latch.countDown();
        });

        assertTrue("Export callback timed out", latch.await(10, TimeUnit.SECONDS));
        assertNotNull("Exported file is null", exported[0]);
        assertTrue("Exported file missing", exported[0].exists());
        assertTrue("Exported file empty", exported[0].length() > 0);
    }
}
