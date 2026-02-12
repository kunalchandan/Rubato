package one.chandan.rubato.ui.fragment;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import one.chandan.rubato.R;
import one.chandan.rubato.databinding.FragmentLibraryBinding;
import one.chandan.rubato.helper.recyclerview.CustomLinearSnapHelper;
import one.chandan.rubato.interfaces.ClickCallback;
import one.chandan.rubato.interfaces.PlaylistCallback;
import one.chandan.rubato.ui.activity.MainActivity;
import one.chandan.rubato.ui.adapter.AlbumAdapter;
import one.chandan.rubato.ui.adapter.ArtistAdapter;
import one.chandan.rubato.ui.adapter.GenreAdapter;
import one.chandan.rubato.ui.adapter.MusicFolderAdapter;
import one.chandan.rubato.ui.adapter.PlaylistHorizontalAdapter;
import one.chandan.rubato.ui.dialog.PlaylistEditorDialog;
import one.chandan.rubato.model.LibrarySourceItem;
import one.chandan.rubato.model.LocalSource;
import one.chandan.rubato.repository.DirectoryRepository;
import one.chandan.rubato.subsonic.models.Playlist;
import one.chandan.rubato.util.Constants;
import one.chandan.rubato.util.OfflinePolicy;
import one.chandan.rubato.util.Preferences;
import one.chandan.rubato.util.TelemetryLogger;
import one.chandan.rubato.sync.SyncOrchestrator;
import one.chandan.rubato.viewmodel.LibraryViewModel;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@UnstableApi
public class LibraryFragment extends Fragment implements ClickCallback {
    private static final String TAG = "LibraryFragment";

    private FragmentLibraryBinding bind;
    private MainActivity activity;
    private LibraryViewModel libraryViewModel;

    private MusicFolderAdapter musicFolderAdapter;
    private AlbumAdapter albumAdapter;
    private ArtistAdapter artistAdapter;
    private GenreAdapter genreAdapter;
    private PlaylistHorizontalAdapter playlistHorizontalAdapter;
    private ItemTouchHelper playlistItemTouchHelper;
    private final RecyclerView.RecycledViewPool albumPool = new RecyclerView.RecycledViewPool();
    private final RecyclerView.RecycledViewPool artistPool = new RecyclerView.RecycledViewPool();
    private final RecyclerView.RecycledViewPool playlistPool = new RecyclerView.RecycledViewPool();

    private MaterialToolbar materialToolbar;
    private long renderStartMs = 0L;
    private boolean renderLogged = false;
    private final Handler syncHandler = new Handler(Looper.getMainLooper());
    private Runnable syncRefreshRunnable;
    private long lastSyncRefreshMs = 0L;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();

        bind = FragmentLibraryBinding.inflate(inflater, container, false);
        View view = bind.getRoot();
        libraryViewModel = new ViewModelProvider(requireActivity()).get(LibraryViewModel.class);

        init();

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        renderStartMs = SystemClock.elapsedRealtime();
        renderLogged = false;
        initAppBar();
        initMusicFolderView();
        initAlbumView();
        initArtistView();
        initGenreView();
        initPlaylistView();
        bindUiState();
        bindSyncState();
        restoreScrollPosition();
    }

    @Override
    public void onStart() {
        super.onStart();
        activity.setBottomNavigationBarVisibility(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        TelemetryLogger.logEvent("Library", "screen_view", null, 0);
    }

    @Override
    public void onStop() {
        persistScrollPosition();
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (syncRefreshRunnable != null) {
            syncHandler.removeCallbacks(syncRefreshRunnable);
            syncRefreshRunnable = null;
        }
        bind = null;
    }

    private void init() {
        bind.albumCatalogueTextViewClickable.setOnClickListener(v -> activity.navController.navigate(R.id.action_libraryFragment_to_albumCatalogueFragment));
        bind.artistCatalogueTextViewClickable.setOnClickListener(v -> activity.navController.navigate(R.id.action_libraryFragment_to_artistCatalogueFragment));
        bind.genreCatalogueTextViewClickable.setOnClickListener(v -> activity.navController.navigate(R.id.action_libraryFragment_to_genreCatalogueFragment));
        bind.playlistCatalogueTextViewClickable.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putString(Constants.PLAYLIST_ALL, Constants.PLAYLIST_ALL);
            activity.navController.navigate(R.id.action_libraryFragment_to_playlistCatalogueFragment, bundle);
        });

        bind.albumCatalogueSampleTextViewRefreshable.setOnLongClickListener(view -> {
            if (guardOffline(view)) return true;
            libraryViewModel.refreshAlbumSample(getViewLifecycleOwner());
            return true;
        });
        bind.artistCatalogueSampleTextViewRefreshable.setOnLongClickListener(view -> {
            if (guardOffline(view)) return true;
            libraryViewModel.refreshArtistSample(getViewLifecycleOwner());
            return true;
        });
        bind.genreCatalogueSampleTextViewRefreshable.setOnLongClickListener(view -> {
            if (guardOffline(view)) return true;
            libraryViewModel.refreshGenreSample(getViewLifecycleOwner());
            return true;
        });
        bind.playlistCatalogueSampleTextViewRefreshable.setOnLongClickListener(view -> {
            if (guardOffline(view)) return true;
            libraryViewModel.refreshPlaylistSample(getViewLifecycleOwner());
            return true;
        });
    }

    private void restoreScrollPosition() {
        if (bind == null || libraryViewModel == null) {
            return;
        }
        int scrollY = libraryViewModel.getLibraryScrollY();
        if (scrollY <= 0) {
            return;
        }
        bind.fragmentLibraryNestedScrollView.post(() ->
                bind.fragmentLibraryNestedScrollView.scrollTo(0, scrollY)
        );
    }

    private void persistScrollPosition() {
        if (bind == null || libraryViewModel == null) {
            return;
        }
        libraryViewModel.setLibraryScrollY(bind.fragmentLibraryNestedScrollView.getScrollY());
    }

    private void initAppBar() {
        materialToolbar = bind.getRoot().findViewById(R.id.toolbar);

        activity.setSupportActionBar(materialToolbar);
        Objects.requireNonNull(materialToolbar.getOverflowIcon()).setTint(requireContext().getResources().getColor(R.color.titleTextColor, null));
    }

    private void initMusicFolderView() {
        if (!Preferences.isMusicDirectorySectionVisible()) {
            bind.libraryMusicFolderSector.setVisibility(View.GONE);
            return;
        }

        bind.musicFolderRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        bind.musicFolderRecyclerView.setHasFixedSize(true);
        bind.musicFolderRecyclerView.setItemAnimator(null);
        bind.musicFolderRecyclerView.setItemViewCacheSize(8);

        musicFolderAdapter = new MusicFolderAdapter(this);
        bind.musicFolderRecyclerView.setAdapter(musicFolderAdapter);
    }

    private void initAlbumView() {
        bind.albumRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        bind.albumRecyclerView.setHasFixedSize(true);
        bind.albumRecyclerView.setItemAnimator(null);
        bind.albumRecyclerView.setItemViewCacheSize(8);
        bind.albumRecyclerView.setRecycledViewPool(albumPool);

        albumAdapter = new AlbumAdapter(this);
        bind.albumRecyclerView.setAdapter(albumAdapter);

        CustomLinearSnapHelper albumSnapHelper = new CustomLinearSnapHelper();
        albumSnapHelper.attachToRecyclerView(bind.albumRecyclerView);
    }

    private void initArtistView() {
        bind.artistRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        bind.artistRecyclerView.setHasFixedSize(true);
        bind.artistRecyclerView.setItemAnimator(null);
        bind.artistRecyclerView.setItemViewCacheSize(8);
        bind.artistRecyclerView.setRecycledViewPool(artistPool);

        artistAdapter = new ArtistAdapter(this, false, false);
        bind.artistRecyclerView.setAdapter(artistAdapter);

        CustomLinearSnapHelper artistSnapHelper = new CustomLinearSnapHelper();
        artistSnapHelper.attachToRecyclerView(bind.artistRecyclerView);
    }

    private void initGenreView() {
        bind.genreRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 3, GridLayoutManager.HORIZONTAL, false));
        bind.genreRecyclerView.setHasFixedSize(true);
        bind.genreRecyclerView.setItemAnimator(null);
        bind.genreRecyclerView.setItemViewCacheSize(8);

        genreAdapter = new GenreAdapter(this);
        bind.genreRecyclerView.setAdapter(genreAdapter);

        CustomLinearSnapHelper genreSnapHelper = new CustomLinearSnapHelper();
        genreSnapHelper.attachToRecyclerView(bind.genreRecyclerView);
    }

    private void initPlaylistView() {
        bind.playlistRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        bind.playlistRecyclerView.setHasFixedSize(true);
        bind.playlistRecyclerView.setItemAnimator(null);
        bind.playlistRecyclerView.setItemViewCacheSize(8);
        bind.playlistRecyclerView.setRecycledViewPool(playlistPool);

        playlistHorizontalAdapter = new PlaylistHorizontalAdapter(this, false);
        bind.playlistRecyclerView.setAdapter(playlistHorizontalAdapter);
        attachPlaylistReorder();
    }

    private void bindUiState() {
        libraryViewModel.getUiState(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), state -> {
            if (state == null || bind == null) {
                return;
            }

            if (!Preferences.isMusicDirectorySectionVisible()) {
                bind.libraryMusicFolderSector.setVisibility(View.GONE);
            } else {
                List<LibrarySourceItem> sources = state.getSources();
                boolean hasSources = sources != null && !sources.isEmpty();
                bind.libraryMusicFolderSector.setVisibility(hasSources ? View.VISIBLE : View.GONE);
                if (musicFolderAdapter != null && sources != null) {
                    musicFolderAdapter.setItems(sources);
                }
                if (hasSources) logRenderOnce("music_folders");
            }

            List<one.chandan.rubato.subsonic.models.AlbumID3> albums = state.getAlbums();
            boolean hasAlbums = albums != null && !albums.isEmpty();
            bind.libraryAlbumSector.setVisibility(hasAlbums ? View.VISIBLE : View.GONE);
            if (albumAdapter != null && albums != null) {
                albumAdapter.setItems(albums);
            }
            if (hasAlbums) logRenderOnce("albums");

            List<one.chandan.rubato.subsonic.models.ArtistID3> artists = state.getArtists();
            boolean hasArtists = artists != null && !artists.isEmpty();
            bind.libraryArtistSector.setVisibility(hasArtists ? View.VISIBLE : View.GONE);
            if (artistAdapter != null && artists != null) {
                artistAdapter.setItems(artists);
            }
            if (hasArtists) logRenderOnce("artists");

            List<one.chandan.rubato.subsonic.models.Genre> genres = state.getGenres();
            boolean hasGenres = genres != null && !genres.isEmpty();
            bind.libraryGenresSector.setVisibility(hasGenres ? View.VISIBLE : View.GONE);
            if (genreAdapter != null && genres != null) {
                genreAdapter.setItems(genres);
            }
            if (hasGenres) logRenderOnce("genres");

            List<Playlist> playlists = state.getPlaylists();
            boolean hasPlaylists = playlists != null && !playlists.isEmpty();
            bind.libraryPlaylistSector.setVisibility(hasPlaylists ? View.VISIBLE : View.GONE);
            if (playlists != null) {
                applySavedPlaylistOrder(playlists);
            }
            if (hasPlaylists) logRenderOnce("playlists");

            if (!state.getLoading()) {
                logRenderOnce("ui_state");
            }
        });
    }

    private void bindSyncState() {
        SyncOrchestrator.getSyncState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) {
                return;
            }
            if (state.getActive()) {
                return;
            }

            long completedAt = state.getLastCompletedAt();
            long lastHandled = libraryViewModel != null ? libraryViewModel.getLastSyncCompletedAt() : 0L;
            if (completedAt > 0 && completedAt != lastHandled) {
                if (libraryViewModel != null) {
                    libraryViewModel.setLastSyncCompletedAt(completedAt);
                }
                scheduleSyncRefresh();
            }
        });
    }

    private void scheduleSyncRefresh() {
        if (syncRefreshRunnable != null) {
            syncHandler.removeCallbacks(syncRefreshRunnable);
        }

        long now = SystemClock.elapsedRealtime();
        long minIntervalMs = 1500L;
        long delay = Math.max(0L, minIntervalMs - (now - lastSyncRefreshMs));

        syncRefreshRunnable = () -> {
            if (bind == null || libraryViewModel == null) return;
            lastSyncRefreshMs = SystemClock.elapsedRealtime();
            libraryViewModel.refreshAlbumSample(getViewLifecycleOwner());
            libraryViewModel.refreshArtistSample(getViewLifecycleOwner());
            libraryViewModel.refreshGenreSample(getViewLifecycleOwner());
            libraryViewModel.refreshPlaylistSample(getViewLifecycleOwner());
        };

        syncHandler.postDelayed(syncRefreshRunnable, delay);
    }

    private void logRenderOnce(String detail) {
        if (renderLogged) return;
        renderLogged = true;
        long duration = Math.max(0, SystemClock.elapsedRealtime() - renderStartMs);
        TelemetryLogger.logEvent("Library", "render", detail, duration, TelemetryLogger.SOURCE_LIST, null);
    }

    private void refreshPlaylistView() {
        final Handler handler = new Handler();

        final Runnable runnable = () -> {
            if (getView() != null && bind != null && libraryViewModel != null)
                libraryViewModel.refreshPlaylistSample(getViewLifecycleOwner());
        };

        handler.postDelayed(runnable, 100);
    }

    private void attachPlaylistReorder() {
        if (playlistItemTouchHelper != null) {
            playlistItemTouchHelper.attachToRecyclerView(null);
        }

        playlistItemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            int fromPosition = -1;
            int toPosition = -1;

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                fromPosition = viewHolder.getBindingAdapterPosition();
                toPosition = target.getBindingAdapterPosition();

                if (fromPosition == RecyclerView.NO_POSITION || toPosition == RecyclerView.NO_POSITION) return false;

                playlistHorizontalAdapter.swapItems(fromPosition, toPosition);
                return true;
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                if (fromPosition != -1 && toPosition != -1) {
                    persistPlaylistOrder();
                }
                fromPosition = -1;
                toPosition = -1;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            }
        });

        playlistItemTouchHelper.attachToRecyclerView(bind.playlistRecyclerView);
    }

    private void applySavedPlaylistOrder(List<Playlist> playlists) {
        List<String> order = Preferences.getLibraryPlaylistOrder();
        if (order == null || order.isEmpty()) {
            playlistHorizontalAdapter.setItems(playlists);
            return;
        }

        Map<String, Playlist> playlistMap = new HashMap<>();
        for (Playlist playlist : playlists) {
            playlistMap.put(playlist.getId(), playlist);
        }

        List<Playlist> ordered = new ArrayList<>();
        for (String id : order) {
            Playlist playlist = playlistMap.remove(id);
            if (playlist != null) ordered.add(playlist);
        }

        ordered.addAll(playlistMap.values());
        playlistHorizontalAdapter.setItems(ordered);
    }

    private void persistPlaylistOrder() {
        List<Playlist> items = playlistHorizontalAdapter.getItems();
        if (items == null || items.isEmpty()) return;

        List<String> orderedIds = new ArrayList<>();
        for (Playlist playlist : items) {
            orderedIds.add(playlist.getId());
        }

        Preferences.setLibraryPlaylistOrder(orderedIds);
    }

    private boolean guardOffline(View anchor) {
        if (!OfflinePolicy.isOffline()) {
            return false;
        }
        if (anchor != null) {
            Snackbar.make(anchor, getString(R.string.queue_add_next_unavailable_offline), Snackbar.LENGTH_SHORT).show();
        }
        return true;
    }

    @Override
    public void onAlbumClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.albumPageFragment, bundle);
    }

    @Override
    public void onAlbumLongClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.albumBottomSheetDialog, bundle);
    }

    @Override
    public void onArtistClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.artistPageFragment, bundle);
    }

    @Override
    public void onArtistLongClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.artistBottomSheetDialog, bundle);
    }

    @Override
    public void onGenreClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.songListPageFragment, bundle);
    }

    @Override
    public void onPlaylistClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.playlistPageFragment, bundle);
    }

    @Override
    public void onPlaylistLongClick(Bundle bundle) {
        PlaylistEditorDialog dialog = new PlaylistEditorDialog(new PlaylistCallback() {
            @Override
            public void onDismiss() {
                refreshPlaylistView();
            }
        });

        dialog.setArguments(bundle);
        dialog.show(activity.getSupportFragmentManager(), null);
    }

    @Override
    public void onMusicFolderClick(Bundle bundle) {
        if (bundle.containsKey(Constants.LOCAL_SOURCE_OBJECT)) {
            LocalSource source = bundle.getParcelable(Constants.LOCAL_SOURCE_OBJECT);
            if (source != null) {
                Bundle dirBundle = new Bundle();
                dirBundle.putString(Constants.MUSIC_DIRECTORY_ID, DirectoryRepository.buildLocalDirectoryId(source.getTreeUri()));
                Navigation.findNavController(requireView()).navigate(R.id.directoryFragment, dirBundle);
                return;
            }
            activity.navController.navigate(R.id.musicSourcesFragment);
            return;
        }
        Navigation.findNavController(requireView()).navigate(R.id.indexFragment, bundle);
    }
}
