package one.chandan.rubato.ui.fragment;

import android.content.ComponentName;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaBrowser;
import androidx.media3.session.SessionToken;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;

import one.chandan.rubato.R;
import one.chandan.rubato.App;
import one.chandan.rubato.databinding.FragmentDownloadBinding;
import one.chandan.rubato.helper.recyclerview.QueueSwipeHelper;
import one.chandan.rubato.interfaces.ClickCallback;
import one.chandan.rubato.model.DownloadStack;
import one.chandan.rubato.service.MediaManager;
import one.chandan.rubato.service.MediaService;
import one.chandan.rubato.subsonic.models.Child;
import one.chandan.rubato.ui.activity.MainActivity;
import one.chandan.rubato.ui.adapter.DownloadHorizontalAdapter;
import one.chandan.rubato.ui.state.DownloadUiState;
import one.chandan.rubato.util.Constants;
import one.chandan.rubato.util.FavoriteUtil;
import one.chandan.rubato.util.Preferences;
import one.chandan.rubato.util.TelemetryLogger;
import one.chandan.rubato.viewmodel.DownloadViewModel;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.snackbar.Snackbar;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@UnstableApi
public class DownloadFragment extends Fragment implements ClickCallback {
    private static final String TAG = "DownloadFragment";

    private FragmentDownloadBinding bind;
    private MainActivity activity;
    private DownloadViewModel downloadViewModel;

    private DownloadHorizontalAdapter downloadHorizontalAdapter;
    private LinearLayoutManager downloadLayoutManager;
    private List<Child> currentDownloads = Collections.emptyList();
    private List<DownloadStack> currentStack = Collections.emptyList();
    private String lastRenderKey = "";
    private long renderStartMs = 0L;
    private boolean renderLogged = false;
    private ItemTouchHelper downloadSwipeHelper;

    private ListenableFuture<MediaBrowser> mediaBrowserListenableFuture;

    private MaterialToolbar materialToolbar;
    private OnBackPressedCallback backPressedCallback;
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
                        || Preferences.METADATA_SYNC_LYRICS_TOTAL.equals(key)) {
                    renderMetadataSyncProgress();
                }
            };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();

        bind = FragmentDownloadBinding.inflate(inflater, container, false);
        View view = bind.getRoot();
        downloadViewModel = new ViewModelProvider(requireActivity()).get(DownloadViewModel.class);

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        renderStartMs = SystemClock.elapsedRealtime();
        renderLogged = false;
        initAppBar();
        initDownloadedView();
        renderMetadataSyncProgress();
    }

    @Override
    public void onStart() {
        super.onStart();

        initializeMediaBrowser();
        App.getInstance().getPreferences().registerOnSharedPreferenceChangeListener(metadataSyncListener);
        renderMetadataSyncProgress();
        activity.setBottomNavigationBarVisibility(true);
        activity.setBottomSheetVisibility(true);
    }

    @Override
    public void onStop() {
        releaseMediaBrowser();
        App.getInstance().getPreferences().unregisterOnSharedPreferenceChangeListener(metadataSyncListener);
        super.onStop();
    }

    @Override
    public void onResume() {
        super.onResume();
        TelemetryLogger.logEvent("Downloads", "screen_view", null, 0);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (downloadSwipeHelper != null) {
            downloadSwipeHelper.attachToRecyclerView(null);
        }
        bind = null;
    }

    private void initAppBar() {
        materialToolbar = bind.getRoot().findViewById(R.id.toolbar);

        activity.setSupportActionBar(materialToolbar);
        Objects.requireNonNull(materialToolbar.getOverflowIcon()).setTint(requireContext().getResources().getColor(R.color.titleTextColor, null));
    }

    private void initDownloadedView() {
        bind.downloadedRecyclerView.setHasFixedSize(true);
        bind.downloadedRecyclerView.setItemViewCacheSize(12);
        bind.downloadedRecyclerView.setItemAnimator(null);

        downloadLayoutManager = new LinearLayoutManager(requireContext());
        bind.downloadedRecyclerView.setLayoutManager(downloadLayoutManager);
        downloadHorizontalAdapter = new DownloadHorizontalAdapter(this);
        bind.downloadedRecyclerView.setAdapter(downloadHorizontalAdapter);
        setupDownloadSwipeHelper();
        setupPlayButton();

        downloadViewModel.getUiState(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), state -> {
            if (state == null) return;
            currentDownloads = state.downloads != null ? state.downloads : Collections.emptyList();
            currentStack = state.stack != null ? state.stack : Collections.emptyList();
            renderDownloadList();
        });

        bind.downloadedGroupByImageView.setOnClickListener(view -> showPopupMenu(view, R.menu.download_popup_menu));
    }

    private void renderDownloadList() {
        if (bind == null) return;

        if (currentDownloads.isEmpty()) {
            bind.emptyDownloadLayout.setVisibility(View.VISIBLE);
            bind.downloadDownloadedSector.setVisibility(View.GONE);
            bind.downloadedGroupByImageView.setVisibility(View.GONE);
            bind.loadingProgressBar.setVisibility(View.GONE);
            return;
        }

        bind.emptyDownloadLayout.setVisibility(View.GONE);
        bind.downloadDownloadedSector.setVisibility(View.VISIBLE);
        bind.downloadedGroupByImageView.setVisibility(View.VISIBLE);
        bind.loadingProgressBar.setVisibility(View.GONE);

        if (currentStack == null || currentStack.isEmpty()) return;

        DownloadStack lastLevel = currentStack.get(currentStack.size() - 1);
        updateDownloadSwipeHelper(Constants.DOWNLOAD_TYPE_TRACK.equals(lastLevel.getView()));
        String renderKey = lastLevel.getId() + ":" + lastLevel.getView();
        boolean sameView = renderKey.equals(lastRenderKey);
        lastRenderKey = renderKey;

        androidx.recyclerview.widget.RecyclerView.LayoutManager layoutManager = bind.downloadedRecyclerView.getLayoutManager();
        android.os.Parcelable state = null;
        if (layoutManager != null && sameView) {
            state = layoutManager.onSaveInstanceState();
        }

        switch (lastLevel.getId()) {
            case Constants.DOWNLOAD_TYPE_TRACK:
                downloadHorizontalAdapter.setItems(Constants.DOWNLOAD_TYPE_TRACK, lastLevel.getId(), lastLevel.getView(), currentDownloads);
                break;
            case Constants.DOWNLOAD_TYPE_ALBUM:
                downloadHorizontalAdapter.setItems(Constants.DOWNLOAD_TYPE_TRACK, lastLevel.getId(), lastLevel.getView(), currentDownloads);
                break;
            case Constants.DOWNLOAD_TYPE_ARTIST:
                downloadHorizontalAdapter.setItems(Constants.DOWNLOAD_TYPE_ALBUM, lastLevel.getId(), lastLevel.getView(), currentDownloads);
                break;
            case Constants.DOWNLOAD_TYPE_GENRE:
                downloadHorizontalAdapter.setItems(Constants.DOWNLOAD_TYPE_TRACK, lastLevel.getId(), lastLevel.getView(), currentDownloads);
                break;
            case Constants.DOWNLOAD_TYPE_YEAR:
                downloadHorizontalAdapter.setItems(Constants.DOWNLOAD_TYPE_TRACK, lastLevel.getId(), lastLevel.getView(), currentDownloads);
                break;
            default:
                break;
        }

        if (layoutManager != null && state != null) {
            android.os.Parcelable restoreState = state;
            bind.downloadedRecyclerView.post(() -> layoutManager.onRestoreInstanceState(restoreState));
        }

        setupBackPressing(currentStack.size());
        setupShuffleButton();

        if (!renderLogged && !currentDownloads.isEmpty()) {
            renderLogged = true;
            long duration = Math.max(0, SystemClock.elapsedRealtime() - renderStartMs);
            TelemetryLogger.logEvent("Downloads", "render", "download_list", duration, TelemetryLogger.SOURCE_LIST, null);
        }
    }

    private void setupDownloadSwipeHelper() {
        downloadSwipeHelper = QueueSwipeHelper.attach(bind.downloadedRecyclerView, downloadHorizontalAdapter, position -> {
            if (downloadHorizontalAdapter == null) return null;
            return downloadHorizontalAdapter.getItem(position);
        }, new QueueSwipeHelper.QueueSwipeAction() {
            @Override
            public boolean canPerform(Child song, QueueSwipeHelper.SwipeAction action) {
                return song != null;
            }

            @Override
            public void onSwipeAction(Child song, QueueSwipeHelper.SwipeAction action) {
                if (action == QueueSwipeHelper.SwipeAction.PLAY_NEXT) {
                    MediaManager.enqueue(mediaBrowserListenableFuture, song, true);
                    activity.setBottomSheetInPeek(true);
                    if (bind != null) {
                        Snackbar.make(bind.getRoot(), getString(R.string.queue_add_next_confirmation), Snackbar.LENGTH_SHORT).show();
                    }
                } else if (action == QueueSwipeHelper.SwipeAction.ADD_TO_QUEUE) {
                    MediaManager.enqueue(mediaBrowserListenableFuture, song, false);
                    activity.setBottomSheetInPeek(true);
                    if (bind != null) {
                        Snackbar.make(bind.getRoot(), getString(R.string.queue_add_later_confirmation), Snackbar.LENGTH_SHORT).show();
                    }
                } else if (action == QueueSwipeHelper.SwipeAction.TOGGLE_FAVORITE) {
                    boolean starred = FavoriteUtil.toggleFavorite(requireContext(), song);
                    if (bind != null) {
                        Snackbar.make(bind.getRoot(), getString(starred ? R.string.favorite_added : R.string.favorite_removed), Snackbar.LENGTH_SHORT).show();
                    }
                }
            }

            @Override
            public void onSwipeRejected(Child song, QueueSwipeHelper.SwipeAction action) {
                if (bind != null) {
                    Snackbar.make(bind.getRoot(), getString(R.string.queue_add_next_unavailable_offline), Snackbar.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void updateDownloadSwipeHelper(boolean enabled) {
        if (downloadSwipeHelper == null) return;
        downloadSwipeHelper.attachToRecyclerView(enabled ? bind.downloadedRecyclerView : null);
    }


    private void renderMetadataSyncProgress() {
        if (bind == null) return;

        boolean active = Preferences.isMetadataSyncActive();
        if (!active) {
            bind.metadataSyncStatusTextView.setVisibility(View.GONE);
            bind.metadataSyncProgressBar.setVisibility(View.GONE);
            return;
        }

        String stageLabel = resolveMetadataStageLabel(Preferences.getMetadataSyncStage());
        int current = Preferences.getMetadataSyncProgressCurrent();
        int total = Preferences.getMetadataSyncProgressTotal();

        String statusText;
        if (total > 0) {
            statusText = getString(R.string.metadata_sync_status_with_total, stageLabel, current, total);
            bind.metadataSyncProgressBar.setIndeterminate(false);
            bind.metadataSyncProgressBar.setMax(total);
            bind.metadataSyncProgressBar.setProgressCompat(Math.min(current, total), false);
        } else if (current > 0) {
            statusText = getString(R.string.metadata_sync_status_with_count, stageLabel, current);
            bind.metadataSyncProgressBar.setIndeterminate(true);
        } else {
            statusText = getString(R.string.metadata_sync_status_format, stageLabel);
            bind.metadataSyncProgressBar.setIndeterminate(true);
        }

        bind.metadataSyncStatusTextView.setText(statusText);
        bind.metadataSyncStatusTextView.setVisibility(View.VISIBLE);
        bind.metadataSyncProgressBar.setVisibility(View.VISIBLE);
    }

    private String resolveMetadataStageLabel(@Nullable String stage) {
        if (stage == null) return getString(R.string.metadata_sync_stage_preparing);
        switch (stage) {
            case one.chandan.rubato.util.MetadataSyncManager.STAGE_PLAYLISTS:
                return getString(R.string.metadata_sync_stage_playlists);
            case one.chandan.rubato.util.MetadataSyncManager.STAGE_JELLYFIN:
                return getString(R.string.metadata_sync_stage_jellyfin);
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

    private void setupShuffleButton() {
        bind.shuffleDownloadedTextViewClickable.setOnClickListener(view -> {
            List<Child> songs = downloadHorizontalAdapter.getShuffling();

            if (songs != null && !songs.isEmpty()) {
                Collections.shuffle(songs);
                MediaManager.startQueue(mediaBrowserListenableFuture, songs, 0);
                activity.setBottomSheetInPeek(true);
            }
        });
    }

    private void setupPlayButton() {
        bind.downloadedPlayButton.setOnClickListener(view -> {
            List<Child> songs = downloadHorizontalAdapter.getOrderedPlaybackList();
            if (songs != null && !songs.isEmpty()) {
                MediaManager.startQueue(mediaBrowserListenableFuture, songs, 0);
                activity.setBottomSheetInPeek(true);
            }
        });
    }

    private void setupBackPressing(int stackSize) {
        if (backPressedCallback != null) {
            backPressedCallback.remove();
        }

        backPressedCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (stackSize > 1) {
                    downloadViewModel.popViewStack();
                } else {
                    activity.navController.navigateUp();
                }
                remove();
            }
        };

        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), backPressedCallback);
    }

    private void showPopupMenu(View view, int menuResource) {
        PopupMenu popup = new PopupMenu(requireContext(), view);
        popup.getMenuInflater().inflate(menuResource, popup.getMenu());

        popup.setOnMenuItemClickListener(menuItem -> {
            if (menuItem.getItemId() == R.id.menu_download_group_by_track) {
                downloadViewModel.initViewStack(new DownloadStack(Constants.DOWNLOAD_TYPE_TRACK, null));
                Preferences.setDefaultDownloadViewType(Constants.DOWNLOAD_TYPE_TRACK);
                return true;
            } else if (menuItem.getItemId() == R.id.menu_download_group_by_album) {
                downloadViewModel.initViewStack(new DownloadStack(Constants.DOWNLOAD_TYPE_ALBUM, null));
                Preferences.setDefaultDownloadViewType(Constants.DOWNLOAD_TYPE_ALBUM);
                return true;
            } else if (menuItem.getItemId() == R.id.menu_download_group_by_artist) {
                downloadViewModel.initViewStack(new DownloadStack(Constants.DOWNLOAD_TYPE_ARTIST, null));
                Preferences.setDefaultDownloadViewType(Constants.DOWNLOAD_TYPE_ARTIST);
                return true;
            } else if (menuItem.getItemId() == R.id.menu_download_group_by_genre) {
                downloadViewModel.initViewStack(new DownloadStack(Constants.DOWNLOAD_TYPE_GENRE, null));
                Preferences.setDefaultDownloadViewType(Constants.DOWNLOAD_TYPE_GENRE);
                return true;
            } else if (menuItem.getItemId() == R.id.menu_download_group_by_year) {
                downloadViewModel.initViewStack(new DownloadStack(Constants.DOWNLOAD_TYPE_YEAR, null));
                Preferences.setDefaultDownloadViewType(Constants.DOWNLOAD_TYPE_YEAR);
                return true;
            }

            return false;
        });

        popup.show();
    }

    private void initializeMediaBrowser() {
        mediaBrowserListenableFuture = new MediaBrowser.Builder(requireContext(), new SessionToken(requireContext(), new ComponentName(requireContext(), MediaService.class))).buildAsync();
    }

    private void releaseMediaBrowser() {
        MediaBrowser.releaseFuture(mediaBrowserListenableFuture);
    }

    @Override
    public void onYearClick(Bundle bundle) {
        downloadViewModel.pushViewStack(new DownloadStack(Constants.DOWNLOAD_TYPE_YEAR, bundle.getString(Constants.DOWNLOAD_TYPE_YEAR)));
    }

    @Override
    public void onGenreClick(Bundle bundle) {
        downloadViewModel.pushViewStack(new DownloadStack(Constants.DOWNLOAD_TYPE_GENRE, bundle.getString(Constants.DOWNLOAD_TYPE_GENRE)));
    }

    @Override
    public void onArtistClick(Bundle bundle) {
        downloadViewModel.pushViewStack(new DownloadStack(Constants.DOWNLOAD_TYPE_ARTIST, bundle.getString(Constants.DOWNLOAD_TYPE_ARTIST)));
    }

    @Override
    public void onAlbumClick(Bundle bundle) {
        downloadViewModel.pushViewStack(new DownloadStack(Constants.DOWNLOAD_TYPE_ALBUM, bundle.getString(Constants.DOWNLOAD_TYPE_ALBUM)));
    }

    @Override
    public void onMediaClick(Bundle bundle) {
        MediaManager.startQueue(mediaBrowserListenableFuture, bundle.getParcelableArrayList(Constants.TRACKS_OBJECT), bundle.getInt(Constants.ITEM_POSITION));
        activity.setBottomSheetInPeek(true);
    }

    @Override
    public void onMediaLongClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.songBottomSheetDialog, bundle);
    }

    @Override
    public void onDownloadGroupLongClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.downloadBottomSheetDialog, bundle);
    }
}
