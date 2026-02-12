package one.chandan.rubato;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.NoMatchingViewException;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import one.chandan.rubato.ui.activity.MainActivity;
import one.chandan.rubato.util.Preferences;

import org.junit.Test;
import org.junit.runner.RunWith;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class GenreNavigationTest {
    @Test
    public void openGenreCatalogue() {
        TestDeviceUtil.assumeNotAutomotive();
        TestDeviceUtil.grantPostNotificationsIfNeeded();
        Preferences.dontAskForOptimization();
        Preferences.setOnboardingShown(true);
        Preferences.setCoachmarkAddServerPending(false);
        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);
        scenario.moveToState(Lifecycle.State.RESUMED);

        if (!isViewDisplayed(R.id.bottom_navigation)) {
            scenario.close();
            return;
        }

        onView(withId(R.id.libraryFragment)).perform(click());
        onView(withId(R.id.genre_catalogue_text_view_clickable)).perform(scrollTo(), click());
        onView(withId(R.id.genre_catalogue_recycler_view)).check(matches(isDisplayed()));

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
}
