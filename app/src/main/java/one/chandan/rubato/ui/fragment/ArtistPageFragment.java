package one.chandan.rubato.ui.fragment;

import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaBrowser;
import androidx.media3.session.SessionToken;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import one.chandan.rubato.R;
import one.chandan.rubato.databinding.FragmentArtistPageBinding;
import one.chandan.rubato.glide.CustomGlideRequest;
import one.chandan.rubato.helper.recyclerview.CustomLinearSnapHelper;
import one.chandan.rubato.helper.recyclerview.GridItemDecoration;
import one.chandan.rubato.helper.recyclerview.QueueSwipeHelper;
import one.chandan.rubato.interfaces.ClickCallback;
import one.chandan.rubato.service.MediaManager;
import one.chandan.rubato.service.MediaService;
import one.chandan.rubato.subsonic.models.ArtistID3;
import one.chandan.rubato.subsonic.models.Child;
import one.chandan.rubato.ui.activity.MainActivity;
import one.chandan.rubato.ui.adapter.AlbumArtistPageOrSimilarAdapter;
import one.chandan.rubato.ui.adapter.AlbumCatalogueAdapter;
import one.chandan.rubato.ui.adapter.ArtistCatalogueAdapter;
import one.chandan.rubato.ui.adapter.ArtistSimilarAdapter;
import one.chandan.rubato.ui.adapter.SongHorizontalAdapter;
import one.chandan.rubato.util.Constants;
import one.chandan.rubato.util.FavoriteUtil;
import one.chandan.rubato.util.MusicUtil;
import one.chandan.rubato.util.OfflinePolicy;
import one.chandan.rubato.util.Preferences;
import one.chandan.rubato.viewmodel.ArtistPageViewModel;
import com.google.android.material.snackbar.Snackbar;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@UnstableApi
public class ArtistPageFragment extends Fragment implements ClickCallback {
    private FragmentArtistPageBinding bind;
    private MainActivity activity;
    private ArtistPageViewModel artistPageViewModel;

    private SongHorizontalAdapter songHorizontalAdapter;
    private AlbumCatalogueAdapter albumCatalogueAdapter;
    private ArtistCatalogueAdapter artistCatalogueAdapter;

    private ListenableFuture<MediaBrowser> mediaBrowserListenableFuture;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();

        bind = FragmentArtistPageBinding.inflate(inflater, container, false);
        View view = bind.getRoot();
        artistPageViewModel = new ViewModelProvider(requireActivity()).get(ArtistPageViewModel.class);

        init();
        initAppBar();
        initArtistInfo();
        initPlayButtons();
        initTopSongsView();
        initAlbumsView();
        initSimilarArtistsView();

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

    private void init() {
        artistPageViewModel.setArtist(requireArguments().getParcelable(Constants.ARTIST_OBJECT));

        bind.mostStreamedSongTextViewClickable.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putString(Constants.MEDIA_BY_ARTIST, Constants.MEDIA_BY_ARTIST);
            bundle.putParcelable(Constants.ARTIST_OBJECT, artistPageViewModel.getArtist());
            activity.navController.navigate(R.id.action_artistPageFragment_to_songListPageFragment, bundle);
        });
    }

    private void initAppBar() {
        activity.setSupportActionBar(bind.animToolbar);
        if (activity.getSupportActionBar() != null)
            activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        bind.collapsingToolbar.setTitle(artistPageViewModel.getArtist().getName());
        bind.animToolbar.setNavigationOnClickListener(v -> activity.navController.navigateUp());
        bind.collapsingToolbar.setExpandedTitleColor(getResources().getColor(R.color.white, null));
    }

    private void initArtistInfo() {
        artistPageViewModel.getArtistInfo(artistPageViewModel.getArtist().getId()).observe(getViewLifecycleOwner(), artistInfo -> {
            if (artistInfo == null) {
                if (bind != null) bind.artistPageBioSector.setVisibility(View.GONE);
            } else {
                String normalizedBio = MusicUtil.forceReadableString(artistInfo.getBiography());

                if (bind != null)
                    bind.artistPageBioSector.setVisibility(!normalizedBio.trim().isEmpty() ? View.VISIBLE : View.GONE);
                if (bind != null)
                    bind.bioMoreTextViewClickable.setVisibility(artistInfo.getLastFmUrl() != null ? View.VISIBLE : View.GONE);

                if (getContext() != null && bind != null) CustomGlideRequest.Builder
                        .from(requireContext(), artistPageViewModel.getArtist().getId(), CustomGlideRequest.ResourceType.Artist)
                        .build()
                        .into(bind.artistBackdropImageView);

                if (bind != null) bind.bioTextView.setText(normalizedBio);

                if (bind != null) bind.bioMoreTextViewClickable.setOnClickListener(v -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse(artistInfo.getLastFmUrl()));
                    startActivity(intent);
                });

                if (bind != null) bind.artistPageBioSector.setVisibility(View.VISIBLE);
            }
        });
    }

    private void initPlayButtons() {
        boolean isOffline = OfflinePolicy.isOffline();
        bind.artistPageShuffleButton.setOnClickListener(v -> {
            artistPageViewModel.getArtistShuffleList().observe(getViewLifecycleOwner(), songs -> {
                if (songs == null) {
                    Toast.makeText(requireContext(), getString(R.string.artist_error_retrieving_tracks), Toast.LENGTH_SHORT).show();
                    return;
                }
                List<Child> playable = OfflinePolicy.filterPlayable(requireContext(), songs);
                if (!playable.isEmpty()) {
                    MediaManager.startQueue(mediaBrowserListenableFuture, playable, 0);
                    activity.setBottomSheetInPeek(true);
                    return;
                }
                if (isOffline) {
                    Snackbar.make(bind.getRoot(), getString(R.string.queue_add_next_unavailable_offline), Snackbar.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(requireContext(), getString(R.string.artist_error_retrieving_tracks), Toast.LENGTH_SHORT).show();
                }
            });
        });

        bind.artistPageRadioButton.setOnClickListener(v -> {
            artistPageViewModel.getArtistInstantMix().observe(getViewLifecycleOwner(), songs -> {
                if (songs != null && !songs.isEmpty()) {
                    MediaManager.startQueue(mediaBrowserListenableFuture, songs, 0);
                    activity.setBottomSheetInPeek(true);
                    return;
                }
                artistPageViewModel.getArtistShuffleList().observe(getViewLifecycleOwner(), fallback -> {
                    if (fallback == null) {
                        Toast.makeText(requireContext(), getString(R.string.artist_error_retrieving_radio), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    List<Child> playable = OfflinePolicy.filterPlayable(requireContext(), fallback);
                    if (!playable.isEmpty()) {
                        MediaManager.startQueue(mediaBrowserListenableFuture, playable, 0);
                        activity.setBottomSheetInPeek(true);
                    } else {
                        Toast.makeText(requireContext(), getString(R.string.artist_error_retrieving_radio), Toast.LENGTH_SHORT).show();
                    }
                });
            });
        });
        boolean canPlayRadio = OfflinePolicy.canPlayRadio();
        bind.artistPageRadioButton.setEnabled(canPlayRadio);
        bind.artistPageRadioButton.setAlpha(canPlayRadio ? 1f : 0.4f);
    }

    private void initTopSongsView() {
        bind.mostStreamedSongRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        songHorizontalAdapter = new SongHorizontalAdapter(this, true, true, null);
        bind.mostStreamedSongRecyclerView.setAdapter(songHorizontalAdapter);
        artistPageViewModel.getArtistTopSongList().observe(getViewLifecycleOwner(), songs -> {
            if (songs == null) {
                if (bind != null) bind.artistPageTopSongsSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.artistPageTopSongsSector.setVisibility(!songs.isEmpty() ? View.VISIBLE : View.GONE);
                if (bind != null)
                    bind.artistPageShuffleButton.setEnabled(OfflinePolicy.hasPlayable(requireContext(), songs));
                if (bind != null)
                    bind.artistPageShuffleButton.setAlpha(bind.artistPageShuffleButton.isEnabled() ? 1f : 0.4f);
                songHorizontalAdapter.setItems(songs);
            }
        });

        QueueSwipeHelper.attach(bind.mostStreamedSongRecyclerView, songHorizontalAdapter, new QueueSwipeHelper.QueueSwipeAction() {
            @Override
            public boolean canPerform(one.chandan.rubato.subsonic.models.Child song, QueueSwipeHelper.SwipeAction action) {
                if (action == QueueSwipeHelper.SwipeAction.TOGGLE_FAVORITE) {
                    return true;
                }
                return OfflinePolicy.canQueue(requireContext(), song);
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
    }

    private void initAlbumsView() {
        bind.albumsRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        bind.albumsRecyclerView.addItemDecoration(new GridItemDecoration(2, 20, false));
        bind.albumsRecyclerView.setHasFixedSize(true);

        albumCatalogueAdapter = new AlbumCatalogueAdapter(this, false);
        bind.albumsRecyclerView.setAdapter(albumCatalogueAdapter);

        artistPageViewModel.getAlbumList().observe(getViewLifecycleOwner(), albums -> {
            if (albums == null) {
                if (bind != null) bind.artistPageAlbumsSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.artistPageAlbumsSector.setVisibility(!albums.isEmpty() ? View.VISIBLE : View.GONE);
                albumCatalogueAdapter.setItems(albums);
            }
        });
    }

    private void initSimilarArtistsView() {
        bind.similarArtistsRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        bind.similarArtistsRecyclerView.addItemDecoration(new GridItemDecoration(2, 20, false));
        bind.similarArtistsRecyclerView.setHasFixedSize(true);

        artistCatalogueAdapter = new ArtistCatalogueAdapter(this);
        bind.similarArtistsRecyclerView.setAdapter(artistCatalogueAdapter);

        artistPageViewModel.getArtistInfo(artistPageViewModel.getArtist().getId()).observe(getViewLifecycleOwner(), artist -> {
            if (artist == null) {
                if (bind != null) bind.similarArtistSector.setVisibility(View.GONE);
            } else {
                if (bind != null && artist.getSimilarArtists() != null)
                    bind.similarArtistSector.setVisibility(!artist.getSimilarArtists().isEmpty() ? View.VISIBLE : View.GONE);

                List<ArtistID3> artists = new ArrayList<>();

                if (artist.getSimilarArtists() != null) {
                    artists.addAll(artist.getSimilarArtists());
                }

                artistCatalogueAdapter.setItems(artists);
            }
        });

        CustomLinearSnapHelper similarArtistSnapHelper = new CustomLinearSnapHelper();
        similarArtistSnapHelper.attachToRecyclerView(bind.similarArtistsRecyclerView);
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
}
