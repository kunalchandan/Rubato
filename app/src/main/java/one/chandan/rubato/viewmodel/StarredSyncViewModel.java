package one.chandan.rubato.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import one.chandan.rubato.repository.LibraryRepository;
import one.chandan.rubato.subsonic.models.Child;
import one.chandan.rubato.util.CollectionUtil;

import java.util.Collections;
import java.util.List;

public class StarredSyncViewModel extends AndroidViewModel {
    private final LibraryRepository libraryRepository;

    private final MutableLiveData<List<Child>> starredTracks = new MutableLiveData<>(Collections.emptyList());

    public StarredSyncViewModel(@NonNull Application application) {
        super(application);

        libraryRepository = new LibraryRepository();
    }

    public LiveData<List<Child>> getStarredTracks(LifecycleOwner owner) {
        libraryRepository.getStarredSongs(false, -1)
                .observe(owner, items -> starredTracks.postValue(CollectionUtil.arrayListOrEmpty(items)));
        return starredTracks;
    }
}
