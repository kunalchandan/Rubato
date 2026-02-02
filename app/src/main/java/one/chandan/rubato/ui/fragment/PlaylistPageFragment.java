package one.chandan.rubato.ui.fragment;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.SearchView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaBrowser;
import androidx.media3.session.SessionToken;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.bitmap.GranularRoundedCorners;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import one.chandan.rubato.R;
import one.chandan.rubato.databinding.FragmentPlaylistPageBinding;
import one.chandan.rubato.glide.CustomGlideRequest;
import one.chandan.rubato.helper.recyclerview.QueueSwipeHelper;
import one.chandan.rubato.interfaces.ClickCallback;
import one.chandan.rubato.model.Download;
import one.chandan.rubato.repository.LocalMusicRepository;
import one.chandan.rubato.service.MediaManager;
import one.chandan.rubato.service.MediaService;
import one.chandan.rubato.subsonic.models.Child;
import one.chandan.rubato.ui.activity.MainActivity;
import one.chandan.rubato.ui.adapter.SongHorizontalAdapter;
import one.chandan.rubato.util.Constants;
import one.chandan.rubato.util.DownloadUtil;
import one.chandan.rubato.util.FavoriteUtil;
import one.chandan.rubato.util.MappingUtil;
import one.chandan.rubato.util.MusicUtil;
import one.chandan.rubato.util.NetworkUtil;
import one.chandan.rubato.util.OfflineMediaUtil;
import one.chandan.rubato.util.PlaylistCoverCache;
import one.chandan.rubato.util.SearchIndexUtil;
import one.chandan.rubato.viewmodel.PlaylistPageViewModel;
import com.google.android.material.snackbar.Snackbar;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@UnstableApi
public class PlaylistPageFragment extends Fragment implements ClickCallback {
    private FragmentPlaylistPageBinding bind;
    private MainActivity activity;
    private PlaylistPageViewModel playlistPageViewModel;

    private SongHorizontalAdapter songHorizontalAdapter;
    private ItemTouchHelper playlistSongTouchHelper;
    private LiveData<List<one.chandan.rubato.subsonic.models.Child>> playlistSongsLive;

    private ListenableFuture<MediaBrowser> mediaBrowserListenableFuture;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.playlist_page_menu, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);

        SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setImeOptions(EditorInfo.IME_ACTION_DONE);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                searchView.clearFocus();
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                songHorizontalAdapter.getFilter().filter(newText);
                return false;
            }
        });

        searchView.setPadding(-32, 0, 0, 0);

        initMenuOption(menu);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();

        bind = FragmentPlaylistPageBinding.inflate(inflater, container, false);
        View view = bind.getRoot();
        playlistPageViewModel = new ViewModelProvider(requireActivity()).get(PlaylistPageViewModel.class);
        init();
        playlistSongsLive = playlistPageViewModel.getPlaylistSongLiveList();
        initAppBar();
        initMusicButton();
        initBackCover();
        initSongsView();

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();

        initializeMediaBrowser();
    }

    @Override
    public void onStop() {
        releaseMediaBrowser();
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        bind = null;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_download_playlist) {
            if (NetworkUtil.isOffline()) {
                if (bind != null) {
                    Snackbar.make(bind.getRoot(), getString(R.string.queue_add_next_unavailable_offline), Snackbar.LENGTH_SHORT).show();
                }
                return true;
            }
            if (playlistSongsLive == null) {
                return true;
            }
            playlistSongsLive.observe(getViewLifecycleOwner(), songs -> {
                if (isVisible() && getActivity() != null) {
                    DownloadUtil.getDownloadTracker(requireContext()).download(
                            MappingUtil.mapDownloads(songs),
                            songs.stream().map(child -> {
                                Download toDownload = new Download(child);
                                toDownload.setPlaylistId(playlistPageViewModel.getPlaylist().getId());
                                toDownload.setPlaylistName(playlistPageViewModel.getPlaylist().getName());
                                return toDownload;
                            }).collect(Collectors.toList())
                    );
                }
            });
            return true;
        } else if (item.getItemId() == R.id.action_pin_playlist) {
            playlistPageViewModel.setPinned(true);
            return true;
        } else if (item.getItemId() == R.id.action_unpin_playlist) {
            playlistPageViewModel.setPinned(false);
            return true;
        }

        return false;
    }

    private void init() {
        playlistPageViewModel.setPlaylist(requireArguments().getParcelable(Constants.PLAYLIST_OBJECT));
    }

    private void initMenuOption(Menu menu) {
        playlistPageViewModel.isPinned(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), isPinned -> {
            menu.findItem(R.id.action_unpin_playlist).setVisible(isPinned);
            menu.findItem(R.id.action_pin_playlist).setVisible(!isPinned);
        });
    }

    private void initAppBar() {
        activity.setSupportActionBar(bind.animToolbar);

        if (activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            activity.getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        bind.animToolbar.setTitle(playlistPageViewModel.getPlaylist().getName());

        bind.playlistNameLabel.setText(playlistPageViewModel.getPlaylist().getName());
        bind.playlistSongCountLabel.setText(getString(R.string.playlist_song_count, playlistPageViewModel.getPlaylist().getSongCount()));
        bind.playlistDurationLabel.setText(getString(R.string.playlist_duration, MusicUtil.getReadableDurationString(playlistPageViewModel.getPlaylist().getDuration(), false)));

        bind.animToolbar.setNavigationOnClickListener(v -> {
            hideKeyboard(v);
            activity.navController.navigateUp();
        });

        Objects.requireNonNull(bind.animToolbar.getOverflowIcon()).setTint(requireContext().getResources().getColor(R.color.titleTextColor, null));
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private void initMusicButton() {
        if (playlistSongsLive == null) {
            return;
        }
        playlistSongsLive.observe(getViewLifecycleOwner(), songs -> {
            if (bind != null) {
                bind.playlistPagePlayButton.setOnClickListener(v -> {
                    List<Child> playable = OfflineMediaUtil.filterPlayable(requireContext(), songs);
                    if (playable.isEmpty()) {
                        if (NetworkUtil.isOffline()) {
                            Snackbar.make(bind.getRoot(), getString(R.string.queue_add_next_unavailable_offline), Snackbar.LENGTH_SHORT).show();
                        }
                        return;
                    }
                    List<Child> limited = playable.subList(0, Math.min(100, playable.size()));
                    MediaManager.startQueue(mediaBrowserListenableFuture, limited, 0);
                    activity.setBottomSheetInPeek(true);
                });

                bind.playlistPageShuffleButton.setOnClickListener(v -> {
                    List<Child> playable = OfflineMediaUtil.filterPlayable(requireContext(), songs);
                    if (playable.isEmpty()) {
                        if (NetworkUtil.isOffline()) {
                            Snackbar.make(bind.getRoot(), getString(R.string.queue_add_next_unavailable_offline), Snackbar.LENGTH_SHORT).show();
                        }
                        return;
                    }
                    List<Child> shuffled = new ArrayList<>(playable);
                    Collections.shuffle(shuffled);
                    List<Child> limited = shuffled.subList(0, Math.min(100, shuffled.size()));
                    MediaManager.startQueue(mediaBrowserListenableFuture, limited, 0);
                    activity.setBottomSheetInPeek(true);
                });
                boolean hasPlayable = OfflineMediaUtil.hasPlayable(requireContext(), songs);
                bind.playlistPagePlayButton.setEnabled(hasPlayable);
                bind.playlistPageShuffleButton.setEnabled(hasPlayable);
            }
        });
    }

    private void initBackCover() {
        if (playlistSongsLive == null) {
            return;
        }
        playlistSongsLive.observe(getViewLifecycleOwner(), songs -> {
            if (bind != null && songs != null && !songs.isEmpty()) {
                List<one.chandan.rubato.subsonic.models.Child> coverSongs = pickCoverSongs(songs, 4);
                String fallbackCover = playlistPageViewModel.getPlaylist().getCoverArtId();
                String playlistId = playlistPageViewModel.getPlaylist().getId();
                boolean shouldPersist = playlistId != null && (fallbackCover == null || fallbackCover.isEmpty() || !PlaylistCoverCache.exists(requireContext(), playlistId));
                Drawable[] collageDrawables = new Drawable[4];
                AtomicInteger pending = new AtomicInteger(4);
                AtomicBoolean saved = new AtomicBoolean(false);

                // Pic top-left
                CustomGlideRequest.Builder
                        .from(requireContext(), resolveCoverArtId(coverSongs, 0, fallbackCover), CustomGlideRequest.ResourceType.Song)
                        .build()
                        .listener(createCoverListener(0, collageDrawables, pending, saved, shouldPersist, playlistId))
                        .transform(new GranularRoundedCorners(CustomGlideRequest.getCornerRadius(CustomGlideRequest.ResourceType.Song), 0, 0, 0))
                        .into(bind.playlistCoverImageViewTopLeft);

                // Pic top-right
                CustomGlideRequest.Builder
                        .from(requireContext(), resolveCoverArtId(coverSongs, 1, fallbackCover), CustomGlideRequest.ResourceType.Song)
                        .build()
                        .listener(createCoverListener(1, collageDrawables, pending, saved, shouldPersist, playlistId))
                        .transform(new GranularRoundedCorners(0, CustomGlideRequest.getCornerRadius(CustomGlideRequest.ResourceType.Song), 0, 0))
                        .into(bind.playlistCoverImageViewTopRight);

                // Pic bottom-left
                CustomGlideRequest.Builder
                        .from(requireContext(), resolveCoverArtId(coverSongs, 2, fallbackCover), CustomGlideRequest.ResourceType.Song)
                        .build()
                        .listener(createCoverListener(2, collageDrawables, pending, saved, shouldPersist, playlistId))
                        .transform(new GranularRoundedCorners(0, 0, 0, CustomGlideRequest.getCornerRadius(CustomGlideRequest.ResourceType.Song)))
                        .into(bind.playlistCoverImageViewBottomLeft);

                // Pic bottom-right
                CustomGlideRequest.Builder
                        .from(requireContext(), resolveCoverArtId(coverSongs, 3, fallbackCover), CustomGlideRequest.ResourceType.Song)
                        .build()
                        .listener(createCoverListener(3, collageDrawables, pending, saved, shouldPersist, playlistId))
                        .transform(new GranularRoundedCorners(0, 0, CustomGlideRequest.getCornerRadius(CustomGlideRequest.ResourceType.Song), 0))
                        .into(bind.playlistCoverImageViewBottomRight);
            }
        });
    }

    private String resolveCoverArtId(List<one.chandan.rubato.subsonic.models.Child> songs, int index, String fallback) {
        if (songs == null || songs.isEmpty()) return fallback;
        int safeIndex = Math.min(index, songs.size() - 1);
        String id = songs.get(safeIndex).getCoverArtId();
        return id != null ? id : fallback;
    }

    private List<one.chandan.rubato.subsonic.models.Child> pickCoverSongs(List<one.chandan.rubato.subsonic.models.Child> songs, int count) {
        if (songs == null || songs.isEmpty() || count <= 0) {
            return Collections.emptyList();
        }
        int target = Math.min(count, songs.size());
        List<one.chandan.rubato.subsonic.models.Child> picks = new ArrayList<>(target);
        Set<String> seenCovers = new HashSet<>();

        for (one.chandan.rubato.subsonic.models.Child song : songs) {
            if (picks.size() >= target) break;
            if (song == null) continue;
            String coverId = song.getCoverArtId();
            if (coverId == null || coverId.trim().isEmpty()) continue;
            if (seenCovers.add(coverId)) {
                picks.add(song);
            }
        }

        if (picks.size() < target) {
            for (one.chandan.rubato.subsonic.models.Child song : songs) {
                if (picks.size() >= target) break;
                if (song == null) continue;
                if (!picks.contains(song)) {
                    picks.add(song);
                }
            }
        }

        return picks;
    }

    private RequestListener<Drawable> createCoverListener(
            int index,
            Drawable[] drawables,
            AtomicInteger pending,
            AtomicBoolean saved,
            boolean shouldPersist,
            String playlistId
    ) {
        return new RequestListener<Drawable>() {
            @Override
            public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                drawables[index] = null;
                maybePersistCollage(drawables, pending, saved, shouldPersist, playlistId);
                return false;
            }

            @Override
            public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                drawables[index] = resource;
                maybePersistCollage(drawables, pending, saved, shouldPersist, playlistId);
                return false;
            }
        };
    }

    private void maybePersistCollage(Drawable[] drawables, AtomicInteger pending, AtomicBoolean saved, boolean shouldPersist, String playlistId) {
        if (!shouldPersist || saved.get()) {
            return;
        }
        if (pending.decrementAndGet() != 0) {
            return;
        }
        saved.set(true);
        if (playlistId == null || bind == null) return;
        List<Drawable> items = new ArrayList<>();
        Collections.addAll(items, drawables);
        PlaylistCoverCache.saveComposite(requireContext(), playlistId, items);
    }

    private void initSongsView() {
        bind.songRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        bind.songRecyclerView.setHasFixedSize(true);

        songHorizontalAdapter = new SongHorizontalAdapter(this, true, false, null, false);
        bind.songRecyclerView.setAdapter(songHorizontalAdapter);
        attachPlaylistSongTouchHelper();

        if (playlistSongsLive != null) {
            playlistSongsLive.observe(getViewLifecycleOwner(), songs -> songHorizontalAdapter.setItems(songs));
        }
    }

    private void attachPlaylistSongTouchHelper() {
        if (playlistSongTouchHelper != null) {
            playlistSongTouchHelper.attachToRecyclerView(null);
        }

        playlistSongTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            int fromPosition = -1;
            int toPosition = -1;
            private final android.graphics.Paint backgroundPaint = new android.graphics.Paint();
            private final android.graphics.Paint labelPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

            @Override
            public boolean isLongPressDragEnabled() {
                if (NetworkUtil.isOffline()) return false;
                return playlistPageViewModel == null
                        || playlistPageViewModel.getPlaylist() == null
                        || !SearchIndexUtil.isJellyfinTagged(playlistPageViewModel.getPlaylist().getId());
            }

            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                int swipeFlags = 0;
                QueueSwipeHelper.SwipeAction left = QueueSwipeHelper.resolveAction(requireContext(), ItemTouchHelper.LEFT);
                QueueSwipeHelper.SwipeAction right = QueueSwipeHelper.resolveAction(requireContext(), ItemTouchHelper.RIGHT);
                if (left != QueueSwipeHelper.SwipeAction.NONE) {
                    swipeFlags |= ItemTouchHelper.LEFT;
                }
                if (right != QueueSwipeHelper.SwipeAction.NONE) {
                    swipeFlags |= ItemTouchHelper.RIGHT;
                }
                return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, swipeFlags);
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                if (NetworkUtil.isOffline()) return false;
                if (playlistPageViewModel != null && playlistPageViewModel.getPlaylist() != null
                        && SearchIndexUtil.isJellyfinTagged(playlistPageViewModel.getPlaylist().getId())) {
                    return false;
                }

                fromPosition = viewHolder.getBindingAdapterPosition();
                toPosition = target.getBindingAdapterPosition();

                songHorizontalAdapter.swapItems(fromPosition, toPosition);
                return true;
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                if (NetworkUtil.isOffline()) {
                    if (bind != null) {
                        Snackbar.make(bind.getRoot(), getString(R.string.playlist_reorder_offline), Snackbar.LENGTH_SHORT).show();
                    }
                    fromPosition = -1;
                    toPosition = -1;
                    return;
                }

                if (fromPosition != -1 && toPosition != -1) {
                    if (playlistPageViewModel != null && playlistPageViewModel.getPlaylist() != null
                            && SearchIndexUtil.isJellyfinTagged(playlistPageViewModel.getPlaylist().getId())) {
                        if (bind != null) {
                            Snackbar.make(bind.getRoot(), getString(R.string.playlist_reorder_offline), Snackbar.LENGTH_SHORT).show();
                        }
                    } else {
                        playlistPageViewModel.updatePlaylistOrder(songHorizontalAdapter.getItems());
                        if (bind != null) {
                            Snackbar.make(bind.getRoot(), getString(R.string.playlist_reorder_saved), Snackbar.LENGTH_SHORT).show();
                        }
                    }
                }

                fromPosition = -1;
                toPosition = -1;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION) return;

                one.chandan.rubato.subsonic.models.Child song = songHorizontalAdapter.getItem(position);
                QueueSwipeHelper.SwipeAction swipeAction = QueueSwipeHelper.resolveAction(requireContext(), direction);
                if (swipeAction == QueueSwipeHelper.SwipeAction.NONE) {
                    Objects.requireNonNull(bind.songRecyclerView.getAdapter()).notifyItemChanged(position);
                    return;
                }

                if (swipeAction == QueueSwipeHelper.SwipeAction.TOGGLE_FAVORITE) {
                    viewHolder.itemView.setHapticFeedbackEnabled(true);
                    viewHolder.itemView.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM);
                    boolean starred = FavoriteUtil.toggleFavorite(requireContext(), song);
                    if (bind != null) {
                        Snackbar.make(bind.getRoot(), getString(starred ? R.string.favorite_added : R.string.favorite_removed), Snackbar.LENGTH_SHORT).show();
                    }
                } else {
                    boolean isLocal = LocalMusicRepository.isLocalSong(song);
                    boolean isDownloaded = isLocal || DownloadUtil.getDownloadTracker(requireContext()).isDownloaded(song.getId());
                    if (NetworkUtil.isOffline() && !isDownloaded) {
                        if (bind != null) {
                            Snackbar.make(bind.getRoot(), getString(R.string.queue_add_next_unavailable_offline), Snackbar.LENGTH_SHORT).show();
                        }
                    } else if (swipeAction == QueueSwipeHelper.SwipeAction.PLAY_NEXT) {
                        viewHolder.itemView.setHapticFeedbackEnabled(true);
                        viewHolder.itemView.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM);
                        MediaManager.enqueue(mediaBrowserListenableFuture, song, true);
                        activity.setBottomSheetInPeek(true);
                        if (bind != null) {
                            Snackbar.make(bind.getRoot(), getString(R.string.queue_add_next_confirmation), Snackbar.LENGTH_SHORT).show();
                        }
                    } else if (swipeAction == QueueSwipeHelper.SwipeAction.ADD_TO_QUEUE) {
                        viewHolder.itemView.setHapticFeedbackEnabled(true);
                        viewHolder.itemView.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM);
                        MediaManager.enqueue(mediaBrowserListenableFuture, song, false);
                        activity.setBottomSheetInPeek(true);
                        if (bind != null) {
                            Snackbar.make(bind.getRoot(), getString(R.string.queue_add_later_confirmation), Snackbar.LENGTH_SHORT).show();
                        }
                    }
                }

                Objects.requireNonNull(bind.songRecyclerView.getAdapter()).notifyItemChanged(position);
            }

            @Override
            public void onChildDraw(@NonNull Canvas canvas, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                QueueSwipeHelper.drawSwipe(canvas, viewHolder, dX, actionState, backgroundPaint, labelPaint);
                super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }
        });

        playlistSongTouchHelper.attachToRecyclerView(bind.songRecyclerView);
    }

    private void initializeMediaBrowser() {
        mediaBrowserListenableFuture = new MediaBrowser.Builder(requireContext(), new SessionToken(requireContext(), new ComponentName(requireContext(), MediaService.class))).buildAsync();
    }

    private void releaseMediaBrowser() {
        MediaBrowser.releaseFuture(mediaBrowserListenableFuture);
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
}
