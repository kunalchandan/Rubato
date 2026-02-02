package one.chandan.rubato.ui.dialog;

import android.app.Dialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import one.chandan.rubato.App;
import one.chandan.rubato.R;
import one.chandan.rubato.databinding.DialogMetadataSyncStatusBinding;
import one.chandan.rubato.repository.CacheRepository;
import one.chandan.rubato.ui.adapter.MetadataSyncLogAdapter;
import one.chandan.rubato.subsonic.models.AlbumID3;
import one.chandan.rubato.subsonic.models.ArtistID3;
import one.chandan.rubato.subsonic.models.Child;
import one.chandan.rubato.subsonic.models.Genre;
import one.chandan.rubato.subsonic.models.Playlist;
import one.chandan.rubato.util.MetadataSyncManager;
import one.chandan.rubato.util.NetworkUtil;
import one.chandan.rubato.util.Preferences;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.reflect.TypeToken;

import java.text.DateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class MetadataSyncStatusDialog extends DialogFragment {
    private DialogMetadataSyncStatusBinding bind;
    private MetadataSyncLogAdapter logAdapter;

    private final SharedPreferences.OnSharedPreferenceChangeListener metadataSyncListener =
            (prefs, key) -> {
                if (key == null) return;
                if (Preferences.METADATA_SYNC_ACTIVE.equals(key)
                        || Preferences.METADATA_SYNC_STAGE.equals(key)
                        || Preferences.METADATA_SYNC_PROGRESS_CURRENT.equals(key)
                        || Preferences.METADATA_SYNC_PROGRESS_TOTAL.equals(key)
                        || Preferences.METADATA_SYNC_COVER_ART_CURRENT.equals(key)
                        || Preferences.METADATA_SYNC_COVER_ART_TOTAL.equals(key)
                        || Preferences.METADATA_SYNC_LYRICS_CURRENT.equals(key)
                        || Preferences.METADATA_SYNC_LYRICS_TOTAL.equals(key)
                        || Preferences.METADATA_SYNC_STARTED.equals(key)
                        || Preferences.METADATA_SYNC_LOGS.equals(key)) {
                    renderActiveSync();
                    renderLastSynced();
                    renderSyncExtras();
                    renderMetadataStorage();
                    renderSyncLogs();
                }
            };

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        bind = DialogMetadataSyncStatusBinding.inflate(getLayoutInflater());

        return new MaterialAlertDialogBuilder(requireActivity())
                .setView(bind.getRoot())
                .setPositiveButton(android.R.string.ok, (dialog, id) -> dialog.dismiss())
                .create();
    }

    @Override
    public void onStart() {
        super.onStart();
        App.getInstance().getPreferences().registerOnSharedPreferenceChangeListener(metadataSyncListener);
        renderConnectionStatus();
        renderActiveSync();
        renderLastSynced();
        loadMetadataCounts();
        renderSyncExtras();
        renderMetadataStorage();
        setupLogList();
        renderSyncLogs();
    }

    @Override
    public void onStop() {
        App.getInstance().getPreferences().unregisterOnSharedPreferenceChangeListener(metadataSyncListener);
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        bind = null;
    }

    private void renderConnectionStatus() {
        if (bind == null) return;
        int subtitleRes = NetworkUtil.isOffline()
                ? R.string.metadata_sync_dialog_subtitle_offline
                : R.string.metadata_sync_dialog_subtitle_online;
        bind.metadataSyncDialogSubtitle.setText(subtitleRes);
    }

    private void renderActiveSync() {
        if (bind == null) return;
        boolean active = Preferences.isMetadataSyncActive();
        bind.metadataSyncActiveStatusTextView.setText(active
                ? buildMetadataSyncDescription()
                : getString(R.string.metadata_sync_status_not_active));

        if (!active) {
            bind.metadataSyncActiveProgress.setVisibility(View.GONE);
            return;
        }

        int current = Preferences.getMetadataSyncProgressCurrent();
        int total = Preferences.getMetadataSyncProgressTotal();
        bind.metadataSyncActiveProgress.setVisibility(View.VISIBLE);
        if (total > 0) {
            bind.metadataSyncActiveProgress.setIndeterminate(false);
            bind.metadataSyncActiveProgress.setMax(total);
            bind.metadataSyncActiveProgress.setProgressCompat(Math.min(current, total), false);
        } else {
            bind.metadataSyncActiveProgress.setIndeterminate(true);
        }
    }

    private void renderLastSynced() {
        if (bind == null) return;
        if (Preferences.isMetadataSyncActive()) {
            long started = Preferences.getMetadataSyncStarted();
            if (started > 0) {
                DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
                String formatted = dateFormat.format(new Date(started));
                bind.metadataSyncLastSyncedTextView.setText(getString(R.string.metadata_sync_last_synced_in_progress_since, formatted));
            } else {
                bind.metadataSyncLastSyncedTextView.setText(R.string.metadata_sync_last_synced_in_progress);
            }
            return;
        }
        long lastSync = Preferences.getMetadataSyncLast();
        if (lastSync <= 0) {
            bind.metadataSyncLastSyncedTextView.setText(R.string.metadata_sync_last_synced_placeholder);
            return;
        }
        DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
        String formatted = dateFormat.format(new Date(lastSync));
        bind.metadataSyncLastSyncedTextView.setText(getString(R.string.metadata_sync_last_synced_format, formatted));
    }

    private void loadMetadataCounts() {
        CacheRepository cacheRepository = new CacheRepository();
        loadCount(cacheRepository, "playlists", new TypeToken<List<Playlist>>() {
        }.getType(), bind.metadataSyncPlaylistsValue);
        loadCount(cacheRepository, "artists_all", new TypeToken<List<ArtistID3>>() {
        }.getType(), bind.metadataSyncArtistsValue);
        loadCount(cacheRepository, "genres_all", new TypeToken<List<Genre>>() {
        }.getType(), bind.metadataSyncGenresValue);
        loadCount(cacheRepository, "albums_all", new TypeToken<List<AlbumID3>>() {
        }.getType(), bind.metadataSyncAlbumsValue);
        loadCount(cacheRepository, "songs_all", new TypeToken<List<Child>>() {
        }.getType(), bind.metadataSyncSongsValue);
    }

    private void renderSyncExtras() {
        if (bind == null) return;
        bind.metadataSyncCoverArtValue.setText(formatCount(
                Preferences.getMetadataSyncCoverArtCurrent(),
                Preferences.getMetadataSyncCoverArtTotal()
        ));
        bind.metadataSyncLyricsValue.setText(formatCount(
                Preferences.getMetadataSyncLyricsCurrent(),
                Preferences.getMetadataSyncLyricsTotal()
        ));
    }

    private void renderMetadataStorage() {
        if (bind == null) return;
        CacheRepository cacheRepository = new CacheRepository();
        List<String> keys = Arrays.asList("playlists", "artists_all", "genres_all", "albums_all", "songs_all");
        List<String> likeKeys = Arrays.asList(
                "lyrics_song_%",
                "album_tracks_%",
                "playlist_songs_%",
                "artist_info_%",
                "album_info_%",
                "jf_%"
        );
        AtomicLong totalBytes = new AtomicLong(0);
        AtomicInteger pending = new AtomicInteger(1 + likeKeys.size());

        CacheRepository.CacheResult<Long> accumulator = size -> {
            if (size != null && size > 0) {
                totalBytes.addAndGet(size);
            }
            if (pending.decrementAndGet() == 0) {
                String formatted = Formatter.formatShortFileSize(requireContext(), totalBytes.get());
                Runnable update = () -> {
                    if (bind == null) return;
                    bind.metadataSyncStorageValue.setText(formatted);
                };
                if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                    update.run();
                } else if (isAdded()) {
                    requireActivity().runOnUiThread(update);
                }
            }
        };

        cacheRepository.loadPayloadSize(keys, accumulator);
        for (String likeKey : likeKeys) {
            cacheRepository.loadPayloadSizeLike(likeKey, accumulator);
        }
    }

    private void setupLogList() {
        if (bind == null) return;
        if (logAdapter == null) {
            logAdapter = new MetadataSyncLogAdapter();
        }
        bind.metadataSyncLogRecycler.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(requireContext()));
        bind.metadataSyncLogRecycler.setAdapter(logAdapter);
    }

    private void renderSyncLogs() {
        if (bind == null || logAdapter == null) return;
        List<one.chandan.rubato.util.MetadataSyncLogEntry> logs = Preferences.getMetadataSyncLogs();
        logAdapter.setItems(logs);
        boolean empty = logs == null || logs.isEmpty();
        bind.metadataSyncLogEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        bind.metadataSyncLogRecycler.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private String formatCount(int current, int total) {
        if (total > 0) {
            return getString(R.string.metadata_sync_count_with_total, current, total);
        }
        return String.valueOf(Math.max(current, 0));
    }

    private <T> void loadCount(CacheRepository cacheRepository, String key, java.lang.reflect.Type type, TextView target) {
        cacheRepository.loadOrNull(key, type, value -> {
            int count = 0;
            if (value instanceof List) {
                count = ((List<?>) value).size();
            }
            int finalCount = count;
            Runnable update = () -> {
                if (bind == null || target == null) return;
                target.setText(String.valueOf(finalCount));
            };
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                update.run();
            } else if (isAdded()) {
                requireActivity().runOnUiThread(update);
            }
        });
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
            case MetadataSyncManager.STAGE_PLAYLISTS:
                return getString(R.string.metadata_sync_stage_playlists);
            case MetadataSyncManager.STAGE_JELLYFIN:
                return getString(R.string.metadata_sync_stage_jellyfin);
            case MetadataSyncManager.STAGE_ARTISTS:
                return getString(R.string.metadata_sync_stage_artists);
            case MetadataSyncManager.STAGE_ARTIST_DETAILS:
                return getString(R.string.metadata_sync_stage_artist_details);
            case MetadataSyncManager.STAGE_GENRES:
                return getString(R.string.metadata_sync_stage_genres);
            case MetadataSyncManager.STAGE_ALBUMS:
                return getString(R.string.metadata_sync_stage_albums);
            case MetadataSyncManager.STAGE_ALBUM_DETAILS:
                return getString(R.string.metadata_sync_stage_album_details);
            case MetadataSyncManager.STAGE_SONGS:
                return getString(R.string.metadata_sync_stage_songs);
            case MetadataSyncManager.STAGE_COVER_ART:
                return getString(R.string.metadata_sync_stage_cover_art);
            case MetadataSyncManager.STAGE_LYRICS:
                return getString(R.string.metadata_sync_stage_lyrics);
            case MetadataSyncManager.STAGE_PREPARING:
            default:
                return getString(R.string.metadata_sync_stage_preparing);
        }
    }
}
