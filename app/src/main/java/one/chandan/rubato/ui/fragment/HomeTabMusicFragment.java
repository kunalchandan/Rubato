package one.chandan.rubato.ui.fragment;

import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaBrowser;
import androidx.media3.session.SessionToken;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SnapHelper;
import androidx.viewpager2.widget.ViewPager2;

import one.chandan.rubato.R;
import one.chandan.rubato.databinding.FragmentHomeTabMusicBinding;
import one.chandan.rubato.helper.recyclerview.CustomLinearSnapHelper;
import one.chandan.rubato.helper.recyclerview.DotsIndicatorDecoration;
import one.chandan.rubato.interfaces.ClickCallback;
import one.chandan.rubato.interfaces.PlaylistCallback;
import one.chandan.rubato.model.Download;
import one.chandan.rubato.model.HomeSector;
import one.chandan.rubato.service.DownloaderManager;
import one.chandan.rubato.service.MediaManager;
import one.chandan.rubato.service.MediaService;
import one.chandan.rubato.subsonic.models.Child;
import one.chandan.rubato.subsonic.models.Share;
import one.chandan.rubato.ui.activity.MainActivity;
import one.chandan.rubato.ui.adapter.AlbumAdapter;
import one.chandan.rubato.ui.adapter.AlbumHorizontalAdapter;
import one.chandan.rubato.ui.adapter.ArtistAdapter;
import one.chandan.rubato.ui.adapter.ArtistHorizontalAdapter;
import one.chandan.rubato.ui.adapter.DiscoverSongAdapter;
import one.chandan.rubato.ui.adapter.PlaylistHorizontalAdapter;
import one.chandan.rubato.ui.adapter.ShareHorizontalAdapter;
import one.chandan.rubato.ui.adapter.SimilarTrackAdapter;
import one.chandan.rubato.ui.adapter.SongHorizontalAdapter;
import one.chandan.rubato.ui.adapter.YearAdapter;
import one.chandan.rubato.ui.dialog.HomeRearrangementDialog;
import one.chandan.rubato.ui.dialog.PlaylistEditorDialog;
import one.chandan.rubato.util.Constants;
import one.chandan.rubato.util.DownloadUtil;
import one.chandan.rubato.util.MappingUtil;
import one.chandan.rubato.util.MusicUtil;
import one.chandan.rubato.util.NetworkUtil;
import one.chandan.rubato.util.Preferences;
import one.chandan.rubato.util.UIUtil;
import one.chandan.rubato.viewmodel.HomeViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@UnstableApi
public class HomeTabMusicFragment extends Fragment implements ClickCallback {
    private static final String TAG = "HomeFragment";

    private FragmentHomeTabMusicBinding bind;
    private MainActivity activity;
    private HomeViewModel homeViewModel;

    private DiscoverSongAdapter discoverSongAdapter;
    private SimilarTrackAdapter similarMusicAdapter;
    private ArtistAdapter radioArtistAdapter;
    private ArtistAdapter bestOfArtistAdapter;
    private SongHorizontalAdapter starredSongAdapter;
    private SongHorizontalAdapter topSongAdapter;
    private AlbumHorizontalAdapter starredAlbumAdapter;
    private ArtistHorizontalAdapter starredArtistAdapter;
    private AlbumAdapter recentlyAddedAlbumAdapter;
    private AlbumAdapter recentlyPlayedAlbumAdapter;
    private AlbumAdapter mostPlayedAlbumAdapter;
    private AlbumHorizontalAdapter newReleasesAlbumAdapter;
    private YearAdapter yearAdapter;
    private PlaylistHorizontalAdapter playlistHorizontalAdapter;
    private ShareHorizontalAdapter shareHorizontalAdapter;
    private List<Integer> allFlashbackDecades = new ArrayList<>();
    private GridLayoutManager topSongsLayoutManager;
    private GridLayoutManager starredTracksLayoutManager;
    private GridLayoutManager starredAlbumsLayoutManager;
    private GridLayoutManager starredArtistsLayoutManager;
    private GridLayoutManager newReleasesLayoutManager;
    private GridLayoutManager sharesLayoutManager;
    private final RecyclerView.RecycledViewPool songPool = new RecyclerView.RecycledViewPool();
    private final RecyclerView.RecycledViewPool albumPool = new RecyclerView.RecycledViewPool();
    private final RecyclerView.RecycledViewPool artistPool = new RecyclerView.RecycledViewPool();
    private final RecyclerView.RecycledViewPool playlistPool = new RecyclerView.RecycledViewPool();

    private ListenableFuture<MediaBrowser> mediaBrowserListenableFuture;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();

        bind = FragmentHomeTabMusicBinding.inflate(inflater, container, false);
        View view = bind.getRoot();
        homeViewModel = new ViewModelProvider(requireActivity()).get(HomeViewModel.class);

        init();

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initSyncStarredView();
        initDiscoverSongSlideView();
        initSimilarSongView();
        initArtistRadio();
        initArtistBestOf();
        initStarredTracksView();
        initStarredAlbumsView();
        initStarredArtistsView();
        initMostPlayedAlbumView();
        initRecentPlayedAlbumView();
        initNewReleasesView();
        initYearSongView();
        initRecentAddedAlbumView();
        initTopSongsView();
        initPinnedPlaylistsView();
        initSharesView();
        initHomeReorganizer();

        reorder();
    }

    @Override
    public void onStart() {
        super.onStart();

        initializeMediaBrowser();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshSharesView();
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
        bind.discoveryTextViewRefreshable.setOnLongClickListener(v -> {
            if (guardOffline(v)) return true;
            homeViewModel.refreshDiscoverySongSample(getViewLifecycleOwner());
            return true;
        });

        bind.discoveryTextViewClickable.setOnClickListener(v -> {
            if (guardOffline(v)) return;
            homeViewModel.getRandomShuffleSample().observe(getViewLifecycleOwner(), songs -> {
                MusicUtil.ratingFilter(songs);

                if (!songs.isEmpty()) {
                    MediaManager.startQueue(mediaBrowserListenableFuture, songs, 0);
                    activity.setBottomSheetInPeek(true);
                }
            });
        });

        bind.similarTracksTextViewRefreshable.setOnLongClickListener(v -> {
            if (guardOffline(v)) return true;
            homeViewModel.refreshSimilarSongSample(getViewLifecycleOwner());
            return true;
        });

        bind.radioArtistTextViewRefreshable.setOnLongClickListener(v -> {
            if (guardOffline(v)) return true;
            homeViewModel.refreshRadioArtistSample(getViewLifecycleOwner());
            return true;
        });

        bind.bestOfArtistTextViewRefreshable.setOnLongClickListener(v -> {
            if (guardOffline(v)) return true;
            homeViewModel.refreshBestOfArtist(getViewLifecycleOwner());
            return true;
        });

        bind.starredTracksTextViewClickable.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putString(Constants.MEDIA_STARRED, Constants.MEDIA_STARRED);
            activity.navController.navigate(R.id.action_homeFragment_to_songListPageFragment, bundle);
        });

        bind.starredAlbumsTextViewClickable.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putString(Constants.ALBUM_STARRED, Constants.ALBUM_STARRED);
            activity.navController.navigate(R.id.action_homeFragment_to_albumListPageFragment, bundle);
        });

        bind.starredArtistsTextViewClickable.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putString(Constants.ARTIST_STARRED, Constants.ARTIST_STARRED);
            activity.navController.navigate(R.id.action_homeFragment_to_artistListPageFragment, bundle);
        });

        bind.recentlyAddedAlbumsTextViewClickable.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putString(Constants.ALBUM_RECENTLY_ADDED, Constants.ALBUM_RECENTLY_ADDED);
            activity.navController.navigate(R.id.action_homeFragment_to_albumListPageFragment, bundle);
        });

        bind.recentlyPlayedAlbumsTextViewClickable.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putString(Constants.ALBUM_RECENTLY_PLAYED, Constants.ALBUM_RECENTLY_PLAYED);
            activity.navController.navigate(R.id.action_homeFragment_to_albumListPageFragment, bundle);
        });

        bind.mostPlayedAlbumsTextViewClickable.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putString(Constants.ALBUM_MOST_PLAYED, Constants.ALBUM_MOST_PLAYED);
            activity.navController.navigate(R.id.action_homeFragment_to_albumListPageFragment, bundle);
        });

        bind.starredTracksTextViewRefreshable.setOnLongClickListener(v -> {
            if (guardOffline(v)) return true;
            homeViewModel.refreshStarredTracks(getViewLifecycleOwner());
            return true;
        });

        bind.starredAlbumsTextViewRefreshable.setOnLongClickListener(v -> {
            if (guardOffline(v)) return true;
            homeViewModel.refreshStarredAlbums(getViewLifecycleOwner());
            return true;
        });

        bind.starredArtistsTextViewRefreshable.setOnLongClickListener(v -> {
            if (guardOffline(v)) return true;
            homeViewModel.refreshStarredArtists(getViewLifecycleOwner());
            return true;
        });

        bind.recentlyPlayedAlbumsTextViewRefreshable.setOnLongClickListener(v -> {
            if (guardOffline(v)) return true;
            homeViewModel.refreshRecentlyPlayedAlbumList(getViewLifecycleOwner());
            return true;
        });

        bind.mostPlayedAlbumsTextViewRefreshable.setOnLongClickListener(v -> {
            if (guardOffline(v)) return true;
            homeViewModel.refreshMostPlayedAlbums(getViewLifecycleOwner());
            return true;
        });

        bind.recentlyAddedAlbumsTextViewRefreshable.setOnLongClickListener(v -> {
            if (guardOffline(v)) return true;
            homeViewModel.refreshMostRecentlyAddedAlbums(getViewLifecycleOwner());
            return true;
        });

        bind.sharesTextViewRefreshable.setOnLongClickListener(v -> {
            if (guardOffline(v)) return true;
            homeViewModel.refreshShares(getViewLifecycleOwner());
            return true;
        });

        bind.gridTracksPreTextView.setOnClickListener(view -> showPopupMenu(view, R.menu.filter_top_songs_popup_menu));
        bind.flashbackSettingsButton.setOnClickListener(view -> showFlashbackDecadePicker());
    }

    private void initSyncStarredView() {
        if (Preferences.isStarredSyncEnabled()) {
            homeViewModel.getAllStarredTracks().observeForever(new Observer<List<Child>>() {
                @Override
                public void onChanged(List<Child> songs) {
                    if (songs != null) {
                        DownloaderManager manager = DownloadUtil.getDownloadTracker(requireContext());
                        List<String> toSync = new ArrayList<>();

                        for (Child song : songs) {
                            if (!manager.isDownloaded(song.getId())) {
                                toSync.add(song.getTitle());
                            }
                        }

                        if (!toSync.isEmpty()) {
                            bind.homeSyncStarredCard.setVisibility(View.VISIBLE);
                            bind.homeSyncStarredTracksToSync.setText(String.join(", ", toSync));
                        }
                    }

                    homeViewModel.getAllStarredTracks().removeObserver(this);
                }
            });
        }

        bind.homeSyncStarredCancel.setOnClickListener(v -> bind.homeSyncStarredCard.setVisibility(View.GONE));

        bind.homeSyncStarredDownload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (guardOffline(v)) return;
                homeViewModel.getAllStarredTracks().observeForever(new Observer<List<Child>>() {
                    @Override
                    public void onChanged(List<Child> songs) {
                        if (songs != null) {
                            DownloaderManager manager = DownloadUtil.getDownloadTracker(requireContext());

                            for (Child song : songs) {
                                if (!manager.isDownloaded(song.getId())) {
                                    manager.download(MappingUtil.mapDownload(song), new Download(song));
                                }
                            }
                        }

                        homeViewModel.getAllStarredTracks().removeObserver(this);
                        bind.homeSyncStarredCard.setVisibility(View.GONE);
                    }
                });
            }
        });
    }

    private void initDiscoverSongSlideView() {
        if (homeViewModel.checkHomeSectorVisibility(Constants.HOME_SECTOR_DISCOVERY)) return;

        bind.discoverSongViewPager.setOrientation(ViewPager2.ORIENTATION_HORIZONTAL);

        discoverSongAdapter = new DiscoverSongAdapter(this);
        bind.discoverSongViewPager.setAdapter(discoverSongAdapter);
        bind.discoverSongViewPager.setOffscreenPageLimit(1);
        homeViewModel.getDiscoverSongSample(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), songs -> {
            MusicUtil.ratingFilter(songs);

            if (songs == null) {
                if (bind != null) bind.homeDiscoverSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.homeDiscoverSector.setVisibility(!songs.isEmpty() ? View.VISIBLE : View.GONE);

                discoverSongAdapter.setItems(songs);
            }
        });

        setSlideViewOffset(bind.discoverSongViewPager, 20, 16);
    }

    private void initSimilarSongView() {
        if (homeViewModel.checkHomeSectorVisibility(Constants.HOME_SECTOR_MADE_FOR_YOU)) return;

        bind.similarTracksRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        bind.similarTracksRecyclerView.setHasFixedSize(true);
        bind.similarTracksRecyclerView.setRecycledViewPool(songPool);
        setupHomeRecyclerView(bind.similarTracksRecyclerView, 8);

        similarMusicAdapter = new SimilarTrackAdapter(this);
        bind.similarTracksRecyclerView.setAdapter(similarMusicAdapter);
        homeViewModel.getStarredTracksSample(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), songs -> {
            MusicUtil.ratingFilter(songs);

            if (songs == null) {
                if (bind != null) bind.homeSimilarTracksSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.homeSimilarTracksSector.setVisibility(!songs.isEmpty() ? View.VISIBLE : View.GONE);

                similarMusicAdapter.setItems(songs);
            }
        });

        CustomLinearSnapHelper similarSongSnapHelper = new CustomLinearSnapHelper();
        similarSongSnapHelper.attachToRecyclerView(bind.similarTracksRecyclerView);
    }

    private void initArtistBestOf() {
        if (homeViewModel.checkHomeSectorVisibility(Constants.HOME_SECTOR_BEST_OF)) return;

        bind.bestOfArtistRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        bind.bestOfArtistRecyclerView.setHasFixedSize(true);
        bind.bestOfArtistRecyclerView.setRecycledViewPool(artistPool);
        setupHomeRecyclerView(bind.bestOfArtistRecyclerView, 8);

        bestOfArtistAdapter = new ArtistAdapter(this, false, true);
        bind.bestOfArtistRecyclerView.setAdapter(bestOfArtistAdapter);
        homeViewModel.getBestOfArtists(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), artists -> {
            if (artists == null) {
                if (bind != null) bind.homeBestOfArtistSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.homeBestOfArtistSector.setVisibility(!artists.isEmpty() ? View.VISIBLE : View.GONE);

                bestOfArtistAdapter.setItems(artists);
            }
        });

        CustomLinearSnapHelper artistBestOfSnapHelper = new CustomLinearSnapHelper();
        artistBestOfSnapHelper.attachToRecyclerView(bind.bestOfArtistRecyclerView);
    }

    private void initArtistRadio() {
        if (homeViewModel.checkHomeSectorVisibility(Constants.HOME_SECTOR_RADIO_STATION)) return;

        bind.radioArtistRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        bind.radioArtistRecyclerView.setHasFixedSize(true);
        bind.radioArtistRecyclerView.setRecycledViewPool(artistPool);
        setupHomeRecyclerView(bind.radioArtistRecyclerView, 8);

        radioArtistAdapter = new ArtistAdapter(this, true, false);
        bind.radioArtistRecyclerView.setAdapter(radioArtistAdapter);
        homeViewModel.getStarredArtistsSample(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), artists -> {
            if (artists == null) {
                if (bind != null) bind.homeRadioArtistSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.homeRadioArtistSector.setVisibility(!artists.isEmpty() ? View.VISIBLE : View.GONE);
                if (bind != null)
                    bind.afterRadioArtistDivider.setVisibility(!artists.isEmpty() ? View.VISIBLE : View.GONE);

                radioArtistAdapter.setItems(artists);
            }
        });

        CustomLinearSnapHelper artistRadioSnapHelper = new CustomLinearSnapHelper();
        artistRadioSnapHelper.attachToRecyclerView(bind.radioArtistRecyclerView);
    }

    private void initTopSongsView() {
        if (homeViewModel.checkHomeSectorVisibility(Constants.HOME_SECTOR_TOP_SONGS)) return;

        bind.topSongsRecyclerView.setHasFixedSize(true);
        bind.topSongsRecyclerView.setRecycledViewPool(songPool);
        topSongsLayoutManager = new GridLayoutManager(requireContext(), 1, GridLayoutManager.HORIZONTAL, false);
        bind.topSongsRecyclerView.setLayoutManager(topSongsLayoutManager);
        setupHomeRecyclerView(bind.topSongsRecyclerView, 6);

        topSongAdapter = new SongHorizontalAdapter(this, true, false, null);
        bind.topSongsRecyclerView.setAdapter(topSongAdapter);
        homeViewModel.getChronologySample(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), chronologies -> {
            if (chronologies == null || chronologies.isEmpty()) {
                if (bind != null) bind.homeGridTracksSector.setVisibility(View.GONE);
                if (bind != null) bind.afterGridDivider.setVisibility(View.GONE);
            } else {
                if (bind != null) bind.homeGridTracksSector.setVisibility(View.VISIBLE);
                if (bind != null) bind.afterGridDivider.setVisibility(View.VISIBLE);
                if (bind != null && topSongsLayoutManager != null) {
                    int spanCount = UIUtil.getSpanCount(chronologies.size(), 5);
                    if (topSongsLayoutManager.getSpanCount() != spanCount) {
                        topSongsLayoutManager.setSpanCount(spanCount);
                    }
                }

                List<Child> topSongs = chronologies.stream()
                        .map(cronologia -> (Child) cronologia)
                        .collect(Collectors.toList());

                topSongAdapter.setItems(topSongs);
            }
        });

        SnapHelper topTrackSnapHelper = new PagerSnapHelper();
        topTrackSnapHelper.attachToRecyclerView(bind.topSongsRecyclerView);

        bind.topSongsRecyclerView.addItemDecoration(
                new DotsIndicatorDecoration(
                        getResources().getDimensionPixelSize(R.dimen.radius),
                        getResources().getDimensionPixelSize(R.dimen.radius) * 4,
                        getResources().getDimensionPixelSize(R.dimen.dots_height),
                        requireContext().getResources().getColor(R.color.titleTextColor, null),
                        requireContext().getResources().getColor(R.color.titleTextColor, null))
        );
    }

    private void initStarredTracksView() {
        if (homeViewModel.checkHomeSectorVisibility(Constants.HOME_SECTOR_STARRED_TRACKS)) return;

        bind.starredTracksRecyclerView.setHasFixedSize(true);
        bind.starredTracksRecyclerView.setRecycledViewPool(songPool);
        starredTracksLayoutManager = new GridLayoutManager(requireContext(), 1, GridLayoutManager.HORIZONTAL, false);
        bind.starredTracksRecyclerView.setLayoutManager(starredTracksLayoutManager);
        setupHomeRecyclerView(bind.starredTracksRecyclerView, 6);

        starredSongAdapter = new SongHorizontalAdapter(this, true, false, null);
        bind.starredTracksRecyclerView.setAdapter(starredSongAdapter);
        homeViewModel.getStarredTracks(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), songs -> {
            if (songs == null) {
                if (bind != null) bind.starredTracksSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.starredTracksSector.setVisibility(!songs.isEmpty() ? View.VISIBLE : View.GONE);
                if (bind != null && starredTracksLayoutManager != null) {
                    int spanCount = UIUtil.getSpanCount(songs.size(), 5);
                    if (starredTracksLayoutManager.getSpanCount() != spanCount) {
                        starredTracksLayoutManager.setSpanCount(spanCount);
                    }
                }

                starredSongAdapter.setItems(songs);
            }
        });

        SnapHelper starredTrackSnapHelper = new PagerSnapHelper();
        starredTrackSnapHelper.attachToRecyclerView(bind.starredTracksRecyclerView);

        bind.starredTracksRecyclerView.addItemDecoration(
                new DotsIndicatorDecoration(
                        getResources().getDimensionPixelSize(R.dimen.radius),
                        getResources().getDimensionPixelSize(R.dimen.radius) * 4,
                        getResources().getDimensionPixelSize(R.dimen.dots_height),
                        requireContext().getResources().getColor(R.color.titleTextColor, null),
                        requireContext().getResources().getColor(R.color.titleTextColor, null))
        );
    }

    private void initStarredAlbumsView() {
        if (homeViewModel.checkHomeSectorVisibility(Constants.HOME_SECTOR_STARRED_ALBUMS)) return;

        bind.starredAlbumsRecyclerView.setHasFixedSize(true);
        bind.starredAlbumsRecyclerView.setRecycledViewPool(albumPool);
        starredAlbumsLayoutManager = new GridLayoutManager(requireContext(), 1, GridLayoutManager.HORIZONTAL, false);
        bind.starredAlbumsRecyclerView.setLayoutManager(starredAlbumsLayoutManager);
        setupHomeRecyclerView(bind.starredAlbumsRecyclerView, 6);

        starredAlbumAdapter = new AlbumHorizontalAdapter(this, false);
        bind.starredAlbumsRecyclerView.setAdapter(starredAlbumAdapter);
        homeViewModel.getStarredAlbums(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), albums -> {
            if (albums == null) {
                if (bind != null) bind.starredAlbumsSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.starredAlbumsSector.setVisibility(!albums.isEmpty() ? View.VISIBLE : View.GONE);
                if (bind != null && starredAlbumsLayoutManager != null) {
                    int spanCount = UIUtil.getSpanCount(albums.size(), 5);
                    if (starredAlbumsLayoutManager.getSpanCount() != spanCount) {
                        starredAlbumsLayoutManager.setSpanCount(spanCount);
                    }
                }

                starredAlbumAdapter.setItems(albums);
            }
        });

        SnapHelper starredAlbumSnapHelper = new PagerSnapHelper();
        starredAlbumSnapHelper.attachToRecyclerView(bind.starredAlbumsRecyclerView);

        bind.starredAlbumsRecyclerView.addItemDecoration(
                new DotsIndicatorDecoration(
                        getResources().getDimensionPixelSize(R.dimen.radius),
                        getResources().getDimensionPixelSize(R.dimen.radius) * 4,
                        getResources().getDimensionPixelSize(R.dimen.dots_height),
                        requireContext().getResources().getColor(R.color.titleTextColor, null),
                        requireContext().getResources().getColor(R.color.titleTextColor, null))
        );
    }

    private void initStarredArtistsView() {
        if (homeViewModel.checkHomeSectorVisibility(Constants.HOME_SECTOR_STARRED_ARTISTS)) return;

        bind.starredArtistsRecyclerView.setHasFixedSize(true);
        bind.starredArtistsRecyclerView.setRecycledViewPool(artistPool);
        starredArtistsLayoutManager = new GridLayoutManager(requireContext(), 1, GridLayoutManager.HORIZONTAL, false);
        bind.starredArtistsRecyclerView.setLayoutManager(starredArtistsLayoutManager);
        setupHomeRecyclerView(bind.starredArtistsRecyclerView, 6);

        starredArtistAdapter = new ArtistHorizontalAdapter(this);
        bind.starredArtistsRecyclerView.setAdapter(starredArtistAdapter);
        homeViewModel.getStarredArtists(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), artists -> {
            if (artists == null) {
                if (bind != null) bind.starredArtistsSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.starredArtistsSector.setVisibility(!artists.isEmpty() ? View.VISIBLE : View.GONE);
                if (bind != null)
                    bind.afterFavoritesDivider.setVisibility(!artists.isEmpty() ? View.VISIBLE : View.GONE);
                if (bind != null && starredArtistsLayoutManager != null) {
                    int spanCount = UIUtil.getSpanCount(artists.size(), 5);
                    if (starredArtistsLayoutManager.getSpanCount() != spanCount) {
                        starredArtistsLayoutManager.setSpanCount(spanCount);
                    }
                }

                starredArtistAdapter.setItems(artists);
            }
        });

        SnapHelper starredArtistSnapHelper = new PagerSnapHelper();
        starredArtistSnapHelper.attachToRecyclerView(bind.starredArtistsRecyclerView);

        bind.starredArtistsRecyclerView.addItemDecoration(
                new DotsIndicatorDecoration(
                        getResources().getDimensionPixelSize(R.dimen.radius),
                        getResources().getDimensionPixelSize(R.dimen.radius) * 4,
                        getResources().getDimensionPixelSize(R.dimen.dots_height),
                        requireContext().getResources().getColor(R.color.titleTextColor, null),
                        requireContext().getResources().getColor(R.color.titleTextColor, null))
        );
    }

    private void initNewReleasesView() {
        if (homeViewModel.checkHomeSectorVisibility(Constants.HOME_SECTOR_NEW_RELEASES)) return;

        bind.newReleasesRecyclerView.setHasFixedSize(true);
        bind.newReleasesRecyclerView.setRecycledViewPool(albumPool);
        newReleasesLayoutManager = new GridLayoutManager(requireContext(), 1, GridLayoutManager.HORIZONTAL, false);
        bind.newReleasesRecyclerView.setLayoutManager(newReleasesLayoutManager);
        setupHomeRecyclerView(bind.newReleasesRecyclerView, 6);

        newReleasesAlbumAdapter = new AlbumHorizontalAdapter(this, false);
        bind.newReleasesRecyclerView.setAdapter(newReleasesAlbumAdapter);
        homeViewModel.getRecentlyReleasedAlbums(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), albums -> {
            if (albums == null) {
                if (bind != null) bind.homeNewReleasesSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.homeNewReleasesSector.setVisibility(!albums.isEmpty() ? View.VISIBLE : View.GONE);
                if (bind != null && newReleasesLayoutManager != null) {
                    int spanCount = UIUtil.getSpanCount(albums.size(), 5);
                    if (newReleasesLayoutManager.getSpanCount() != spanCount) {
                        newReleasesLayoutManager.setSpanCount(spanCount);
                    }
                }

                newReleasesAlbumAdapter.setItems(albums);
            }
        });

        SnapHelper newReleasesSnapHelper = new PagerSnapHelper();
        newReleasesSnapHelper.attachToRecyclerView(bind.newReleasesRecyclerView);

        bind.newReleasesRecyclerView.addItemDecoration(
                new DotsIndicatorDecoration(
                        getResources().getDimensionPixelSize(R.dimen.radius),
                        getResources().getDimensionPixelSize(R.dimen.radius) * 4,
                        getResources().getDimensionPixelSize(R.dimen.dots_height),
                        requireContext().getResources().getColor(R.color.titleTextColor, null),
                        requireContext().getResources().getColor(R.color.titleTextColor, null))
        );
    }

    private void initYearSongView() {
        if (homeViewModel.checkHomeSectorVisibility(Constants.HOME_SECTOR_FLASHBACK)) return;

        bind.yearsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        bind.yearsRecyclerView.setHasFixedSize(true);
        bind.yearsRecyclerView.setRecycledViewPool(songPool);
        setupHomeRecyclerView(bind.yearsRecyclerView, 6);

        yearAdapter = new YearAdapter(this);
        bind.yearsRecyclerView.setAdapter(yearAdapter);
        homeViewModel.getYearList(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), years -> {
            if (years == null) {
                if (bind != null) bind.homeFlashbackSector.setVisibility(View.GONE);
            } else {
                allFlashbackDecades = new ArrayList<>(years);
                applyFlashbackSelection();
            }
        });

        CustomLinearSnapHelper yearSnapHelper = new CustomLinearSnapHelper();
        yearSnapHelper.attachToRecyclerView(bind.yearsRecyclerView);
    }

    private void applyFlashbackSelection() {
        if (bind == null) return;

        List<Integer> selected = Preferences.getFlashbackDecades();
        List<Integer> filtered;

        if (selected == null || selected.isEmpty()) {
            filtered = allFlashbackDecades;
        } else {
            filtered = allFlashbackDecades.stream()
                    .filter(selected::contains)
                    .collect(Collectors.toList());
        }

        bind.homeFlashbackSector.setVisibility(!filtered.isEmpty() ? View.VISIBLE : View.GONE);
        yearAdapter.setItems(filtered);
    }

    private void showFlashbackDecadePicker() {
        if (bind == null || allFlashbackDecades == null || allFlashbackDecades.isEmpty()) {
            Snackbar.make(requireView(), R.string.home_flashback_decades_empty, Snackbar.LENGTH_SHORT)
                    .setAnchorView(activity.bind.playerBottomSheet)
                    .show();
            return;
        }

        List<Integer> sorted = new ArrayList<>(allFlashbackDecades);
        Collections.sort(sorted);
        Collections.reverse(sorted);

        String[] labels = new String[sorted.size()];
        boolean[] checked = new boolean[sorted.size()];

        List<Integer> selected = Preferences.getFlashbackDecades();
        boolean useAll = selected == null || selected.isEmpty();

        for (int i = 0; i < sorted.size(); i++) {
            int decade = sorted.get(i);
            labels[i] = decade + "s";
            checked[i] = useAll || selected.contains(decade);
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.home_flashback_decades_title)
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton(R.string.action_apply, (dialog, which) -> {
                    List<Integer> picked = new ArrayList<>();
                    for (int i = 0; i < checked.length; i++) {
                        if (checked[i]) {
                            picked.add(sorted.get(i));
                        }
                    }

                    if (picked.isEmpty() || picked.size() == sorted.size()) {
                        Preferences.setFlashbackDecades(null);
                    } else {
                        Preferences.setFlashbackDecades(picked);
                    }

                    applyFlashbackSelection();
                })
                .setNeutralButton(R.string.action_reset, (dialog, which) -> {
                    Preferences.setFlashbackDecades(null);
                    applyFlashbackSelection();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void initMostPlayedAlbumView() {
        if (homeViewModel.checkHomeSectorVisibility(Constants.HOME_SECTOR_MOST_PLAYED)) return;

        bind.mostPlayedAlbumsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        bind.mostPlayedAlbumsRecyclerView.setHasFixedSize(true);
        bind.mostPlayedAlbumsRecyclerView.setRecycledViewPool(albumPool);
        setupHomeRecyclerView(bind.mostPlayedAlbumsRecyclerView, 8);

        mostPlayedAlbumAdapter = new AlbumAdapter(this);
        bind.mostPlayedAlbumsRecyclerView.setAdapter(mostPlayedAlbumAdapter);
        homeViewModel.getMostPlayedAlbums(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), albums -> {
            if (albums == null) {
                if (bind != null) bind.homeMostPlayedAlbumsSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.homeMostPlayedAlbumsSector.setVisibility(!albums.isEmpty() ? View.VISIBLE : View.GONE);

                mostPlayedAlbumAdapter.setItems(albums);
            }
        });

        CustomLinearSnapHelper mostPlayedAlbumSnapHelper = new CustomLinearSnapHelper();
        mostPlayedAlbumSnapHelper.attachToRecyclerView(bind.mostPlayedAlbumsRecyclerView);
    }

    private void initRecentPlayedAlbumView() {
        if (homeViewModel.checkHomeSectorVisibility(Constants.HOME_SECTOR_LAST_PLAYED)) return;

        bind.recentlyPlayedAlbumsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        bind.recentlyPlayedAlbumsRecyclerView.setHasFixedSize(true);
        bind.recentlyPlayedAlbumsRecyclerView.setRecycledViewPool(albumPool);
        setupHomeRecyclerView(bind.recentlyPlayedAlbumsRecyclerView, 8);

        recentlyPlayedAlbumAdapter = new AlbumAdapter(this);
        bind.recentlyPlayedAlbumsRecyclerView.setAdapter(recentlyPlayedAlbumAdapter);
        homeViewModel.getRecentlyPlayedAlbumList(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), albums -> {
            if (albums == null) {
                if (bind != null) bind.homeRecentlyPlayedAlbumsSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.homeRecentlyPlayedAlbumsSector.setVisibility(!albums.isEmpty() ? View.VISIBLE : View.GONE);

                recentlyPlayedAlbumAdapter.setItems(albums);
            }
        });

        CustomLinearSnapHelper recentPlayedAlbumSnapHelper = new CustomLinearSnapHelper();
        recentPlayedAlbumSnapHelper.attachToRecyclerView(bind.recentlyPlayedAlbumsRecyclerView);
    }

    private void initRecentAddedAlbumView() {
        if (homeViewModel.checkHomeSectorVisibility(Constants.HOME_SECTOR_RECENTLY_ADDED)) return;

        bind.recentlyAddedAlbumsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        bind.recentlyAddedAlbumsRecyclerView.setHasFixedSize(true);
        bind.recentlyAddedAlbumsRecyclerView.setRecycledViewPool(albumPool);
        setupHomeRecyclerView(bind.recentlyAddedAlbumsRecyclerView, 8);

        recentlyAddedAlbumAdapter = new AlbumAdapter(this);
        bind.recentlyAddedAlbumsRecyclerView.setAdapter(recentlyAddedAlbumAdapter);
        homeViewModel.getMostRecentlyAddedAlbums(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), albums -> {
            if (albums == null) {
                if (bind != null) bind.homeRecentlyAddedAlbumsSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.homeRecentlyAddedAlbumsSector.setVisibility(!albums.isEmpty() ? View.VISIBLE : View.GONE);

                recentlyAddedAlbumAdapter.setItems(albums);
            }
        });

        CustomLinearSnapHelper recentAddedAlbumSnapHelper = new CustomLinearSnapHelper();
        recentAddedAlbumSnapHelper.attachToRecyclerView(bind.recentlyAddedAlbumsRecyclerView);
    }

    private void initPinnedPlaylistsView() {
        if (homeViewModel.checkHomeSectorVisibility(Constants.HOME_SECTOR_PINNED_PLAYLISTS)) return;

        bind.pinnedPlaylistsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        bind.pinnedPlaylistsRecyclerView.setHasFixedSize(true);
        bind.pinnedPlaylistsRecyclerView.setRecycledViewPool(playlistPool);
        setupHomeRecyclerView(bind.pinnedPlaylistsRecyclerView, 6);

        playlistHorizontalAdapter = new PlaylistHorizontalAdapter(this);
        bind.pinnedPlaylistsRecyclerView.setAdapter(playlistHorizontalAdapter);
        homeViewModel.getPinnedPlaylists(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), playlists -> {
            if (playlists == null) {
                if (bind != null) bind.pinnedPlaylistsSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.pinnedPlaylistsSector.setVisibility(!playlists.isEmpty() ? View.VISIBLE : View.GONE);

                playlistHorizontalAdapter.setItems(playlists);
            }
        });
    }

    private void initSharesView() {
        if (homeViewModel.checkHomeSectorVisibility(Constants.HOME_SECTOR_SHARED)) return;

        bind.sharesRecyclerView.setHasFixedSize(true);
        bind.sharesRecyclerView.setRecycledViewPool(albumPool);
        sharesLayoutManager = new GridLayoutManager(requireContext(), 1, GridLayoutManager.HORIZONTAL, false);
        bind.sharesRecyclerView.setLayoutManager(sharesLayoutManager);
        setupHomeRecyclerView(bind.sharesRecyclerView, 6);

        shareHorizontalAdapter = new ShareHorizontalAdapter(this);
        bind.sharesRecyclerView.setAdapter(shareHorizontalAdapter);
        if (Preferences.isSharingEnabled()) {
            homeViewModel.getShares(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), shares -> {
                if (shares == null) {
                    if (bind != null) bind.sharesSector.setVisibility(View.GONE);
                } else {
                    if (bind != null)
                        bind.sharesSector.setVisibility(!shares.isEmpty() ? View.VISIBLE : View.GONE);
                    if (bind != null && sharesLayoutManager != null) {
                        int spanCount = UIUtil.getSpanCount(shares.size(), 10);
                        if (sharesLayoutManager.getSpanCount() != spanCount) {
                            sharesLayoutManager.setSpanCount(spanCount);
                        }
                    }

                    shareHorizontalAdapter.setItems(shares);
                }
            });
        }

        SnapHelper starredTrackSnapHelper = new PagerSnapHelper();
        starredTrackSnapHelper.attachToRecyclerView(bind.sharesRecyclerView);

        bind.sharesRecyclerView.addItemDecoration(
                new DotsIndicatorDecoration(
                        getResources().getDimensionPixelSize(R.dimen.radius),
                        getResources().getDimensionPixelSize(R.dimen.radius) * 4,
                        getResources().getDimensionPixelSize(R.dimen.dots_height),
                        requireContext().getResources().getColor(R.color.titleTextColor, null),
                        requireContext().getResources().getColor(R.color.titleTextColor, null))
        );
    }

    private void initHomeReorganizer() {
        if (bind != null) {
            bind.homeSectorRearrangementButton.setVisibility(View.VISIBLE);
        }

        bind.homeSectorRearrangementButton.setOnClickListener(v -> {
            HomeRearrangementDialog dialog = new HomeRearrangementDialog();
            dialog.show(requireActivity().getSupportFragmentManager(), null);
        });
    }

    private void refreshSharesView() {
        final Handler handler = new Handler();
        final Runnable runnable = () -> {
            if (getView() != null && bind != null && Preferences.isSharingEnabled()) {
                homeViewModel.refreshShares(getViewLifecycleOwner());
            }
        };
        handler.postDelayed(runnable, 100);
    }

    private void setSlideViewOffset(ViewPager2 viewPager, float pageOffset, float pageMargin) {
        viewPager.setPageTransformer((page, position) -> {
            float myOffset = position * -(2 * pageOffset + pageMargin);
            if (viewPager.getOrientation() == ViewPager2.ORIENTATION_HORIZONTAL) {
                if (ViewCompat.getLayoutDirection(viewPager) == ViewCompat.LAYOUT_DIRECTION_RTL) {
                    page.setTranslationX(-myOffset);
                } else {
                    page.setTranslationX(myOffset);
                }
            } else {
                page.setTranslationY(myOffset);
            }
        });
    }

    private void setupHomeRecyclerView(RecyclerView recyclerView, int cacheSize) {
        recyclerView.setNestedScrollingEnabled(false);
        recyclerView.setItemAnimator(null);
        recyclerView.setItemViewCacheSize(cacheSize);
    }

    public void reorder() {
        if (bind != null && homeViewModel.getHomeSectorList() != null) {
            bind.homeLinearLayoutContainer.removeAllViews();

            for (HomeSector sector : homeViewModel.getHomeSectorList()) {
                if (!sector.isVisible()) continue;

                switch (sector.getId()) {
                    case Constants.HOME_SECTOR_DISCOVERY:
                        bind.homeLinearLayoutContainer.addView(bind.homeDiscoverSector);
                        break;
                    case Constants.HOME_SECTOR_MADE_FOR_YOU:
                        bind.homeLinearLayoutContainer.addView(bind.homeSimilarTracksSector);
                        break;
                    case Constants.HOME_SECTOR_BEST_OF:
                        bind.homeLinearLayoutContainer.addView(bind.homeBestOfArtistSector);
                        break;
                    case Constants.HOME_SECTOR_RADIO_STATION:
                        bind.homeLinearLayoutContainer.addView(bind.homeRadioArtistSector);
                        break;
                    case Constants.HOME_SECTOR_TOP_SONGS:
                        bind.homeLinearLayoutContainer.addView(bind.homeGridTracksSector);
                        break;
                    case Constants.HOME_SECTOR_STARRED_TRACKS:
                        bind.homeLinearLayoutContainer.addView(bind.starredTracksSector);
                        break;
                    case Constants.HOME_SECTOR_STARRED_ALBUMS:
                        bind.homeLinearLayoutContainer.addView(bind.starredAlbumsSector);
                        break;
                    case Constants.HOME_SECTOR_STARRED_ARTISTS:
                        bind.homeLinearLayoutContainer.addView(bind.starredArtistsSector);
                        break;
                    case Constants.HOME_SECTOR_NEW_RELEASES:
                        bind.homeLinearLayoutContainer.addView(bind.homeNewReleasesSector);
                        break;
                    case Constants.HOME_SECTOR_FLASHBACK:
                        bind.homeLinearLayoutContainer.addView(bind.homeFlashbackSector);
                        break;
                    case Constants.HOME_SECTOR_MOST_PLAYED:
                        bind.homeLinearLayoutContainer.addView(bind.homeMostPlayedAlbumsSector);
                        break;
                    case Constants.HOME_SECTOR_LAST_PLAYED:
                        bind.homeLinearLayoutContainer.addView(bind.homeRecentlyPlayedAlbumsSector);
                        break;
                    case Constants.HOME_SECTOR_RECENTLY_ADDED:
                        bind.homeLinearLayoutContainer.addView(bind.homeRecentlyAddedAlbumsSector);
                        break;
                    case Constants.HOME_SECTOR_PINNED_PLAYLISTS:
                        bind.homeLinearLayoutContainer.addView(bind.pinnedPlaylistsSector);
                        break;
                    case Constants.HOME_SECTOR_SHARED:
                        bind.homeLinearLayoutContainer.addView(bind.sharesSector);
                        break;
                }
            }

            bind.homeLinearLayoutContainer.addView(bind.homeSectorRearrangementButton);
        }
    }

    private void showPopupMenu(View view, int menuResource) {
        PopupMenu popup = new PopupMenu(requireContext(), view);
        popup.getMenuInflater().inflate(menuResource, popup.getMenu());

        popup.setOnMenuItemClickListener(menuItem -> {
            if (menuItem.getItemId() == R.id.menu_last_week_name) {
                if (guardOffline(view)) return true;
                homeViewModel.changeChronologyPeriod(getViewLifecycleOwner(), 0);
                bind.gridTracksPreTextView.setText(getString(R.string.home_title_last_week));
                return true;
            } else if (menuItem.getItemId() == R.id.menu_last_month_name) {
                if (guardOffline(view)) return true;
                homeViewModel.changeChronologyPeriod(getViewLifecycleOwner(), 1);
                bind.gridTracksPreTextView.setText(getString(R.string.home_title_last_month));
                return true;
            } else if (menuItem.getItemId() == R.id.menu_last_year_name) {
                if (guardOffline(view)) return true;
                homeViewModel.changeChronologyPeriod(getViewLifecycleOwner(), 2);
                bind.gridTracksPreTextView.setText(getString(R.string.home_title_last_year));
                return true;
            }

            return false;
        });

        popup.show();
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

    private void refreshPlaylistView() {
        final Handler handler = new Handler();

        final Runnable runnable = () -> {
            if (getView() != null && bind != null && homeViewModel != null)
                homeViewModel.getPinnedPlaylists(getViewLifecycleOwner());
        };

        handler.postDelayed(runnable, 100);
    }

    private void initializeMediaBrowser() {
        mediaBrowserListenableFuture = new MediaBrowser.Builder(requireContext(), new SessionToken(requireContext(), new ComponentName(requireContext(), MediaService.class))).buildAsync();
    }

    private void releaseMediaBrowser() {
        MediaBrowser.releaseFuture(mediaBrowserListenableFuture);
    }

    @Override
    public void onMediaClick(Bundle bundle) {
        if (bundle.containsKey(Constants.MEDIA_MIX)) {
            MediaManager.startQueue(mediaBrowserListenableFuture, bundle.getParcelable(Constants.TRACK_OBJECT));
            activity.setBottomSheetInPeek(true);

            if (mediaBrowserListenableFuture != null) {
                homeViewModel.getMediaInstantMix(getViewLifecycleOwner(), bundle.getParcelable(Constants.TRACK_OBJECT)).observe(getViewLifecycleOwner(), songs -> {
                    MusicUtil.ratingFilter(songs);

                    if (songs != null && !songs.isEmpty()) {
                        MediaManager.enqueue(mediaBrowserListenableFuture, songs, true);
                    }
                });
            }
        } else if (bundle.containsKey(Constants.MEDIA_CHRONOLOGY)) {
            List<Child> media = bundle.getParcelableArrayList(Constants.TRACKS_OBJECT);
            MediaManager.startQueue(mediaBrowserListenableFuture, media, bundle.getInt(Constants.ITEM_POSITION));
            activity.setBottomSheetInPeek(true);
        } else {
            MediaManager.startQueue(mediaBrowserListenableFuture, bundle.getParcelableArrayList(Constants.TRACKS_OBJECT), bundle.getInt(Constants.ITEM_POSITION));
            activity.setBottomSheetInPeek(true);
        }
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
        if (bundle.containsKey(Constants.MEDIA_MIX) && bundle.getBoolean(Constants.MEDIA_MIX)) {
            Snackbar.make(requireView(), R.string.artist_adapter_radio_station_starting, Snackbar.LENGTH_LONG)
                    .setAnchorView(activity.bind.playerBottomSheet)
                    .show();

            if (mediaBrowserListenableFuture != null) {
                homeViewModel.getArtistInstantMix(getViewLifecycleOwner(), bundle.getParcelable(Constants.ARTIST_OBJECT)).observe(getViewLifecycleOwner(), songs -> {
                    MusicUtil.ratingFilter(songs);

                    if (!songs.isEmpty()) {
                        MediaManager.startQueue(mediaBrowserListenableFuture, songs, 0);
                        activity.setBottomSheetInPeek(true);
                    }
                });
            }
        } else if (bundle.containsKey(Constants.MEDIA_BEST_OF) && bundle.getBoolean(Constants.MEDIA_BEST_OF)) {
            if (mediaBrowserListenableFuture != null) {
                homeViewModel.getArtistBestOf(getViewLifecycleOwner(), bundle.getParcelable(Constants.ARTIST_OBJECT)).observe(getViewLifecycleOwner(), songs -> {
                    MusicUtil.ratingFilter(songs);

                    if (!songs.isEmpty()) {
                        MediaManager.startQueue(mediaBrowserListenableFuture, songs, 0);
                        activity.setBottomSheetInPeek(true);
                    }
                });
            }
        } else {
            Navigation.findNavController(requireView()).navigate(R.id.artistPageFragment, bundle);
        }
    }

    @Override
    public void onArtistLongClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.artistBottomSheetDialog, bundle);
    }

    @Override
    public void onYearClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.songListPageFragment, bundle);
    }

    @Override
    public void onShareClick(Bundle bundle) {
        Share share = bundle.getParcelable(Constants.SHARE_OBJECT);
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(share.getUrl())).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
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
    public void onShareLongClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.shareBottomSheetDialog, bundle);
    }
}
