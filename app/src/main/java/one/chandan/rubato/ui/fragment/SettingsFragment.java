package one.chandan.rubato.ui.fragment;

import android.content.Intent;
import android.media.audiofx.AudioEffect;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.navigation.Navigation;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import one.chandan.rubato.BuildConfig;
import one.chandan.rubato.R;
import one.chandan.rubato.helper.ThemeHelper;
import one.chandan.rubato.interfaces.DialogClickCallback;
import one.chandan.rubato.interfaces.ScanCallback;
import one.chandan.rubato.ui.activity.MainActivity;
import one.chandan.rubato.ui.dialog.DeleteDownloadStorageDialog;
import one.chandan.rubato.ui.dialog.DownloadStorageDialog;
import one.chandan.rubato.ui.dialog.StarredSyncDialog;
import one.chandan.rubato.ui.dialog.StreamingCacheStorageDialog;
import one.chandan.rubato.ui.dialog.ThemeSelectorDialog;
import one.chandan.rubato.ui.dialog.VisualizerSettingsDialog;
import one.chandan.rubato.util.DownloadUtil;
import one.chandan.rubato.util.LocalMusicPermissions;
import one.chandan.rubato.util.NetworkUtil;
import one.chandan.rubato.util.Preferences;
import one.chandan.rubato.util.UIUtil;
import one.chandan.rubato.repository.LocalMusicRepository;
import one.chandan.rubato.viewmodel.SettingViewModel;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.color.MaterialColors;

import java.util.Locale;
import java.util.IdentityHashMap;
import java.util.Map;

@OptIn(markerClass = UnstableApi.class)
public class SettingsFragment extends PreferenceFragmentCompat {
    private static final String TAG = "SettingsFragment";

    private MainActivity activity;
    private SettingViewModel settingViewModel;
    private SearchView searchView;
    private String lastQuery = "";
    private final Map<Preference, Boolean> baseVisibility = new IdentityHashMap<>();

    private ActivityResultLauncher<Intent> someActivityResultLauncher;
    private ActivityResultLauncher<String[]> audioPermissionLauncher;
    private boolean pendingLocalMusicEnable = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        someActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                });

        audioPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean granted = false;
                    for (Boolean value : result.values()) {
                        if (Boolean.TRUE.equals(value)) {
                            granted = true;
                            break;
                        }
                    }

                    if (pendingLocalMusicEnable) {
                        pendingLocalMusicEnable = false;
                        if (granted) {
                            SwitchPreference localMusicPref = findPreference("local_music_enabled");
                            if (localMusicPref != null) {
                                localMusicPref.setChecked(true);
                            }
                            LocalMusicRepository.invalidateCache();
                        }
                    }
                });
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();

        View view = super.onCreateView(inflater, container, savedInstanceState);
        settingViewModel = new ViewModelProvider(requireActivity()).get(SettingViewModel.class);

        if (view == null) return null;

        if (view instanceof ViewGroup) {
            ViewGroup root = (ViewGroup) view;

            MaterialToolbar toolbar = new MaterialToolbar(requireContext());
            toolbar.setTitle(R.string.menu_settings_button);
            toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
            toolbar.setNavigationOnClickListener(v -> activity.onBackPressed());
            toolbar.setBackgroundColor(MaterialColors.getColor(toolbar, R.attr.colorSurface));

            int toolbarHeight = ViewGroup.LayoutParams.WRAP_CONTENT;
            android.util.TypedValue typedValue = new android.util.TypedValue();
            if (requireContext().getTheme().resolveAttribute(android.R.attr.actionBarSize, typedValue, true)) {
                toolbarHeight = android.util.TypedValue.complexToDimensionPixelSize(
                        typedValue.data,
                        getResources().getDisplayMetrics()
                );
            }

            searchView = new SearchView(requireContext());
            searchView.setIconifiedByDefault(false);
            searchView.setQueryHint(getString(R.string.settings_search_hint));
            searchView.setSubmitButtonEnabled(false);
            searchView.clearFocus();
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                lastQuery = newText == null ? "" : newText;
                applyPreferenceFilter(lastQuery);
                return true;
            }
            });

            int horizontalPadding = (int) (getResources().getDisplayMetrics().density * 16);
            int verticalPadding = (int) (getResources().getDisplayMetrics().density * 8);
            searchView.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);

            if (root instanceof LinearLayout) {
                LinearLayout.LayoutParams toolbarParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        toolbarHeight
                );
                toolbar.setLayoutParams(toolbarParams);

                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                searchView.setLayoutParams(params);
            } else {
                toolbar.setLayoutParams(new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        toolbarHeight
                ));
                searchView.setLayoutParams(new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                ));
            }

            root.addView(toolbar, 0);
            root.addView(searchView, 1);
        }

        getListView().setPadding(0, 0, 0, (int) getResources().getDimension(R.dimen.global_padding_bottom));
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        activity.setBottomNavigationBarVisibility(false);
        activity.setBottomSheetVisibility(false);
    }

    @Override
    public void onResume() {
        super.onResume();

        checkEqualizer();
        checkCacheStorage();
        checkStorage();

        setStreamingCacheSize();
        setAppLanguage();
        setVersion();

        actionMusicSources();
        actionVisualizerSettings();
        actionScan();
        actionSyncStarredTracks();
        actionChangeStreamingCacheStorage();
        actionChangeDownloadStorage();
        actionDeleteDownloadStorage();
        actionKeepScreenOn();
        setupLocalMusicPreference();
        snapshotPreferenceVisibility();
        applyPreferenceFilter(lastQuery);
    }

    @Override
    public void onStop() {
        super.onStop();
        activity.setBottomSheetVisibility(true);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.global_preferences, rootKey);
        Preference themePreference = findPreference(Preferences.THEME);
        if (themePreference != null) {
            getParentFragmentManager().setFragmentResultListener(
                    ThemeSelectorDialog.RESULT_KEY,
                    this,
                    (requestKey, result) -> {
                        String themeOption = result.getString(ThemeSelectorDialog.RESULT_THEME, ThemeHelper.DEFAULT_MODE);
                        Preferences.setTheme(themeOption);
                        themePreference.setSummary(resolveThemeLabel(themeOption));
                        ThemeHelper.applyTheme(themeOption);
                        if (getActivity() != null) {
                            getActivity().recreate();
                        }
                    }
            );
            themePreference.setSummary(resolveThemeLabel(Preferences.getTheme()));
            themePreference.setOnPreferenceClickListener(preference -> {
                ThemeSelectorDialog dialog = ThemeSelectorDialog.newInstance(Preferences.getTheme());
                dialog.show(getParentFragmentManager(), "theme_selector");
                return true;
            });
        }
    }

    private void checkEqualizer() {
        Preference equalizer = findPreference("equalizer");

        if (equalizer == null) return;

        Intent intent = new Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL);

        if ((intent.resolveActivity(requireActivity().getPackageManager()) != null)) {
            equalizer.setOnPreferenceClickListener(preference -> {
                someActivityResultLauncher.launch(intent);
                return true;
            });
        } else {
            equalizer.setVisible(false);
        }
    }

    private void checkCacheStorage() {
        Preference storage = findPreference("streaming_cache_storage");

        if (storage == null) return;

        try {
            if (requireContext().getExternalFilesDirs(null)[1] == null) {
                storage.setVisible(false);
            } else {
                storage.setSummary(Preferences.getDownloadStoragePreference() == 0 ? R.string.download_storage_internal_dialog_negative_button : R.string.download_storage_external_dialog_positive_button);
            }
        } catch (Exception exception) {
            storage.setVisible(false);
        }
    }

    private void checkStorage() {
        Preference storage = findPreference("download_storage");

        if (storage == null) return;

        try {
            if (requireContext().getExternalFilesDirs(null)[1] == null) {
                storage.setVisible(false);
            } else {
                storage.setSummary(Preferences.getDownloadStoragePreference() == 0 ? R.string.download_storage_internal_dialog_negative_button : R.string.download_storage_external_dialog_positive_button);
            }
        } catch (Exception exception) {
            storage.setVisible(false);
        }
    }

    private void setStreamingCacheSize() {
        ListPreference streamingCachePreference = findPreference("streaming_cache_size");

        if (streamingCachePreference != null) {
            streamingCachePreference.setSummaryProvider(new Preference.SummaryProvider<ListPreference>() {
                @Nullable
                @Override
                public CharSequence provideSummary(@NonNull ListPreference preference) {
                    CharSequence entry = preference.getEntry();

                    if (entry == null) return null;

                    long currentSizeMb = DownloadUtil.getStreamingCacheSize(requireActivity()) / (1024 * 1024);

                    return getString(R.string.settings_summary_streaming_cache_size, entry, String.valueOf(currentSizeMb));
                }
            });
        }
    }

    private void setAppLanguage() {
        ListPreference localePref = (ListPreference) findPreference("language");

        Map<String, String> locales = UIUtil.getLangPreferenceDropdownEntries(requireContext());

        CharSequence[] entries = locales.keySet().toArray(new CharSequence[locales.size()]);
        CharSequence[] entryValues = locales.values().toArray(new CharSequence[locales.size()]);

        localePref.setEntries(entries);
        localePref.setEntryValues(entryValues);

        localePref.setDefaultValue(entryValues[0]);
        localePref.setSummary(Locale.forLanguageTag(localePref.getValue()).getDisplayLanguage());

        localePref.setOnPreferenceChangeListener((preference, newValue) -> {
            LocaleListCompat appLocale = LocaleListCompat.forLanguageTags((String) newValue);
            AppCompatDelegate.setApplicationLocales(appLocale);
            return true;
        });
    }

    private void setVersion() {
        findPreference("version").setSummary(BuildConfig.VERSION_NAME);
    }

    private void applyPreferenceFilter(String query) {
        PreferenceScreen screen = getPreferenceScreen();
        if (screen == null) return;

        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.getDefault());
        if (normalized.isEmpty()) {
            restorePreferenceVisibility(screen);
            return;
        }

        filterPreference(screen, normalized);
    }

    private void snapshotPreferenceVisibility() {
        PreferenceScreen screen = getPreferenceScreen();
        if (screen == null) return;
        baseVisibility.clear();
        captureVisibility(screen);
    }

    private void captureVisibility(Preference preference) {
        baseVisibility.put(preference, preference.isVisible());
        if (preference instanceof PreferenceGroup) {
            PreferenceGroup group = (PreferenceGroup) preference;
            for (int i = 0; i < group.getPreferenceCount(); i++) {
                captureVisibility(group.getPreference(i));
            }
        }
    }

    private void restorePreferenceVisibility(Preference preference) {
        if (preference instanceof PreferenceGroup) {
            PreferenceGroup group = (PreferenceGroup) preference;
            for (int i = 0; i < group.getPreferenceCount(); i++) {
                restorePreferenceVisibility(group.getPreference(i));
            }
        }

        Boolean visible = baseVisibility.get(preference);
        if (visible != null) {
            preference.setVisible(visible);
        }
    }

    private boolean filterPreference(Preference preference, String query) {
        if (!isBaseVisible(preference)) {
            preference.setVisible(false);
            return false;
        }

        if (preference instanceof PreferenceGroup) {
            PreferenceGroup group = (PreferenceGroup) preference;
            boolean hasVisibleChild = false;
            for (int i = 0; i < group.getPreferenceCount(); i++) {
                if (filterPreference(group.getPreference(i), query)) {
                    hasVisibleChild = true;
                }
            }
            if (preference instanceof PreferenceCategory) {
                preference.setVisible(hasVisibleChild);
            }
            return hasVisibleChild;
        }

        boolean matches = matchesPreference(preference, query);
        preference.setVisible(matches);
        return matches;
    }

    private boolean isBaseVisible(Preference preference) {
        Boolean visible = baseVisibility.get(preference);
        return visible == null || visible;
    }

    private boolean matchesPreference(Preference preference, String query) {
        CharSequence title = preference.getTitle();
        CharSequence summary = preference.getSummary();

        if (title != null && title.toString().toLowerCase(Locale.getDefault()).contains(query)) {
            return true;
        }

        return summary != null && summary.toString().toLowerCase(Locale.getDefault()).contains(query);
    }

    private void actionMusicSources() {
        Preference sources = findPreference("music_sources");
        if (sources == null) return;
        sources.setOnPreferenceClickListener(preference -> {
            Navigation.findNavController(requireView()).navigate(R.id.action_settingsFragment_to_musicSourcesFragment);
            return true;
        });
    }

    private void actionVisualizerSettings() {
        Preference visualizer = findPreference("visualizer_settings");
        if (visualizer == null) return;
        visualizer.setOnPreferenceClickListener(preference -> {
            VisualizerSettingsDialog dialog = new VisualizerSettingsDialog();
            dialog.show(getParentFragmentManager(), "VisualizerSettingsDialog");
            return true;
        });
    }

    private void actionScan() {
        findPreference("scan_library").setOnPreferenceClickListener(preference -> {
            if (NetworkUtil.isOffline()) {
                Toast.makeText(requireContext(), R.string.queue_add_next_unavailable_offline, Toast.LENGTH_SHORT).show();
                return true;
            }
            settingViewModel.launchScan(new ScanCallback() {
                @Override
                public void onError(Exception exception) {
                    findPreference("scan_library").setSummary(exception.getMessage());
                }

                @Override
                public void onSuccess(boolean isScanning, long count) {
                    findPreference("scan_library").setSummary("Scanning: counting " + count + " tracks");
                    if (isScanning) getScanStatus();
                }
            });

            return true;
        });
    }

    private void actionSyncStarredTracks() {
        findPreference("sync_starred_tracks_for_offline_use").setOnPreferenceChangeListener((preference, newValue) -> {
            if (newValue instanceof Boolean) {
                if ((Boolean) newValue) {
                    StarredSyncDialog dialog = new StarredSyncDialog();
                    dialog.show(activity.getSupportFragmentManager(), null);
                }
            }
            return true;
        });
    }

    private void actionChangeStreamingCacheStorage() {
        findPreference("streaming_cache_storage").setOnPreferenceClickListener(preference -> {
            StreamingCacheStorageDialog dialog = new StreamingCacheStorageDialog(new DialogClickCallback() {
                @Override
                public void onPositiveClick() {
                    findPreference("streaming_cache_storage").setSummary(R.string.streaming_cache_storage_external_dialog_positive_button);
                }

                @Override
                public void onNegativeClick() {
                    findPreference("streaming_cache_storage").setSummary(R.string.streaming_cache_storage_internal_dialog_negative_button);
                }
            });
            dialog.show(activity.getSupportFragmentManager(), null);
            return true;
        });
    }

    private void actionChangeDownloadStorage() {
        findPreference("download_storage").setOnPreferenceClickListener(preference -> {
            DownloadStorageDialog dialog = new DownloadStorageDialog(new DialogClickCallback() {
                @Override
                public void onPositiveClick() {
                    findPreference("download_storage").setSummary(R.string.download_storage_external_dialog_positive_button);
                }

                @Override
                public void onNegativeClick() {
                    findPreference("download_storage").setSummary(R.string.download_storage_internal_dialog_negative_button);
                }
            });
            dialog.show(activity.getSupportFragmentManager(), null);
            return true;
        });
    }

    private void actionDeleteDownloadStorage() {
        findPreference("delete_download_storage").setOnPreferenceClickListener(preference -> {
            DeleteDownloadStorageDialog dialog = new DeleteDownloadStorageDialog();
            dialog.show(activity.getSupportFragmentManager(), null);
            return true;
        });
    }

    private void getScanStatus() {
        settingViewModel.getScanStatus(new ScanCallback() {
            @Override
            public void onError(Exception exception) {
                findPreference("scan_library").setSummary(exception.getMessage());
            }

            @Override
            public void onSuccess(boolean isScanning, long count) {
                findPreference("scan_library").setSummary("Scanning: counting " + count + " tracks");
                if (isScanning) getScanStatus();
            }
        });
    }

    private void actionKeepScreenOn() {
        findPreference("always_on_display").setOnPreferenceChangeListener((preference, newValue) -> {
            if (newValue instanceof Boolean) {
                if ((Boolean) newValue) {
                    activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                } else {
                    activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
            }
            return true;
        });
    }

    private String resolveThemeLabel(String themeId) {
        String[] values = getResources().getStringArray(R.array.theme_list_values);
        String[] titles = getResources().getStringArray(R.array.theme_list_titles);
        if (themeId != null) {
            for (int i = 0; i < values.length && i < titles.length; i++) {
                if (themeId.equals(values[i])) {
                    return titles[i];
                }
            }
        }
        return getString(R.string.theme_option_system);
    }

    private void setupLocalMusicPreference() {
        SwitchPreference localMusicPreference = findPreference("local_music_enabled");
        if (localMusicPreference == null) return;

        localMusicPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            if (!(newValue instanceof Boolean)) return true;

            boolean enable = (Boolean) newValue;
            if (enable && !LocalMusicPermissions.hasReadPermission(requireContext())) {
                pendingLocalMusicEnable = true;
                audioPermissionLauncher.launch(LocalMusicPermissions.getReadPermissions());
                return false;
            }

            LocalMusicRepository.invalidateCache();
            return true;
        });
    }
}
