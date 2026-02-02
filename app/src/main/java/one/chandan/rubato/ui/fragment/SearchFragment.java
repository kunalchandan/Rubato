package one.chandan.rubato.ui.fragment;

import android.content.ComponentName;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageView;
import android.widget.TextView;
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
import one.chandan.rubato.databinding.FragmentSearchBinding;
import one.chandan.rubato.helper.recyclerview.CustomLinearSnapHelper;
import one.chandan.rubato.helper.recyclerview.QueueSwipeHelper;
import one.chandan.rubato.interfaces.ClickCallback;
import one.chandan.rubato.repository.LocalMusicRepository;
import one.chandan.rubato.service.MediaManager;
import one.chandan.rubato.service.MediaService;
import one.chandan.rubato.subsonic.models.Child;
import one.chandan.rubato.ui.activity.MainActivity;
import one.chandan.rubato.ui.adapter.AlbumAdapter;
import one.chandan.rubato.ui.adapter.ArtistAdapter;
import one.chandan.rubato.ui.adapter.PlaylistHorizontalAdapter;
import one.chandan.rubato.ui.adapter.SongHorizontalAdapter;
import one.chandan.rubato.ui.dialog.PlaylistEditorDialog;
import one.chandan.rubato.glide.CustomGlideRequest;
import one.chandan.rubato.model.SearchSuggestion;
import one.chandan.rubato.util.Constants;
import one.chandan.rubato.util.DownloadUtil;
import one.chandan.rubato.util.FavoriteUtil;
import one.chandan.rubato.util.NetworkUtil;
import one.chandan.rubato.util.SearchIndexUtil;
import one.chandan.rubato.viewmodel.SearchViewModel;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.shape.RelativeCornerSize;
import com.google.android.material.snackbar.Snackbar;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.Collections;

@UnstableApi
public class SearchFragment extends Fragment implements ClickCallback {
    private static final String TAG = "SearchFragment";

    private FragmentSearchBinding bind;
    private MainActivity activity;
    private SearchViewModel searchViewModel;

    private ArtistAdapter artistAdapter;
    private AlbumAdapter albumAdapter;
    private PlaylistHorizontalAdapter playlistAdapter;
    private SongHorizontalAdapter songHorizontalAdapter;

    private ListenableFuture<MediaBrowser> mediaBrowserListenableFuture;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();

        bind = FragmentSearchBinding.inflate(inflater, container, false);
        View view = bind.getRoot();
        searchViewModel = new ViewModelProvider(requireActivity()).get(SearchViewModel.class);

        initSearchResultView();
        initSearchView();
        inputFocus();

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

    private void initSearchResultView() {
        // Artists
        bind.searchResultArtistRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        bind.searchResultArtistRecyclerView.setHasFixedSize(true);

        artistAdapter = new ArtistAdapter(this, false, false);
        bind.searchResultArtistRecyclerView.setAdapter(artistAdapter);

        CustomLinearSnapHelper artistSnapHelper = new CustomLinearSnapHelper();
        artistSnapHelper.attachToRecyclerView(bind.searchResultArtistRecyclerView);

        // Albums
        bind.searchResultAlbumRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        bind.searchResultAlbumRecyclerView.setHasFixedSize(true);

        albumAdapter = new AlbumAdapter(this);
        bind.searchResultAlbumRecyclerView.setAdapter(albumAdapter);

        CustomLinearSnapHelper albumSnapHelper = new CustomLinearSnapHelper();
        albumSnapHelper.attachToRecyclerView(bind.searchResultAlbumRecyclerView);

        // Songs
        bind.searchResultTracksRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        bind.searchResultTracksRecyclerView.setHasFixedSize(true);

        songHorizontalAdapter = new SongHorizontalAdapter(this, true, false, null);
        bind.searchResultTracksRecyclerView.setAdapter(songHorizontalAdapter);

        QueueSwipeHelper.attach(bind.searchResultTracksRecyclerView, songHorizontalAdapter, new QueueSwipeHelper.QueueSwipeAction() {
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
    }

    private void initSearchView() {
        setRecentSuggestions();

        bind.searchView
                .getEditText()
                .setOnEditorActionListener((textView, actionId, keyEvent) -> {
                    String query = bind.searchView.getText().toString();

                    if (isQueryValid(query)) {
                        search(query);
                        return true;
                    }

                    return false;
                });

        bind.searchView
                .getEditText()
                .addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence charSequence, int start, int count, int after) {

                    }

                    @Override
                    public void onTextChanged(CharSequence charSequence, int start, int before, int count) {
                        if (start + count > 1) {
                            setSearchSuggestions(charSequence.toString());
                        } else {
                            setRecentSuggestions();
                        }
                    }

                    @Override
                    public void afterTextChanged(Editable editable) {

                    }
                });
    }

    public void setRecentSuggestions() {
        bind.searchViewSuggestionContainer.removeAllViews();

        for (String suggestion : searchViewModel.getRecentSearchSuggestion()) {
            View view = LayoutInflater.from(bind.searchViewSuggestionContainer.getContext()).inflate(R.layout.item_search_suggestion, bind.searchViewSuggestionContainer, false);

            ImageView leadingImageView = view.findViewById(R.id.search_suggestion_icon);
            TextView titleView = view.findViewById(R.id.search_suggestion_title);
            TextView subtitleView = view.findViewById(R.id.search_suggestion_subtitle);
            ImageView tailingImageView = view.findViewById(R.id.search_suggestion_delete_icon);

            leadingImageView.setImageDrawable(getResources().getDrawable(R.drawable.ic_history, null));
            titleView.setText(suggestion);
            subtitleView.setText(R.string.search_suggestion_subtitle_recent);

            view.setOnClickListener(v -> search(suggestion));

            tailingImageView.setOnClickListener(v -> {
                searchViewModel.deleteRecentSearch(suggestion);
                setRecentSuggestions();
            });

            bind.searchViewSuggestionContainer.addView(view);
        }
    }

    public void setSearchSuggestions(String query) {
        searchViewModel.getSearchSuggestion(query).observe(getViewLifecycleOwner(), suggestions -> {
            bind.searchViewSuggestionContainer.removeAllViews();

            for (SearchSuggestion suggestion : suggestions) {
                View view = LayoutInflater.from(bind.searchViewSuggestionContainer.getContext()).inflate(R.layout.item_search_suggestion, bind.searchViewSuggestionContainer, false);

                ImageView leadingImageView = view.findViewById(R.id.search_suggestion_icon);
                TextView titleView = view.findViewById(R.id.search_suggestion_title);
                TextView subtitleView = view.findViewById(R.id.search_suggestion_subtitle);
                ImageView tailingImageView = view.findViewById(R.id.search_suggestion_delete_icon);

                titleView.setText(suggestion.getTitle());
                subtitleView.setText(resolveSuggestionSubtitle(suggestion));
                tailingImageView.setVisibility(View.GONE);

                bindSearchSuggestionIcon(leadingImageView, suggestion);

                view.setOnClickListener(v -> search(suggestion.getTitle()));

                bind.searchViewSuggestionContainer.addView(view);
            }
        });

        // Playlists
        bind.searchResultPlaylistRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        bind.searchResultPlaylistRecyclerView.setHasFixedSize(true);

        playlistAdapter = new PlaylistHorizontalAdapter(this, true);
        bind.searchResultPlaylistRecyclerView.setAdapter(playlistAdapter);

        CustomLinearSnapHelper playlistSnapHelper = new CustomLinearSnapHelper();
        playlistSnapHelper.attachToRecyclerView(bind.searchResultPlaylistRecyclerView);
    }

    private void bindSearchSuggestionIcon(ImageView imageView, SearchSuggestion suggestion) {
        if (suggestion == null || imageView == null) return;
        CustomGlideRequest.ResourceType type;
        int placeholderRes;
        switch (suggestion.getKind()) {
            case ARTIST:
                type = CustomGlideRequest.ResourceType.Artist;
                placeholderRes = R.drawable.ic_placeholder_artist;
                break;
            case ALBUM:
                type = CustomGlideRequest.ResourceType.Album;
                placeholderRes = R.drawable.ic_placeholder_album;
                break;
            case SONG:
                type = CustomGlideRequest.ResourceType.Song;
                placeholderRes = R.drawable.ic_placeholder_song;
                break;
            case UNKNOWN:
            default:
                type = CustomGlideRequest.ResourceType.Unknown;
                placeholderRes = R.drawable.ic_search;
                break;
        }

        String coverArtId = suggestion.getCoverArtId();
        if (coverArtId != null && !coverArtId.isEmpty()) {
            CustomGlideRequest.Builder
                    .from(requireContext(), coverArtId, type)
                    .build()
                    .into(imageView);
        } else {
            imageView.setImageDrawable(getResources().getDrawable(placeholderRes, null));
        }

        if (imageView instanceof ShapeableImageView) {
            ShapeableImageView shapeableImageView = (ShapeableImageView) imageView;
            float cornerSize = suggestion.getKind() == SearchSuggestion.Kind.ARTIST ? 0.5f : 0f;
            shapeableImageView.setShapeAppearanceModel(
                    shapeableImageView.getShapeAppearanceModel()
                            .toBuilder()
                            .setAllCornerSizes(new RelativeCornerSize(cornerSize))
                            .build()
            );
        }
    }

    private String resolveSuggestionSubtitle(SearchSuggestion suggestion) {
        if (suggestion == null) return getString(R.string.search_suggestion_subtitle_unknown);
        switch (suggestion.getKind()) {
            case ARTIST:
                return getString(R.string.search_suggestion_subtitle_artist);
            case ALBUM:
                return getString(R.string.search_suggestion_subtitle_album);
            case SONG:
                return getString(R.string.search_suggestion_subtitle_song);
            case UNKNOWN:
            default:
                return getString(R.string.search_suggestion_subtitle_unknown);
        }
    }

    public void search(String query) {
        searchViewModel.setQuery(query);
        bind.searchBar.setText(query);
        bind.searchView.hide();
        performSearch(query);
    }

    private void performSearch(String query) {
        searchViewModel.search3(query).observe(getViewLifecycleOwner(), result -> {
            if (bind != null) {
                if (result.getArtists() != null) {
                    bind.searchArtistSector.setVisibility(!result.getArtists().isEmpty() ? View.VISIBLE : View.GONE);
                    artistAdapter.setItems(result.getArtists());
                } else {
                    artistAdapter.setItems(Collections.emptyList());
                    bind.searchArtistSector.setVisibility(View.GONE);
                }

                if (result.getAlbums() != null) {
                    bind.searchAlbumSector.setVisibility(!result.getAlbums().isEmpty() ? View.VISIBLE : View.GONE);
                    albumAdapter.setItems(result.getAlbums());
                } else {
                    albumAdapter.setItems(Collections.emptyList());
                    bind.searchAlbumSector.setVisibility(View.GONE);
                }

                if (result.getSongs() != null) {
                    bind.searchSongSector.setVisibility(!result.getSongs().isEmpty() ? View.VISIBLE : View.GONE);
                    songHorizontalAdapter.setItems(result.getSongs());
                } else {
                    songHorizontalAdapter.setItems(Collections.emptyList());
                    bind.searchSongSector.setVisibility(View.GONE);
                }
            }
        });

        searchViewModel.searchPlaylists(query).observe(getViewLifecycleOwner(), playlists -> {
            if (bind != null) {
                if (playlists != null && !playlists.isEmpty()) {
                    bind.searchPlaylistSector.setVisibility(View.VISIBLE);
                    playlistAdapter.setItems(playlists);
                } else {
                    playlistAdapter.setItems(Collections.emptyList());
                    bind.searchPlaylistSector.setVisibility(View.GONE);
                }
            }
        });

        bind.searchResultLayout.setVisibility(View.VISIBLE);
    }

    private boolean blockIfUnsupportedSource(String id) {
        return blockIfUnsupportedSource(id, false);
    }

    private boolean blockIfUnsupportedSource(String id, boolean allowJellyfin) {
        if (id == null) return false;
        if (SearchIndexUtil.isJellyfinTagged(id) && !allowJellyfin) {
            Toast.makeText(requireContext(), R.string.music_sources_jellyfin_coming_soon, Toast.LENGTH_SHORT).show();
            return true;
        }
        return false;
    }

    private boolean isQueryValid(String query) {
        return !query.equals("") && query.trim().length() > 2;
    }

    private void inputFocus() {
        bind.searchView.show();
    }

    private void initializeMediaBrowser() {
        mediaBrowserListenableFuture = new MediaBrowser.Builder(requireContext(), new SessionToken(requireContext(), new ComponentName(requireContext(), MediaService.class))).buildAsync();
    }

    private void releaseMediaBrowser() {
        MediaBrowser.releaseFuture(mediaBrowserListenableFuture);
    }

    @Override
    public void onMediaClick(Bundle bundle) {
        if (NetworkUtil.isOffline()) {
            Child song = null;
            if (bundle != null && bundle.containsKey(Constants.TRACK_OBJECT)) {
                song = bundle.getParcelable(Constants.TRACK_OBJECT);
            } else if (bundle != null && bundle.containsKey(Constants.TRACKS_OBJECT)) {
                int index = bundle.getInt(Constants.ITEM_POSITION, -1);
                java.util.ArrayList<Child> tracks = bundle.getParcelableArrayList(Constants.TRACKS_OBJECT);
                if (tracks != null && index >= 0 && index < tracks.size()) {
                    song = tracks.get(index);
                }
            }
            if (song != null) {
                boolean isLocal = LocalMusicRepository.isLocalSong(song);
                boolean isDownloaded = isLocal || DownloadUtil.getDownloadTracker(requireContext()).isDownloaded(song.getId());
                if (!isDownloaded) {
                    Snackbar.make(bind.getRoot(), getString(R.string.queue_add_next_unavailable_offline), Snackbar.LENGTH_SHORT).show();
                    return;
                }
            }
        }
        if (bundle != null && bundle.containsKey(Constants.TRACK_OBJECT)) {
            Child song = bundle.getParcelable(Constants.TRACK_OBJECT);
            if (song != null && blockIfUnsupportedSource(song.getId(), true)) {
                return;
            }
        }
        MediaManager.startQueue(mediaBrowserListenableFuture, bundle.getParcelableArrayList(Constants.TRACKS_OBJECT), bundle.getInt(Constants.ITEM_POSITION));
        activity.setBottomSheetInPeek(true);
    }

    @Override
    public void onMediaLongClick(Bundle bundle) {
        if (bundle != null && bundle.containsKey(Constants.TRACK_OBJECT)) {
            Child song = bundle.getParcelable(Constants.TRACK_OBJECT);
            if (song != null && blockIfUnsupportedSource(song.getId())) {
                return;
            }
        }
        Navigation.findNavController(requireView()).navigate(R.id.songBottomSheetDialog, bundle);
    }

    @Override
    public void onAlbumClick(Bundle bundle) {
        if (bundle != null && bundle.containsKey(Constants.ALBUM_OBJECT)) {
            one.chandan.rubato.subsonic.models.AlbumID3 album = bundle.getParcelable(Constants.ALBUM_OBJECT);
            if (album != null && blockIfUnsupportedSource(album.getId(), true)) {
                return;
            }
        }
        Navigation.findNavController(requireView()).navigate(R.id.albumPageFragment, bundle);
    }

    @Override
    public void onAlbumLongClick(Bundle bundle) {
        if (bundle != null && bundle.containsKey(Constants.ALBUM_OBJECT)) {
            one.chandan.rubato.subsonic.models.AlbumID3 album = bundle.getParcelable(Constants.ALBUM_OBJECT);
            if (album != null && blockIfUnsupportedSource(album.getId())) {
                return;
            }
        }
        Navigation.findNavController(requireView()).navigate(R.id.albumBottomSheetDialog, bundle);
    }

    @Override
    public void onArtistClick(Bundle bundle) {
        if (bundle != null && bundle.containsKey(Constants.ARTIST_OBJECT)) {
            one.chandan.rubato.subsonic.models.ArtistID3 artist = bundle.getParcelable(Constants.ARTIST_OBJECT);
            if (artist != null && blockIfUnsupportedSource(artist.getId(), true)) {
                return;
            }
        }
        Navigation.findNavController(requireView()).navigate(R.id.artistPageFragment, bundle);
    }

    @Override
    public void onArtistLongClick(Bundle bundle) {
        if (bundle != null && bundle.containsKey(Constants.ARTIST_OBJECT)) {
            one.chandan.rubato.subsonic.models.ArtistID3 artist = bundle.getParcelable(Constants.ARTIST_OBJECT);
            if (artist != null && blockIfUnsupportedSource(artist.getId())) {
                return;
            }
        }
        Navigation.findNavController(requireView()).navigate(R.id.artistBottomSheetDialog, bundle);
    }

    @Override
    public void onPlaylistClick(Bundle bundle) {
        if (bundle != null && bundle.containsKey(Constants.PLAYLIST_OBJECT)) {
            one.chandan.rubato.subsonic.models.Playlist playlist = bundle.getParcelable(Constants.PLAYLIST_OBJECT);
            if (playlist != null && blockIfUnsupportedSource(playlist.getId(), true)) {
                return;
            }
        }
        Navigation.findNavController(requireView()).navigate(R.id.playlistPageFragment, bundle);
    }

    @Override
    public void onPlaylistLongClick(Bundle bundle) {
        if (bundle != null && bundle.containsKey(Constants.PLAYLIST_OBJECT)) {
            one.chandan.rubato.subsonic.models.Playlist playlist = bundle.getParcelable(Constants.PLAYLIST_OBJECT);
            if (playlist != null && blockIfUnsupportedSource(playlist.getId())) {
                return;
            }
        }
        PlaylistEditorDialog dialog = new PlaylistEditorDialog(null);
        dialog.setArguments(bundle);
        dialog.show(activity.getSupportFragmentManager(), null);
    }
}
