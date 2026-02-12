package one.chandan.rubato.viewmodel;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import one.chandan.rubato.model.DownloadStack;
import one.chandan.rubato.repository.DownloadRepository;
import one.chandan.rubato.repository.LocalMusicRepository;
import one.chandan.rubato.subsonic.models.Child;
import one.chandan.rubato.ui.state.DownloadUiState;
import one.chandan.rubato.util.OfflinePolicy;
import one.chandan.rubato.util.Preferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class DownloadViewModel extends AndroidViewModel {
    private static final String TAG = "DownloadViewModel";

    private final DownloadRepository downloadRepository;

    private final MutableLiveData<List<Child>> downloadedTrackSample = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<ArrayList<DownloadStack>> viewStack = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<DownloadUiState> uiState = new MutableLiveData<>(null);
    private List<Child> cachedDownloads = new ArrayList<>();
    private List<DownloadStack> cachedStack = new ArrayList<>();

    public DownloadViewModel(@NonNull Application application) {
        super(application);

        downloadRepository = new DownloadRepository();

        initViewStack(new DownloadStack(Preferences.getDefaultDownloadViewType(), null));
    }

    public LiveData<List<Child>> getDownloadedTracks(LifecycleOwner owner) {
        downloadRepository.getLiveDownload().observe(owner, downloads -> {
            List<Child> safeDownloads = downloads != null ? new ArrayList<>(downloads) : new ArrayList<>();
            List<Child> mapped = safeDownloads.stream()
                    .map(download -> (Child) download)
                    .collect(Collectors.toList());
            mergeWithLocalLibrary(mapped);
        });
        return downloadedTrackSample;
    }

    public LiveData<ArrayList<DownloadStack>> getViewStack() {
        return viewStack;
    }

    public LiveData<DownloadUiState> getUiState(LifecycleOwner owner) {
        getDownloadedTracks(owner);
        return uiState;
    }

    public void initViewStack(DownloadStack level) {
        ArrayList<DownloadStack> stack = new ArrayList<>();
        stack.add(level);
        viewStack.setValue(stack);
        cachedStack = stack;
        updateUiState();
    }

    public void pushViewStack(DownloadStack level) {
        ArrayList<DownloadStack> stack = viewStack.getValue();
        if (stack == null) {
            stack = new ArrayList<>();
        }
        stack.add(level);
        viewStack.setValue(stack);
        cachedStack = stack;
        updateUiState();
    }

    public void popViewStack() {
        ArrayList<DownloadStack> stack = viewStack.getValue();
        if (stack == null || stack.isEmpty()) {
            return;
        }
        stack.remove(stack.size() - 1);
        viewStack.setValue(stack);
        cachedStack = stack;
        updateUiState();
    }

    private void updateUiState() {
        boolean loading = cachedDownloads == null || cachedStack == null;
        uiState.postValue(new DownloadUiState(
                loading,
                OfflinePolicy.isOffline(),
                cachedDownloads == null ? new ArrayList<>() : cachedDownloads,
                cachedStack == null ? new ArrayList<>() : cachedStack
        ));
    }

    private void mergeWithLocalLibrary(List<Child> downloads) {
        List<Child> base = downloads != null ? downloads : new ArrayList<>();
        LocalMusicRepository.loadLibrary(getApplication(), library -> {
            List<Child> merged = new ArrayList<>(base);
            if (library != null && library.songs != null && !library.songs.isEmpty()) {
                merged.addAll(library.songs);
            }
            downloadedTrackSample.postValue(merged);
            cachedDownloads = merged;
            updateUiState();
        });
    }
}
