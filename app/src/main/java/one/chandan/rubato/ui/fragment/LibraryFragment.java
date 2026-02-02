package one.chandan.rubato.ui.fragment;

import android.os.Bundle;
import android.os.Handler;
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
import one.chandan.rubato.subsonic.models.Playlist;
import one.chandan.rubato.util.Constants;
import one.chandan.rubato.util.NetworkUtil;
import one.chandan.rubato.util.Preferences;
import one.chandan.rubato.util.TelemetryLogger;
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
    }

    @Override
    public void onStart() {
        super.onStart();
        activity.setBottomNavigationBarVisibility(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshPlaylistView();
        TelemetryLogger.logEvent("Library", "screen_view", null, 0);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
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
        libraryViewModel.getLibrarySources(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), sources -> {
            if (sources == null) {
                if (bind != null) bind.libraryMusicFolderSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.libraryMusicFolderSector.setVisibility(!sources.isEmpty() ? View.VISIBLE : View.GONE);

                musicFolderAdapter.setItems(sources);
                logRenderOnce("music_folders");
            }
        });
    }

    private void initAlbumView() {
        bind.albumRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        bind.albumRecyclerView.setHasFixedSize(true);
        bind.albumRecyclerView.setItemAnimator(null);
        bind.albumRecyclerView.setItemViewCacheSize(8);
        bind.albumRecyclerView.setRecycledViewPool(albumPool);

        albumAdapter = new AlbumAdapter(this);
        bind.albumRecyclerView.setAdapter(albumAdapter);
        libraryViewModel.getAlbumSample(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), albums -> {
            if (albums == null) {
                if (bind != null) bind.libraryAlbumSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.libraryAlbumSector.setVisibility(!albums.isEmpty() ? View.VISIBLE : View.GONE);

                albumAdapter.setItems(albums);
                logRenderOnce("albums");
            }
        });

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
        libraryViewModel.getArtistSample(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), artists -> {
            if (artists == null) {
                if (bind != null) bind.libraryArtistSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.libraryArtistSector.setVisibility(!artists.isEmpty() ? View.VISIBLE : View.GONE);

                artistAdapter.setItems(artists);
                logRenderOnce("artists");
            }
        });

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

        libraryViewModel.getGenreSample(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), genres -> {
            if (genres == null) {
                if (bind != null) bind.libraryGenresSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.libraryGenresSector.setVisibility(!genres.isEmpty() ? View.VISIBLE : View.GONE);

                genreAdapter.setItems(genres);
                logRenderOnce("genres");
            }
        });

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
        libraryViewModel.getPlaylistSample(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), playlists -> {
            if (playlists == null) {
                if (bind != null) bind.libraryPlaylistSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.libraryPlaylistSector.setVisibility(!playlists.isEmpty() ? View.VISIBLE : View.GONE);

                applySavedPlaylistOrder(playlists);
                logRenderOnce("playlists");
            }
        });
    }

    private void logRenderOnce(String detail) {
        if (renderLogged) return;
        renderLogged = true;
        long duration = Math.max(0, SystemClock.elapsedRealtime() - renderStartMs);
        TelemetryLogger.logEvent("Library", "render", detail, duration);
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
        if (!NetworkUtil.isOffline()) {
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
            activity.navController.navigate(R.id.musicSourcesFragment);
            return;
        }
        Navigation.findNavController(requireView()).navigate(R.id.indexFragment, bundle);
    }
}
