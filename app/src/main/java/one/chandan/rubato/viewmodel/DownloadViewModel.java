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
import one.chandan.rubato.subsonic.models.Child;
import one.chandan.rubato.util.Preferences;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DownloadViewModel extends AndroidViewModel {
    private static final String TAG = "DownloadViewModel";

    private final DownloadRepository downloadRepository;

    private final MutableLiveData<List<Child>> downloadedTrackSample = new MutableLiveData<>(null);
    private final MutableLiveData<ArrayList<DownloadStack>> viewStack = new MutableLiveData<>(null);

    public DownloadViewModel(@NonNull Application application) {
        super(application);

        downloadRepository = new DownloadRepository();

        initViewStack(new DownloadStack(Preferences.getDefaultDownloadViewType(), null));
    }

    public LiveData<List<Child>> getDownloadedTracks(LifecycleOwner owner) {
        downloadRepository.getLiveDownload().observe(owner, downloads -> downloadedTrackSample.postValue(downloads.stream().map(download -> (Child) download).collect(Collectors.toList())));
        return downloadedTrackSample;
    }

    public LiveData<ArrayList<DownloadStack>> getViewStack() {
        return viewStack;
    }

    public void initViewStack(DownloadStack level) {
        ArrayList<DownloadStack> stack = new ArrayList<>();
        stack.add(level);
        viewStack.setValue(stack);
    }

    public void pushViewStack(DownloadStack level) {
        ArrayList<DownloadStack> stack = viewStack.getValue();
        stack.add(level);
        viewStack.setValue(stack);
    }

    public void popViewStack() {
        ArrayList<DownloadStack> stack = viewStack.getValue();
        stack.remove(stack.size() - 1);
        viewStack.setValue(stack);
    }
}
