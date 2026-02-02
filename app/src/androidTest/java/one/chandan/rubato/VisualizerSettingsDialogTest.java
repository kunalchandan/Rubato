package one.chandan.rubato;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.not;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import one.chandan.rubato.ui.activity.MainActivity;
import one.chandan.rubato.util.Preferences;

import org.junit.Test;
import org.junit.runner.RunWith;

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
            scenario.onActivity(activity -> activity.navController.navigate(R.id.settingsFragment));

            onView(withText(R.string.settings_visualizer_title)).perform(click());
            onView(withId(R.id.visualizer_preview)).check(matches(isDisplayed()));

            onView(withId(R.id.visualizer_style_line_chip)).perform(click());
            onView(withId(R.id.visualizer_peak_caps_switch)).check(matches(not(isEnabled())));

            onView(withId(R.id.visualizer_style_bars_chip)).perform(click());
            onView(withId(R.id.visualizer_peak_caps_switch)).check(matches(isEnabled()));
        }
    }
}
