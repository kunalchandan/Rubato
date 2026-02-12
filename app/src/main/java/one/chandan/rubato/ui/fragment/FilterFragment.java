package one.chandan.rubato.ui.fragment;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;

import one.chandan.rubato.R;
import one.chandan.rubato.databinding.FragmentFilterBinding;
import one.chandan.rubato.subsonic.models.Genre;
import one.chandan.rubato.ui.activity.MainActivity;
import one.chandan.rubato.util.Constants;
import one.chandan.rubato.util.MusicUtil;
import one.chandan.rubato.sync.SyncOrchestrator;
import one.chandan.rubato.viewmodel.FilterViewModel;
import com.google.android.material.chip.Chip;

import java.util.Collections;
import java.util.List;

import java.util.Collections;
import java.util.List;

@OptIn(markerClass = UnstableApi.class)
public class FilterFragment extends Fragment {
    private static final String TAG = "FilterFragment";

    private MainActivity activity;
    private FragmentFilterBinding bind;
    private FilterViewModel filterViewModel;
    private final Handler syncHandler = new Handler(Looper.getMainLooper());
    private Runnable syncRefreshRunnable;
    private long lastSyncRefreshMs = 0L;
    private long lastSyncCompletedAt = 0L;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();
        bind = FragmentFilterBinding.inflate(inflater, container, false);
        View view = bind.getRoot();
        filterViewModel = new ViewModelProvider(requireActivity()).get(FilterViewModel.class);

        init();
        initAppBar();
        setFilterChips();
        bindSyncState();
        return view;
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
        Bundle bundle = new Bundle();
        bundle.putString(Constants.MEDIA_BY_GENRES, Constants.MEDIA_BY_GENRES);
        bundle.putStringArrayList("filters_list", filterViewModel.getFilters());
        bundle.putStringArrayList("filter_name_list", filterViewModel.getFilterNames());
        bind.finishFilteringTextViewClickable.setOnClickListener(v -> {
            if (filterViewModel.getFilters().size() > 1)
                activity.navController.navigate(R.id.action_filterFragment_to_songListPageFragment, bundle);
            else
                Toast.makeText(requireContext(), getString(R.string.filter_info_selection), Toast.LENGTH_SHORT).show();
        });
    }

    private void initAppBar() {
        activity.setSupportActionBar(bind.toolbar);

        if (activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            activity.getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        bind.toolbar.setNavigationOnClickListener(v -> activity.navController.navigateUp());


        bind.appBarLayout.addOnOffsetChangedListener((appBarLayout, verticalOffset) -> {
            if ((bind.genreFilterInfoSector.getHeight() + verticalOffset) < (2 * ViewCompat.getMinimumHeight(bind.toolbar))) {
                bind.toolbar.setTitle(R.string.filter_title);
            } else {
                bind.toolbar.setTitle(R.string.empty_string);
            }
        });
    }

    private void setFilterChips() {
        filterViewModel.getGenreList().observe(getViewLifecycleOwner(), genres -> {
            if (bind == null) return;
            bind.loadingProgressBar.setVisibility(View.GONE);
            bind.filterContainer.setVisibility(View.VISIBLE);
            bind.filtersChipsGroup.removeAllViews();
            if (genres == null || genres.isEmpty()) {
                return;
            }
            List<Genre> safeGenres = genres != null ? genres : Collections.emptyList();
            for (Genre genre : safeGenres) {
                if (genre == null || genre.getGenre() == null) {
                    continue;
                }
                Chip chip = (Chip) requireActivity().getLayoutInflater().inflate(R.layout.chip_search_filter_genre, null, false);
                chip.setText(genre.getGenre());
                chip.setChecked(filterViewModel.getFilters().contains(genre.getGenre()));
                chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked)
                        filterViewModel.addFilter(genre.getGenre(), buttonView.getText().toString());
                    else
                        filterViewModel.removeFilter(genre.getGenre(), buttonView.getText().toString());
                });
                bind.filtersChipsGroup.addView(chip);
            }
        });
    }

    private void bindSyncState() {
        SyncOrchestrator.getSyncState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) return;
            if (state.getActive()) {
                return;
            }
            long completedAt = state.getLastCompletedAt();
            if (completedAt > 0 && completedAt != lastSyncCompletedAt) {
                lastSyncCompletedAt = completedAt;
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
            if (bind == null || filterViewModel == null) return;
            lastSyncRefreshMs = SystemClock.elapsedRealtime();
            filterViewModel.refreshGenreList();
        };
        syncHandler.postDelayed(syncRefreshRunnable, delay);
    }
}
