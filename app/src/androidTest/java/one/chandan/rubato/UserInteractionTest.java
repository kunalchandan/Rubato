package one.chandan.rubato;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.lifecycle.Lifecycle;

import one.chandan.rubato.ui.activity.MainActivity;
import one.chandan.rubato.ui.fragment.DownloadFragment;
import one.chandan.rubato.ui.fragment.MusicSourcesFragment;
import one.chandan.rubato.ui.fragment.SearchFragment;
import one.chandan.rubato.ui.fragment.SettingsFragment;
import one.chandan.rubato.util.Preferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import android.view.View;
import android.view.ViewGroup;
import androidx.preference.Preference;
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
        launchFragment(scenario, new SearchFragment());
        scenario.onActivity(activity -> {
            SearchFragment fragment = (SearchFragment) activity.getSupportFragmentManager()
                    .findFragmentById(R.id.nav_host_fragment);
            org.junit.Assert.assertNotNull(fragment);
            View root = fragment.getView();
            org.junit.Assert.assertNotNull(root);
            org.junit.Assert.assertNotNull(root.findViewById(R.id.search_bar));
            org.junit.Assert.assertNotNull(root.findViewById(R.id.search_view));
        });

        scenario.close();
    }

    @Test
    public void downloadedScreenShowsCoreControlsOrEmptyState() {
        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);
        scenario.moveToState(Lifecycle.State.RESUMED);
        launchFragment(scenario, new DownloadFragment());
        scenario.onActivity(activity -> {
            DownloadFragment fragment = (DownloadFragment) activity.getSupportFragmentManager()
                    .findFragmentById(R.id.nav_host_fragment);
            org.junit.Assert.assertNotNull(fragment);
            View root = fragment.getView();
            org.junit.Assert.assertNotNull(root);
            View emptyLayout = root.findViewById(R.id.empty_download_layout);
            View contentLayout = root.findViewById(R.id.download_downloaded_sector);
            org.junit.Assert.assertNotNull(emptyLayout);
            org.junit.Assert.assertNotNull(contentLayout);
            boolean hasEmpty = emptyLayout.getVisibility() == View.VISIBLE;
            boolean hasContent = contentLayout.getVisibility() == View.VISIBLE;
            org.junit.Assert.assertTrue(hasEmpty || hasContent);
            if (hasContent) {
                org.junit.Assert.assertNotNull(root.findViewById(R.id.shuffle_downloaded_text_view_clickable));
                org.junit.Assert.assertNotNull(root.findViewById(R.id.downloaded_group_by_image_view));
                org.junit.Assert.assertNotNull(root.findViewById(R.id.metadata_sync_status_text_view));
                org.junit.Assert.assertNotNull(root.findViewById(R.id.metadata_sync_progress_bar));
            }
        });

        scenario.close();
    }

    @Test
    public void settingsSearchFiltersPreferences() {
        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);
        scenario.moveToState(Lifecycle.State.RESUMED);
        launchFragment(scenario, new SettingsFragment());
        scenario.onActivity(activity -> {
            SettingsFragment fragment = (SettingsFragment) activity.getSupportFragmentManager()
                    .findFragmentById(R.id.nav_host_fragment);
            org.junit.Assert.assertNotNull(fragment);
            Preference themePreference = fragment.findPreference(Preferences.THEME);
            org.junit.Assert.assertNotNull(themePreference);
            View root = fragment.getView();
            org.junit.Assert.assertNotNull(root);
            SearchView searchView = findSearchView(root);
            org.junit.Assert.assertNotNull(searchView);
            searchView.setQuery("theme", false);
            org.junit.Assert.assertTrue(themePreference.isVisible());
            searchView.setQuery("zzzzzz", false);
            org.junit.Assert.assertFalse(themePreference.isVisible());
        });

        scenario.close();
    }

    @Test
    public void musicSourcesPageHasAllSections() {
        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);
        scenario.moveToState(Lifecycle.State.RESUMED);
        launchFragment(scenario, new MusicSourcesFragment());
        scenario.onActivity(activity -> {
            MusicSourcesFragment fragment = (MusicSourcesFragment) activity.getSupportFragmentManager()
                    .findFragmentById(R.id.nav_host_fragment);
            org.junit.Assert.assertNotNull(fragment);
            View root = fragment.getView();
            org.junit.Assert.assertNotNull(root);
            View addSubsonic = root.findViewById(R.id.add_subsonic_button);
            org.junit.Assert.assertNotNull(addSubsonic);
            View subsonicList = root.findViewById(R.id.subsonic_recycler_view);
            View subsonicEmpty = root.findViewById(R.id.subsonic_empty_text);
            org.junit.Assert.assertNotNull(subsonicList);
            org.junit.Assert.assertNotNull(subsonicEmpty);
            boolean hasSubsonic = subsonicList.getVisibility() == View.VISIBLE
                    || subsonicEmpty.getVisibility() == View.VISIBLE;
            org.junit.Assert.assertTrue(hasSubsonic);

            View addJellyfin = root.findViewById(R.id.add_jellyfin_button);
            View jellyfinEmpty = root.findViewById(R.id.jellyfin_empty_text);
            org.junit.Assert.assertNotNull(addJellyfin);
            org.junit.Assert.assertNotNull(jellyfinEmpty);

            View addLocal = root.findViewById(R.id.add_local_button);
            View localList = root.findViewById(R.id.local_recycler_view);
            View localEmpty = root.findViewById(R.id.local_empty_text);
            org.junit.Assert.assertNotNull(addLocal);
            org.junit.Assert.assertNotNull(localList);
            org.junit.Assert.assertNotNull(localEmpty);
            boolean hasLocal = localList.getVisibility() == View.VISIBLE
                    || localEmpty.getVisibility() == View.VISIBLE;
            org.junit.Assert.assertTrue(hasLocal);
        });

        scenario.close();
    }

    private void launchFragment(ActivityScenario<MainActivity> scenario, androidx.fragment.app.Fragment fragment) {
        scenario.onActivity(activity -> activity.getSupportFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.nav_host_fragment, fragment)
                .commitNowAllowingStateLoss());
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private SearchView findSearchView(View root) {
        if (root instanceof SearchView) {
            return (SearchView) root;
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                SearchView found = findSearchView(group.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
