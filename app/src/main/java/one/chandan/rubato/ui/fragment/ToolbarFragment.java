package one.chandan.rubato.ui.fragment;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.media3.common.util.UnstableApi;

import one.chandan.rubato.R;
import one.chandan.rubato.App;
import one.chandan.rubato.databinding.FragmentToolbarBinding;
import one.chandan.rubato.repository.CacheRepository;
import one.chandan.rubato.subsonic.models.AlbumID3;
import one.chandan.rubato.subsonic.models.ArtistID3;
import one.chandan.rubato.subsonic.models.Child;
import one.chandan.rubato.ui.activity.MainActivity;
import one.chandan.rubato.ui.dialog.MetadataSyncStatusDialog;
import one.chandan.rubato.util.NetworkUtil;
import one.chandan.rubato.util.Preferences;
import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.material.color.MaterialColors;
import com.google.gson.reflect.TypeToken;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@UnstableApi
public class ToolbarFragment extends Fragment {
    private static final String TAG = "ToolbarFragment";

    private FragmentToolbarBinding bind;
    private MainActivity activity;
    private MenuItem metadataStatusItem;
    private final SharedPreferences.OnSharedPreferenceChangeListener metadataSyncListener =
            (prefs, key) -> {
                if (key == null) return;
                if (Preferences.METADATA_SYNC_ACTIVE.equals(key)
                        || Preferences.METADATA_SYNC_STAGE.equals(key)
                        || Preferences.METADATA_SYNC_PROGRESS_CURRENT.equals(key)
                        || Preferences.METADATA_SYNC_PROGRESS_TOTAL.equals(key)) {
                    updateMetadataStatusIcon();
                }
            };

    private enum MetadataStatus {
        NONE,
        PARTIAL,
        FULL
    }

    public ToolbarFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.main_page_menu, menu);
        CastButtonFactory.setUpMediaRouteButton(requireContext(), menu, R.id.media_route_menu_item);
        metadataStatusItem = menu.findItem(R.id.action_metadata_status);
        updateMetadataStatusIcon();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();

        bind = FragmentToolbarBinding.inflate(inflater, container, false);
        View view = bind.getRoot();

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        App.getInstance().getPreferences().registerOnSharedPreferenceChangeListener(metadataSyncListener);
        updateMetadataStatusIcon();
    }

    @Override
    public void onStop() {
        App.getInstance().getPreferences().unregisterOnSharedPreferenceChangeListener(metadataSyncListener);
        super.onStop();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_metadata_status) {
            MetadataSyncStatusDialog dialog = new MetadataSyncStatusDialog();
            dialog.show(getParentFragmentManager(), "MetadataSyncStatusDialog");
            return true;
        } else if (item.getItemId() == R.id.action_settings) {
            activity.navController.navigate(R.id.settingsFragment);
            return true;
        }

        return false;
    }

    private void updateMetadataStatusIcon() {
        if (!isAdded() || metadataStatusItem == null) return;

        CacheRepository cacheRepository = new CacheRepository();
        AtomicInteger pending = new AtomicInteger(3);
        AtomicBoolean hasAny = new AtomicBoolean(false);
        AtomicBoolean hasAll = new AtomicBoolean(true);

        cacheRepository.loadOrNull("albums_all", new TypeToken<List<AlbumID3>>() {
        }.getType(), new CacheRepository.CacheResult<List<AlbumID3>>() {
            @Override
            public void onLoaded(List<AlbumID3> albums) {
                boolean present = albums != null && !albums.isEmpty();
                if (present) {
                    hasAny.set(true);
                } else {
                    hasAll.set(false);
                }
                if (pending.decrementAndGet() == 0) {
                    applyMetadataStatus(hasAny.get(), hasAll.get());
                }
            }
        });

        cacheRepository.loadOrNull("artists_all", new TypeToken<List<ArtistID3>>() {
        }.getType(), new CacheRepository.CacheResult<List<ArtistID3>>() {
            @Override
            public void onLoaded(List<ArtistID3> artists) {
                boolean present = artists != null && !artists.isEmpty();
                if (present) {
                    hasAny.set(true);
                } else {
                    hasAll.set(false);
                }
                if (pending.decrementAndGet() == 0) {
                    applyMetadataStatus(hasAny.get(), hasAll.get());
                }
            }
        });

        cacheRepository.loadOrNull("songs_all", new TypeToken<List<Child>>() {
        }.getType(), new CacheRepository.CacheResult<List<Child>>() {
            @Override
            public void onLoaded(List<Child> songs) {
                boolean present = songs != null && !songs.isEmpty();
                if (present) {
                    hasAny.set(true);
                } else {
                    hasAll.set(false);
                }
                if (pending.decrementAndGet() == 0) {
                    applyMetadataStatus(hasAny.get(), hasAll.get());
                }
            }
        });
    }

    private void applyMetadataStatus(boolean hasAny, boolean hasAll) {
        if (!isAdded()) return;
        Runnable updater = () -> {
            if (!isAdded() || metadataStatusItem == null) return;

            boolean offline = NetworkUtil.isOffline();
            boolean syncing = Preferences.isMetadataSyncActive();
            MetadataStatus status;
            if (!hasAny) {
                status = MetadataStatus.NONE;
            } else if (hasAll) {
                status = MetadataStatus.FULL;
            } else {
                status = MetadataStatus.PARTIAL;
            }

            int iconRes;
            int colorAttr;
            String description;

            if (offline) {
                iconRes = R.drawable.ic_cloud_off;
                colorAttr = com.google.android.material.R.attr.colorOutline;
                description = getString(R.string.metadata_sync_status_offline);
            } else if (syncing) {
                iconRes = R.drawable.ic_downloading;
                colorAttr = com.google.android.material.R.attr.colorTertiary;
                description = buildMetadataSyncDescription();
            } else if (status == MetadataStatus.FULL) {
                iconRes = R.drawable.ic_cloud_done;
                colorAttr = com.google.android.material.R.attr.colorPrimary;
                description = getString(R.string.download_status_full_metadata);
            } else if (status == MetadataStatus.PARTIAL) {
                iconRes = R.drawable.ic_cloud_download;
                colorAttr = com.google.android.material.R.attr.colorTertiary;
                description = getString(R.string.download_status_partial);
            } else {
                iconRes = R.drawable.ic_cloud_download;
                colorAttr = com.google.android.material.R.attr.colorOutline;
                description = getString(R.string.download_status_none);
            }

            metadataStatusItem.setIcon(iconRes);
            if (metadataStatusItem.getIcon() != null) {
                int tint = MaterialColors.getColor(requireContext(), colorAttr, 0xFF757575);
                metadataStatusItem.getIcon().setTint(tint);
            }
            metadataStatusItem.setTitle(description);
            metadataStatusItem.setContentDescription(description);
            metadataStatusItem.setTooltipText(description);
        };

        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            updater.run();
        } else {
            requireActivity().runOnUiThread(updater);
        }
    }

    private String buildMetadataSyncDescription() {
        String stageLabel = resolveMetadataStageLabel(Preferences.getMetadataSyncStage());
        int current = Preferences.getMetadataSyncProgressCurrent();
        int total = Preferences.getMetadataSyncProgressTotal();

        if (total > 0) {
            return getString(R.string.metadata_sync_status_with_total, stageLabel, current, total);
        }
        if (current > 0) {
            return getString(R.string.metadata_sync_status_with_count, stageLabel, current);
        }
        return getString(R.string.metadata_sync_status_format, stageLabel);
    }

    private String resolveMetadataStageLabel(@Nullable String stage) {
        if (stage == null) return getString(R.string.metadata_sync_stage_preparing);
        switch (stage) {
            case one.chandan.rubato.util.MetadataSyncManager.STAGE_PLAYLISTS:
                return getString(R.string.metadata_sync_stage_playlists);
            case one.chandan.rubato.util.MetadataSyncManager.STAGE_ARTISTS:
                return getString(R.string.metadata_sync_stage_artists);
            case one.chandan.rubato.util.MetadataSyncManager.STAGE_ARTIST_DETAILS:
                return getString(R.string.metadata_sync_stage_artist_details);
            case one.chandan.rubato.util.MetadataSyncManager.STAGE_GENRES:
                return getString(R.string.metadata_sync_stage_genres);
            case one.chandan.rubato.util.MetadataSyncManager.STAGE_ALBUMS:
                return getString(R.string.metadata_sync_stage_albums);
            case one.chandan.rubato.util.MetadataSyncManager.STAGE_ALBUM_DETAILS:
                return getString(R.string.metadata_sync_stage_album_details);
            case one.chandan.rubato.util.MetadataSyncManager.STAGE_SONGS:
                return getString(R.string.metadata_sync_stage_songs);
            case one.chandan.rubato.util.MetadataSyncManager.STAGE_COVER_ART:
                return getString(R.string.metadata_sync_stage_cover_art);
            case one.chandan.rubato.util.MetadataSyncManager.STAGE_LYRICS:
                return getString(R.string.metadata_sync_stage_lyrics);
            case one.chandan.rubato.util.MetadataSyncManager.STAGE_PREPARING:
            default:
                return getString(R.string.metadata_sync_stage_preparing);
        }
    }
}
