package one.chandan.rubato.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.media3.common.util.UnstableApi;

import android.content.SharedPreferences;

import one.chandan.rubato.R;
import one.chandan.rubato.App;
import one.chandan.rubato.databinding.FragmentHomeBinding;
import one.chandan.rubato.ui.activity.MainActivity;
import one.chandan.rubato.ui.fragment.pager.HomePager;
import one.chandan.rubato.util.Preferences;
import one.chandan.rubato.util.TelemetryLogger;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.Objects;

@UnstableApi
public class HomeFragment extends Fragment {
    private static final String TAG = "HomeFragment";

    private FragmentHomeBinding bind;
    private MainActivity activity;

    private MaterialToolbar materialToolbar;
    private AppBarLayout appBarLayout;
    private TabLayout tabLayout;
    private TabLayoutMediator tabLayoutMediator;
    private HomePager homePager;

    private final SharedPreferences.OnSharedPreferenceChangeListener sectionVisibilityListener =
            (prefs, key) -> {
                if (key == null) return;
                if ("podcast_section_visibility".equals(key) || "radio_section_visibility".equals(key)) {
                    rebuildHomePager();
                }
            };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();
        bind = FragmentHomeBinding.inflate(inflater, container, false);
        return bind.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initAppBar();
        initHomePager();
    }

    @Override
    public void onStart() {
        super.onStart();

        activity.setBottomNavigationBarVisibility(true);
        activity.setBottomSheetVisibility(true);
        App.getInstance().getPreferences().registerOnSharedPreferenceChangeListener(sectionVisibilityListener);
        if (needsPagerRefresh()) {
            rebuildHomePager();
        }
    }

    @Override
    public void onStop() {
        App.getInstance().getPreferences().unregisterOnSharedPreferenceChangeListener(sectionVisibilityListener);
        super.onStop();
    }

    @Override
    public void onResume() {
        super.onResume();
        TelemetryLogger.logEvent("Home", "screen_view", null, 0);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (tabLayoutMediator != null) {
            tabLayoutMediator.detach();
            tabLayoutMediator = null;
        }
        bind = null;
    }

    private void initAppBar() {
        appBarLayout = bind.getRoot().findViewById(R.id.toolbar_fragment);
        materialToolbar = bind.getRoot().findViewById(R.id.toolbar);

        activity.setSupportActionBar(materialToolbar);
        Objects.requireNonNull(materialToolbar.getOverflowIcon()).setTint(requireContext().getResources().getColor(R.color.titleTextColor, null));

        tabLayout = new TabLayout(requireContext());
        tabLayout.setTabGravity(TabLayout.GRAVITY_FILL);
        tabLayout.setTabMode(TabLayout.MODE_FIXED);

        appBarLayout.addView(tabLayout);
    }

    private void initHomePager() {
        homePager = new HomePager(this);
        buildPagerSections(homePager);

        bind.homeViewPager.setAdapter(homePager);
        bind.homeViewPager.setOffscreenPageLimit(3);
        bind.homeViewPager.setUserInputEnabled(false);

        attachTabs(homePager);
    }

    private boolean needsPagerRefresh() {
        if (homePager == null) {
            return true;
        }
        boolean wantsPodcast = Preferences.isPodcastSectionVisible();
        boolean wantsRadio = Preferences.isRadioSectionVisible();
        int expectedCount = 1 + (wantsPodcast ? 1 : 0) + (wantsRadio ? 1 : 0);
        if (homePager.getItemCount() != expectedCount) {
            return true;
        }
        boolean hasPodcast = hasTabTitle("Podcast");
        boolean hasRadio = hasTabTitle("Radio");
        return hasPodcast != wantsPodcast || hasRadio != wantsRadio;
    }

    private boolean hasTabTitle(String title) {
        if (homePager == null || title == null) {
            return false;
        }
        for (int i = 0; i < homePager.getItemCount(); i++) {
            if (title.equals(homePager.getPageTitle(i))) {
                return true;
            }
        }
        return false;
    }

    private void rebuildHomePager() {
        if (bind == null || tabLayout == null) return;

        String selectedTitle = null;
        int selectedPosition = tabLayout.getSelectedTabPosition();
        if (selectedPosition >= 0 && selectedPosition < tabLayout.getTabCount()) {
            TabLayout.Tab tab = tabLayout.getTabAt(selectedPosition);
            if (tab != null && tab.getText() != null) {
                selectedTitle = tab.getText().toString();
            }
        }

        if (tabLayoutMediator != null) {
            tabLayoutMediator.detach();
            tabLayoutMediator = null;
        }

        homePager = new HomePager(this);
        buildPagerSections(homePager);
        bind.homeViewPager.setAdapter(homePager);
        attachTabs(homePager);

        if (selectedTitle != null) {
            int index = findTabIndex(homePager, selectedTitle);
            if (index >= 0) {
                bind.homeViewPager.setCurrentItem(index, false);
            }
        }
    }

    private void buildPagerSections(HomePager pager) {
        pager.addFragment(new HomeTabMusicFragment(), "Music", R.drawable.ic_home);

        if (Preferences.isPodcastSectionVisible()) {
            pager.addFragment(new HomeTabPodcastFragment(), "Podcast", R.drawable.ic_graphic_eq);
        }

        if (Preferences.isRadioSectionVisible()) {
            pager.addFragment(new HomeTabRadioFragment(), "Radio", R.drawable.ic_play_for_work);
        }
    }

    private void attachTabs(HomePager pager) {
        tabLayoutMediator = new TabLayoutMediator(tabLayout, bind.homeViewPager,
                (tab, position) -> tab.setText(pager.getPageTitle(position))
        );
        tabLayoutMediator.attach();

        tabLayout.setVisibility(Preferences.isPodcastSectionVisible() || Preferences.isRadioSectionVisible() ? View.VISIBLE : View.GONE);
    }

    private int findTabIndex(HomePager pager, String title) {
        for (int i = 0; i < pager.getItemCount(); i++) {
            if (title.equals(pager.getPageTitle(i))) {
                return i;
            }
        }
        return -1;
    }
}
