package one.chandan.rubato;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import androidx.test.espresso.NoMatchingViewException;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.core.app.ActivityScenario;
import androidx.lifecycle.Lifecycle;

import one.chandan.rubato.ui.activity.MainActivity;
import one.chandan.rubato.util.Preferences;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertTrue;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class SmokeTest {
    @Test
    public void launchAndNavigate() {
        TestDeviceUtil.assumeNotAutomotive();
        TestDeviceUtil.grantPostNotificationsIfNeeded();
        Preferences.dontAskForOptimization();
        Preferences.setOnboardingShown(true);
        Preferences.setCoachmarkAddServerPending(false);
        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);
        scenario.moveToState(Lifecycle.State.RESUMED);

        onView(withId(R.id.drawer_layout)).check(matches(isDisplayed()));

        boolean hasBottomNav = isViewDisplayed(R.id.bottom_navigation);
        boolean hasLogin = isViewDisplayed(R.id.login_source_subsonic_card);

        assertTrue("Expected bottom navigation or login sources to be visible.", hasBottomNav || hasLogin);

        if (hasBottomNav) {
            clickIfExists(R.id.libraryFragment);
            clickIfExists(R.id.downloadFragment);
            clickIfExists(R.id.homeFragment);
        }

        scenario.close();
    }

    private boolean isViewDisplayed(int viewId) {
        try {
            onView(withId(viewId)).check(matches(isDisplayed()));
            return true;
        } catch (NoMatchingViewException | AssertionError e) {
            return false;
        }
    }

    private void clickIfExists(int viewId) {
        try {
            onView(withId(viewId)).perform(click());
        } catch (NoMatchingViewException | AssertionError ignored) {
        }
    }
}
