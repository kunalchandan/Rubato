package one.chandan.rubato.ui.fragment;

import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaBrowser;
import androidx.media3.session.SessionToken;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import one.chandan.rubato.R;
import one.chandan.rubato.databinding.FragmentAlbumPageBinding;
import one.chandan.rubato.glide.CustomGlideRequest;
import one.chandan.rubato.helper.recyclerview.QueueSwipeHelper;
import one.chandan.rubato.interfaces.ClickCallback;
import one.chandan.rubato.model.Download;
import one.chandan.rubato.repository.LocalMusicRepository;
import one.chandan.rubato.service.MediaManager;
import one.chandan.rubato.service.MediaService;
import one.chandan.rubato.ui.activity.MainActivity;
import one.chandan.rubato.ui.adapter.SongHorizontalAdapter;
import one.chandan.rubato.util.Constants;
import one.chandan.rubato.util.DownloadUtil;
import one.chandan.rubato.util.FavoriteUtil;
import one.chandan.rubato.util.MappingUtil;
import one.chandan.rubato.util.MusicUtil;
import one.chandan.rubato.util.NetworkUtil;
import one.chandan.rubato.util.OfflineMediaUtil;
import one.chandan.rubato.viewmodel.AlbumPageViewModel;
import com.google.android.material.snackbar.Snackbar;
import com.google.common.util.concurrent.ListenableFuture;

import one.chandan.rubato.subsonic.models.Child;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@UnstableApi
public class AlbumPageFragment extends Fragment implements ClickCallback {
    private FragmentAlbumPageBinding bind;
    private MainActivity activity;
    private AlbumPageViewModel albumPageViewModel;
    private SongHorizontalAdapter songHorizontalAdapter;
    private ListenableFuture<MediaBrowser> mediaBrowserListenableFuture;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.album_page_menu, menu);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();

        bind = FragmentAlbumPageBinding.inflate(inflater, container, false);
        View view = bind.getRoot();
        albumPageViewModel = new ViewModelProvider(requireActivity()).get(AlbumPageViewModel.class);

        init();
        initAppBar();
        initAlbumInfoTextButton();
        initAlbumNotes();
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
        if (item.getItemId() == R.id.action_download_album) {
            if (NetworkUtil.isOffline()) {
                if (bind != null) {
                    Snackbar.make(bind.getRoot(), getString(R.string.queue_add_next_unavailable_offline), Snackbar.LENGTH_SHORT).show();
                }
                return true;
            }
            albumPageViewModel.getAlbumSongLiveList().observe(getViewLifecycleOwner(), songs -> {
                DownloadUtil.getDownloadTracker(requireContext()).download(MappingUtil.mapDownloads(songs), songs.stream().map(Download::new).collect(Collectors.toList()));
            });
            return true;
        }

        return false;
    }

    private void init() {
        albumPageViewModel.setAlbum(getViewLifecycleOwner(), requireArguments().getParcelable(Constants.ALBUM_OBJECT));
    }

    private void initAppBar() {
        activity.setSupportActionBar(bind.animToolbar);

        if (activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            activity.getSupportActionBar().setDisplayShowHomeEnabled(true);

        }

        albumPageViewModel.getAlbum().observe(getViewLifecycleOwner(), album -> {
            if (bind != null && album != null) {
                bind.animToolbar.setTitle(album.getName());

                bind.albumNameLabel.setText(album.getName());
                bind.albumArtistLabel.setText(album.getArtist());
                bind.albumReleaseYearLabel.setText(album.getYear() != 0 ? String.valueOf(album.getYear()) : "");
                bind.albumSongCountDurationTextview.setText(getString(R.string.album_page_tracks_count_and_duration, album.getSongCount(), album.getDuration() != null ? album.getDuration() / 60 : 0));
                bind.albumGenresTextview.setText(album.getGenre());

                if (album.getReleaseDate() != null && album.getOriginalReleaseDate() != null) {
                    bind.albumReleaseYearsTextview.setVisibility(View.VISIBLE);

                    if (album.getReleaseDate() == null || album.getOriginalReleaseDate() == null) {
                        bind.albumReleaseYearsTextview.setText(getString(R.string.album_page_release_date_label, album.getReleaseDate() != null ? album.getReleaseDate().getFormattedDate() : album.getOriginalReleaseDate().getFormattedDate()));
                    }

                    if (album.getReleaseDate() != null && album.getOriginalReleaseDate() != null) {
                        if (Objects.equals(album.getReleaseDate().getYear(), album.getOriginalReleaseDate().getYear()) && Objects.equals(album.getReleaseDate().getMonth(), album.getOriginalReleaseDate().getMonth()) && Objects.equals(album.getReleaseDate().getDay(), album.getOriginalReleaseDate().getDay())) {
                            bind.albumReleaseYearsTextview.setText(getString(R.string.album_page_release_date_label, album.getReleaseDate().getFormattedDate()));
                        } else {
                            bind.albumReleaseYearsTextview.setText(getString(R.string.album_page_release_dates_label, album.getReleaseDate().getFormattedDate(), album.getOriginalReleaseDate().getFormattedDate()));
                        }
                    }
                }
            }
        });

        bind.animToolbar.setNavigationOnClickListener(v -> activity.navController.navigateUp());

        Objects.requireNonNull(bind.animToolbar.getOverflowIcon()).setTint(requireContext().getResources().getColor(R.color.titleTextColor, null));

        bind.albumOtherInfoButton.setOnClickListener(v -> {
            if (bind.albumDetailView.getVisibility() == View.GONE) {
                bind.albumDetailView.setVisibility(View.VISIBLE);
            } else if (bind.albumDetailView.getVisibility() == View.VISIBLE) {
                bind.albumDetailView.setVisibility(View.GONE);
            }
        });
    }

    private void initAlbumInfoTextButton() {
        bind.albumArtistLabel.setOnClickListener(v -> albumPageViewModel.getArtist().observe(getViewLifecycleOwner(), artist -> {
            if (artist != null) {
                Bundle bundle = new Bundle();
                bundle.putParcelable(Constants.ARTIST_OBJECT, artist);
                activity.navController.navigate(R.id.action_albumPageFragment_to_artistPageFragment, bundle);
            } else
                Toast.makeText(requireContext(), getString(R.string.album_error_retrieving_artist), Toast.LENGTH_SHORT).show();
        }));
    }

    private void initAlbumNotes() {
        albumPageViewModel.getAlbumInfo().observe(getViewLifecycleOwner(), albumInfo -> {
            if (albumInfo != null) {
                if (bind != null) bind.albumNotesTextview.setVisibility(View.VISIBLE);
                if (bind != null)
                    bind.albumNotesTextview.setText(MusicUtil.forceReadableString(albumInfo.getNotes()));

                if (bind != null && albumInfo.getLastFmUrl() != null && !albumInfo.getLastFmUrl().isEmpty()) {
                    bind.albumNotesTextview.setOnClickListener(v -> {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setData(Uri.parse(albumInfo.getLastFmUrl()));
                        startActivity(intent);
                    });
                }
            } else {
                if (bind != null) bind.albumNotesTextview.setVisibility(View.GONE);
            }
        });
    }

    private void initMusicButton() {
        albumPageViewModel.getAlbumSongLiveList().observe(getViewLifecycleOwner(), songs -> {
            if (bind != null && songs != null && !songs.isEmpty()) {
                bind.albumPagePlayButton.setOnClickListener(v -> {
                    List<Child> playable = OfflineMediaUtil.filterPlayable(requireContext(), songs);
                    if (playable.isEmpty()) {
                        if (NetworkUtil.isOffline()) {
                            Snackbar.make(bind.getRoot(), getString(R.string.queue_add_next_unavailable_offline), Snackbar.LENGTH_SHORT).show();
                        }
                        return;
                    }
                    MediaManager.startQueue(mediaBrowserListenableFuture, playable, 0);
                    activity.setBottomSheetInPeek(true);
                });

                bind.albumPageShuffleButton.setOnClickListener(v -> {
                    List<Child> playable = OfflineMediaUtil.filterPlayable(requireContext(), songs);
                    if (playable.isEmpty()) {
                        if (NetworkUtil.isOffline()) {
                            Snackbar.make(bind.getRoot(), getString(R.string.queue_add_next_unavailable_offline), Snackbar.LENGTH_SHORT).show();
                        }
                        return;
                    }
                    List<Child> shuffled = new ArrayList<>(playable);
                    Collections.shuffle(shuffled);
                    MediaManager.startQueue(mediaBrowserListenableFuture, shuffled, 0);
                    activity.setBottomSheetInPeek(true);
                });
            }

            if (bind != null) {
                boolean hasPlayable = OfflineMediaUtil.hasPlayable(requireContext(), songs);
                bind.albumPagePlayButton.setEnabled(hasPlayable);
                bind.albumPageShuffleButton.setEnabled(hasPlayable);
            }
        });
    }

    private void initBackCover() {
        albumPageViewModel.getAlbum().observe(getViewLifecycleOwner(), album -> {
            if (bind != null && album != null) {
                CustomGlideRequest.Builder.from(requireContext(), album.getCoverArtId(), CustomGlideRequest.ResourceType.Album).build().into(bind.albumCoverImageView);
            }
        });
    }

    private void initSongsView() {
        albumPageViewModel.getAlbum().observe(getViewLifecycleOwner(), album -> {
            if (bind != null && album != null) {
                bind.songRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
                bind.songRecyclerView.setHasFixedSize(true);

                songHorizontalAdapter = new SongHorizontalAdapter(this, false, false, album);
                bind.songRecyclerView.setAdapter(songHorizontalAdapter);

                QueueSwipeHelper.attach(bind.songRecyclerView, songHorizontalAdapter, new QueueSwipeHelper.QueueSwipeAction() {
                    @Override
                    public boolean canPerform(one.chandan.rubato.subsonic.models.Child song, QueueSwipeHelper.SwipeAction action) {
                        if (action == QueueSwipeHelper.SwipeAction.TOGGLE_FAVORITE) {
                            return true;
                        }
                        boolean isLocal = LocalMusicRepository.isLocalSong(song);
                        boolean isDownloaded = isLocal || DownloadUtil.getDownloadTracker(requireContext()).isDownloaded(song.getId());
                        return !NetworkUtil.isOffline() || isDownloaded;
                    }

                    @Override
                    public void onSwipeAction(one.chandan.rubato.subsonic.models.Child song, QueueSwipeHelper.SwipeAction action) {
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
                    public void onSwipeRejected(one.chandan.rubato.subsonic.models.Child song, QueueSwipeHelper.SwipeAction action) {
                        if (bind != null) {
                            Snackbar.make(bind.getRoot(), getString(R.string.queue_add_next_unavailable_offline), Snackbar.LENGTH_SHORT).show();
                        }
                    }
                });

                albumPageViewModel.getAlbumSongLiveList().observe(getViewLifecycleOwner(), songs -> songHorizontalAdapter.setItems(songs));
            }
        });
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
