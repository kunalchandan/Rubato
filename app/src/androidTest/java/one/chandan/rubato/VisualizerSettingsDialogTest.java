package one.chandan.rubato;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import one.chandan.rubato.ui.activity.MainActivity;
import one.chandan.rubato.ui.dialog.VisualizerSettingsDialog;
import one.chandan.rubato.util.Preferences;

import org.junit.Test;
import org.junit.runner.RunWith;
import android.view.View;

@RunWith(AndroidJUnit4.class)
public class VisualizerSettingsDialogTest {
    @Test
    public void visualizerSettingsShowsPreviewAndTogglesPeakCaps() {
        TestDeviceUtil.assumeNotAutomotive();
        TestDeviceUtil.grantPostNotificationsIfNeeded();
        Preferences.dontAskForOptimization();
        Preferences.setOnboardingShown(true);
        Preferences.setCoachmarkAddServerPending(false);
        Preferences.setPassword("test");
        Preferences.setVisualizerEnabled(true);

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                VisualizerSettingsDialog dialog = new VisualizerSettingsDialog();
                dialog.show(activity.getSupportFragmentManager(), "VisualizerSettingsDialog");
                activity.getSupportFragmentManager().executePendingTransactions();
                android.app.Dialog dialogWindow = dialog.getDialog();
                org.junit.Assert.assertNotNull(dialogWindow);
                View preview = dialogWindow.findViewById(R.id.visualizer_preview);
                View lineChip = dialogWindow.findViewById(R.id.visualizer_style_line_chip);
                View barsChip = dialogWindow.findViewById(R.id.visualizer_style_bars_chip);
                View peakSwitch = dialogWindow.findViewById(R.id.visualizer_peak_caps_switch);
                org.junit.Assert.assertNotNull(preview);
                org.junit.Assert.assertNotNull(lineChip);
                org.junit.Assert.assertNotNull(barsChip);
                org.junit.Assert.assertNotNull(peakSwitch);

                lineChip.performClick();
                org.junit.Assert.assertFalse(peakSwitch.isEnabled());

                barsChip.performClick();
                org.junit.Assert.assertTrue(peakSwitch.isEnabled());
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        }
    }
}
