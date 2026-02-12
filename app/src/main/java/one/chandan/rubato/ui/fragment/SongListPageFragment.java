package one.chandan.rubato.ui.fragment;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.PopupMenu;
import android.widget.SearchView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaBrowser;
import androidx.media3.session.SessionToken;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import one.chandan.rubato.R;
import one.chandan.rubato.databinding.FragmentSongListPageBinding;
import one.chandan.rubato.helper.recyclerview.PaginationScrollListener;
import one.chandan.rubato.helper.recyclerview.QueueSwipeHelper;
import one.chandan.rubato.interfaces.ClickCallback;
import one.chandan.rubato.service.MediaManager;
import one.chandan.rubato.service.MediaService;
import one.chandan.rubato.subsonic.models.Child;
import one.chandan.rubato.subsonic.models.Genre;
import one.chandan.rubato.ui.activity.MainActivity;
import one.chandan.rubato.ui.adapter.SongHorizontalAdapter;
import one.chandan.rubato.util.Constants;
import one.chandan.rubato.util.FavoriteUtil;
import one.chandan.rubato.util.OfflinePolicy;
import one.chandan.rubato.viewmodel.SongListPageViewModel;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.chip.Chip;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@UnstableApi
public class SongListPageFragment extends Fragment implements ClickCallback {
    private static final String TAG = "SongListPageFragment";

    private FragmentSongListPageBinding bind;
    private MainActivity activity;
    private SongListPageViewModel songListPageViewModel;

    private SongHorizontalAdapter songHorizontalAdapter;

    private ListenableFuture<MediaBrowser> mediaBrowserListenableFuture;

    private boolean isLoading = true;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();

        bind = FragmentSongListPageBinding.inflate(inflater, container, false);
        View view = bind.getRoot();
        songListPageViewModel = new ViewModelProvider(requireActivity()).get(SongListPageViewModel.class);

        init();
        initAppBar();
        initButtons();
        initSongListView();
        initRelatedGenres();

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
        if (requireArguments().getString(Constants.MEDIA_RECENTLY_PLAYED) != null) {
            songListPageViewModel.title = Constants.MEDIA_RECENTLY_PLAYED;
            songListPageViewModel.toolbarTitle = getString(R.string.song_list_page_recently_played);
            bind.pageTitleLabel.setText(R.string.song_list_page_recently_played);
        } else if (requireArguments().getString(Constants.MEDIA_MOST_PLAYED) != null) {
            songListPageViewModel.title = Constants.MEDIA_MOST_PLAYED;
            songListPageViewModel.toolbarTitle = getString(R.string.song_list_page_most_played);
            bind.pageTitleLabel.setText(R.string.song_list_page_most_played);
        } else if (requireArguments().getString(Constants.MEDIA_RECENTLY_ADDED) != null) {
            songListPageViewModel.title = Constants.MEDIA_RECENTLY_ADDED;
            songListPageViewModel.toolbarTitle = getString(R.string.song_list_page_recently_added);
            bind.pageTitleLabel.setText(R.string.song_list_page_recently_added);
        } else if (requireArguments().getString(Constants.MEDIA_BY_GENRE) != null) {
            songListPageViewModel.title = Constants.MEDIA_BY_GENRE;
            songListPageViewModel.genre = requireArguments().getParcelable(Constants.GENRE_OBJECT);
            songListPageViewModel.toolbarTitle = songListPageViewModel.genre.getGenre();
            bind.pageTitleLabel.setText(songListPageViewModel.genre.getGenre());
        } else if (requireArguments().getString(Constants.MEDIA_BY_ARTIST) != null) {
            songListPageViewModel.title = Constants.MEDIA_BY_ARTIST;
            songListPageViewModel.artist = requireArguments().getParcelable(Constants.ARTIST_OBJECT);
            songListPageViewModel.toolbarTitle = getString(R.string.song_list_page_top, songListPageViewModel.artist.getName());
            bind.pageTitleLabel.setText(getString(R.string.song_list_page_top, songListPageViewModel.artist.getName()));
        } else if (requireArguments().getString(Constants.MEDIA_BY_GENRES) != null) {
            songListPageViewModel.title = Constants.MEDIA_BY_GENRES;
            songListPageViewModel.filters = requireArguments().getStringArrayList("filters_list");
            songListPageViewModel.filterNames = requireArguments().getStringArrayList("filter_name_list");
            songListPageViewModel.toolbarTitle = songListPageViewModel.getFiltersTitle();
            bind.pageTitleLabel.setText(songListPageViewModel.getFiltersTitle());
        } else if (requireArguments().getString(Constants.MEDIA_BY_YEAR) != null) {
            songListPageViewModel.title = Constants.MEDIA_BY_YEAR;
            songListPageViewModel.year = requireArguments().getInt("year_object");
            songListPageViewModel.toolbarTitle = getString(R.string.song_list_page_year, songListPageViewModel.year);
            bind.pageTitleLabel.setText(getString(R.string.song_list_page_year, songListPageViewModel.year));
        } else if (requireArguments().getString(Constants.MEDIA_STARRED) != null) {
            songListPageViewModel.title = Constants.MEDIA_STARRED;
            songListPageViewModel.toolbarTitle = getString(R.string.song_list_page_starred);
            bind.pageTitleLabel.setText(R.string.song_list_page_starred);
        } else if (requireArguments().getString(Constants.MEDIA_DOWNLOADED) != null) {
            songListPageViewModel.title = Constants.MEDIA_DOWNLOADED;
            songListPageViewModel.toolbarTitle = getString(R.string.song_list_page_downloaded);
            bind.pageTitleLabel.setText(getString(R.string.song_list_page_downloaded));
        } else if (requireArguments().getParcelable(Constants.ALBUM_OBJECT) != null) {
            songListPageViewModel.album = requireArguments().getParcelable(Constants.ALBUM_OBJECT);
            songListPageViewModel.title = Constants.MEDIA_FROM_ALBUM;
            songListPageViewModel.toolbarTitle = songListPageViewModel.album.getName();
            bind.pageTitleLabel.setText(songListPageViewModel.album.getName());
        }
    }

    private void initAppBar() {
        activity.setSupportActionBar(bind.toolbar);

        if (activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            activity.getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        if (bind != null)
            bind.toolbar.setNavigationOnClickListener(v -> {
                hideKeyboard(v);
                activity.navController.navigateUp();
            });

        if (bind != null)
            bind.appBarLayout.addOnOffsetChangedListener((appBarLayout, verticalOffset) -> {
                if ((bind.albumInfoSector.getHeight() + verticalOffset) < (2 * ViewCompat.getMinimumHeight(bind.toolbar))) {
                    bind.toolbar.setTitle(getString(R.string.toolbar_song_title_format, songListPageViewModel.toolbarTitle));
                } else {
                    bind.toolbar.setTitle(R.string.empty_string);
                }
            });
    }

    private void initButtons() {
        songListPageViewModel.getSongList().observe(getViewLifecycleOwner(), songs -> {
            if (bind != null) {
                setSongListPageSorter();

                bind.songListShuffleImageView.setOnClickListener(v -> {
                    List<Child> playable = OfflinePolicy.filterPlayable(requireContext(), songs);
                    if (playable.isEmpty()) {
                        if (OfflinePolicy.isOffline()) {
                            Snackbar.make(bind.getRoot(), getString(R.string.queue_add_next_unavailable_offline), Snackbar.LENGTH_SHORT).show();
                        }
                        return;
                    }
                    List<Child> shuffled = new ArrayList<>(playable);
                    Collections.shuffle(shuffled);
                    MediaManager.startQueue(mediaBrowserListenableFuture, shuffled.subList(0, Math.min(25, shuffled.size())), 0);
                    activity.setBottomSheetInPeek(true);
                });
                boolean hasPlayable = OfflinePolicy.hasPlayable(requireContext(), songs);
                bind.songListShuffleImageView.setEnabled(hasPlayable);
                bind.songListShuffleImageView.setAlpha(hasPlayable ? 1f : 0.4f);
            }
        });
    }

    private void initRelatedGenres() {
        if (!Constants.MEDIA_BY_GENRE.equals(songListPageViewModel.title)) {
            if (bind != null) {
                bind.relatedGenresLabel.setVisibility(View.GONE);
                bind.relatedGenresGroup.setVisibility(View.GONE);
                refreshAppBarLayout();
            }
            return;
        }

        songListPageViewModel.getRelatedGenres().observe(getViewLifecycleOwner(), genres -> {
            if (bind == null) return;
            bind.relatedGenresGroup.removeAllViews();
            if (genres == null || genres.isEmpty()) {
                bind.relatedGenresLabel.setVisibility(View.GONE);
                bind.relatedGenresGroup.setVisibility(View.GONE);
                refreshAppBarLayout();
                return;
            }

            for (Genre genre : genres) {
                if (genre == null || genre.getGenre() == null) continue;
                Chip chip = (Chip) getLayoutInflater().inflate(R.layout.chip_search_filter_genre, bind.relatedGenresGroup, false);
                chip.setText(genre.getGenre());
                chip.setCheckable(false);
                chip.setOnClickListener(v -> {
                    Bundle bundle = new Bundle();
                    bundle.putString(Constants.MEDIA_BY_GENRE, Constants.MEDIA_BY_GENRE);
                    bundle.putParcelable(Constants.GENRE_OBJECT, genre);
                    Navigation.findNavController(requireView()).navigate(R.id.songListPageFragment, bundle);
                });
                bind.relatedGenresGroup.addView(chip);
            }

            bind.relatedGenresLabel.setVisibility(View.VISIBLE);
            bind.relatedGenresGroup.setVisibility(View.VISIBLE);
            refreshAppBarLayout();
        });
    }

    private void refreshAppBarLayout() {
        if (bind == null) return;
        bind.appBarLayout.post(() -> {
            bind.appBarLayout.requestLayout();
            bind.appBarLayout.invalidate();
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initSongListView() {
        bind.songListRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        bind.songListRecyclerView.setHasFixedSize(true);

        songHorizontalAdapter = new SongHorizontalAdapter(this, true, false, null);
        bind.songListRecyclerView.setAdapter(songHorizontalAdapter);
        songListPageViewModel.getSongList().observe(getViewLifecycleOwner(), songs -> {
            isLoading = false;
            songHorizontalAdapter.setItems(songs);
            setSongListPageSubtitle(songs);
        });

        QueueSwipeHelper.attach(bind.songListRecyclerView, songHorizontalAdapter, new QueueSwipeHelper.QueueSwipeAction() {
            @Override
            public boolean canPerform(Child song, QueueSwipeHelper.SwipeAction action) {
                if (action == QueueSwipeHelper.SwipeAction.TOGGLE_FAVORITE) {
                    return true;
                }
                return OfflinePolicy.canQueue(requireContext(), song);
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

        bind.songListRecyclerView.addOnScrollListener(new PaginationScrollListener((LinearLayoutManager) bind.songListRecyclerView.getLayoutManager()) {
            @Override
            protected void loadMoreItems() {
                isLoading = true;
                songListPageViewModel.getSongsByPage(getViewLifecycleOwner());
            }

            @Override
            public boolean isLoading() {
                return isLoading;
            }
        });

        bind.songListRecyclerView.setOnTouchListener((v, event) -> {
            hideKeyboard(v);
            return false;
        });

        bind.songListSortImageView.setOnClickListener(view -> showPopupMenu(view, R.menu.sort_song_popup_menu));
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.toolbar_menu, menu);

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
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private void showPopupMenu(View view, int menuResource) {
        PopupMenu popup = new PopupMenu(requireContext(), view);
        popup.getMenuInflater().inflate(menuResource, popup.getMenu());

        popup.setOnMenuItemClickListener(menuItem -> {
            if (menuItem.getItemId() == R.id.menu_song_sort_name) {
                songHorizontalAdapter.sort(Constants.MEDIA_BY_TITLE);
                return true;
            } else if (menuItem.getItemId() == R.id.menu_song_sort_most_recently_starred) {
                songHorizontalAdapter.sort(Constants.MEDIA_MOST_RECENTLY_STARRED);
                return true;
            } else if (menuItem.getItemId() == R.id.menu_song_sort_least_recently_starred) {
                songHorizontalAdapter.sort(Constants.MEDIA_LEAST_RECENTLY_STARRED);
                return true;
            }

            return false;
        });

        popup.show();
    }

    private void setSongListPageSubtitle(List<Child> children) {
        switch (songListPageViewModel.title) {
            case Constants.MEDIA_BY_GENRE:
                bind.pageSubtitleLabel.setText(children.size() < songListPageViewModel.maxNumberByGenre ?
                        getString(R.string.generic_list_page_count, children.size()) :
                        getString(R.string.generic_list_page_count_unknown, songListPageViewModel.maxNumberByGenre)
                );
                break;
            case Constants.MEDIA_BY_YEAR:
                bind.pageSubtitleLabel.setText(children.size() < songListPageViewModel.maxNumberByYear ?
                        getString(R.string.generic_list_page_count, children.size()) :
                        getString(R.string.generic_list_page_count_unknown, songListPageViewModel.maxNumberByYear)
                );
                break;
            case Constants.MEDIA_BY_ARTIST:
            case Constants.MEDIA_BY_GENRES:
            case Constants.MEDIA_STARRED:
                bind.pageSubtitleLabel.setText(getString(R.string.generic_list_page_count, children.size()));
                break;
        }
    }

    private void setSongListPageSorter() {
        switch (songListPageViewModel.title) {
            case Constants.MEDIA_BY_GENRE:
            case Constants.MEDIA_BY_YEAR:
                bind.songListSortImageView.setVisibility(View.GONE);
                break;
            case Constants.MEDIA_BY_ARTIST:
            case Constants.MEDIA_BY_GENRES:
            case Constants.MEDIA_STARRED:
                bind.songListSortImageView.setVisibility(View.VISIBLE);
                break;
        }
    }

    private void initializeMediaBrowser() {
        mediaBrowserListenableFuture = new MediaBrowser.Builder(requireContext(), new SessionToken(requireContext(), new ComponentName(requireContext(), MediaService.class))).buildAsync();
    }

    private void releaseMediaBrowser() {
        MediaBrowser.releaseFuture(mediaBrowserListenableFuture);
    }

    @Override
    public void onMediaClick(Bundle bundle) {
        hideKeyboard(requireView());
        MediaManager.startQueue(mediaBrowserListenableFuture, bundle.getParcelableArrayList(Constants.TRACKS_OBJECT), bundle.getInt(Constants.ITEM_POSITION));
        activity.setBottomSheetInPeek(true);
    }

    @Override
    public void onMediaLongClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.songBottomSheetDialog, bundle);
    }
}
