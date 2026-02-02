package one.chandan.rubato;

import static org.junit.Assert.assertFalse;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import one.chandan.rubato.ui.activity.MainActivity;
import one.chandan.rubato.util.Preferences;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MainActivityLaunchTest {
    @Test
    public void launchesMainActivity() {
        TestDeviceUtil.assumeNotAutomotive();
        TestDeviceUtil.grantPostNotificationsIfNeeded();
        Preferences.dontAskForOptimization();
        Preferences.setOnboardingShown(true);
        Preferences.setCoachmarkAddServerPending(false);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> assertFalse(activity.isFinishing()));
        }
    }
}
