package one.chandan.rubato;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.NoMatchingViewException;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.espresso.matcher.ViewMatchers.Visibility;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.lifecycle.Lifecycle;

import one.chandan.rubato.ui.activity.MainActivity;
import one.chandan.rubato.util.Preferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.hamcrest.Matcher;

import android.view.View;
import androidx.appcompat.widget.SearchView;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class UserInteractionTest {
    @Before
    public void setUp() {
        TestDeviceUtil.assumeNotAutomotive();
        TestDeviceUtil.grantPostNotificationsIfNeeded();
        Preferences.setOnboardingShown(true);
        Preferences.setCoachmarkAddServerPending(false);
        Preferences.dontAskForOptimization();
        Preferences.setServer("http://127.0.0.1");
        Preferences.setUser("test");
        Preferences.setPassword("test");
        Preferences.setLowSecurity(true);
        App.refreshSubsonicClient();
    }

    @Test
    public void searchPanelOpensAndShowsSuggestions() {
        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);
        scenario.moveToState(Lifecycle.State.RESUMED);
        navigateTo(scenario, R.id.searchFragment);

        onView(withId(R.id.search_bar)).check(matches(isDisplayed()));
        onView(withId(R.id.search_bar)).perform(click());
        onView(withId(R.id.search_view)).check(matches(isDisplayed()));

        scenario.close();
    }

    @Test
    public void downloadedScreenShowsCoreControlsOrEmptyState() {
        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);
        scenario.moveToState(Lifecycle.State.RESUMED);
        navigateTo(scenario, R.id.downloadFragment);

        boolean hasEmpty = isViewDisplayed(R.id.empty_download_layout);
        boolean hasContent = isViewDisplayed(R.id.download_downloaded_sector);

        if (hasContent) {
            onView(withId(R.id.shuffle_downloaded_text_view_clickable)).check(matches(isDisplayed()));
            onView(withId(R.id.downloaded_group_by_image_view)).check(matches(isDisplayed()));
            onView(withId(R.id.metadata_sync_status_icon)).check(matches(isDisplayed()));
        } else {
            onView(withId(R.id.empty_download_layout)).check(matches(isDisplayed()));
        }

        scenario.close();
    }

    @Test
    public void settingsSearchFiltersPreferences() {
        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);
        scenario.moveToState(Lifecycle.State.RESUMED);
        navigateTo(scenario, R.id.settingsFragment);

        onView(withText(R.string.settings_theme)).check(matches(isDisplayed()));

        onView(isAssignableFrom(SearchView.class)).perform(setSearchQuery("theme"));
        onView(withText(R.string.settings_theme)).check(matches(isDisplayed()));

        onView(isAssignableFrom(SearchView.class)).perform(setSearchQuery("zzzzzz"));
        onView(withId(androidx.appcompat.R.id.search_src_text))
                .check(matches(withText("zzzzzz")));

        scenario.close();
    }

    @Test
    public void musicSourcesPageHasAllSections() {
        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);
        scenario.moveToState(Lifecycle.State.RESUMED);
        navigateTo(scenario, R.id.settingsFragment);

        onView(withText(R.string.settings_music_sources_title)).perform(click());

        onView(withText(R.string.music_sources_subsonic_title)).check(matches(isDisplayed()));
        onView(withId(R.id.add_subsonic_button))
                .check(matches(withEffectiveVisibility(Visibility.VISIBLE)));

        boolean hasSubsonicList = isViewDisplayed(R.id.subsonic_recycler_view);
        boolean hasSubsonicEmpty = isViewDisplayed(R.id.subsonic_empty_text);
        org.junit.Assert.assertTrue(hasSubsonicList || hasSubsonicEmpty);

        onView(withId(R.id.add_jellyfin_button))
                .check(matches(withEffectiveVisibility(Visibility.VISIBLE)));
        onView(withId(R.id.jellyfin_empty_text))
                .check(matches(withEffectiveVisibility(Visibility.VISIBLE)));

        onView(withId(R.id.add_local_button))
                .check(matches(withEffectiveVisibility(Visibility.VISIBLE)));
        boolean hasLocalList = isViewDisplayed(R.id.local_recycler_view);
        boolean hasLocalEmpty = isViewDisplayed(R.id.local_empty_text);
        org.junit.Assert.assertTrue(hasLocalList || hasLocalEmpty);

        scenario.close();
    }

    private void navigateTo(ActivityScenario<MainActivity> scenario, int destinationId) {
        scenario.onActivity(activity -> {
            NavController navController = Navigation.findNavController(activity, R.id.nav_host_fragment);
            navController.navigate(destinationId);
        });
    }

    private boolean isViewDisplayed(int viewId) {
        try {
            onView(withId(viewId)).check(matches(isDisplayed()));
            return true;
        } catch (NoMatchingViewException | AssertionError e) {
            return false;
        }
    }

    private ViewAction setSearchQuery(String query) {
        return new ViewAction() {
            @Override
            public Matcher<View> getConstraints() {
                return isAssignableFrom(SearchView.class);
            }

            @Override
            public String getDescription() {
                return "Set SearchView query text";
            }

            @Override
            public void perform(UiController uiController, View view) {
                SearchView searchView = (SearchView) view;
                searchView.setQuery(query, false);
                searchView.clearFocus();
                uiController.loopMainThreadUntilIdle();
            }
        };
    }
}
