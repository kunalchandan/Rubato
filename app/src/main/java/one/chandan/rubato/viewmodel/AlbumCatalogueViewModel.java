package one.chandan.rubato.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import one.chandan.rubato.repository.LibraryRepository;
import one.chandan.rubato.subsonic.models.AlbumID3;
import one.chandan.rubato.util.CollectionUtil;

import java.util.ArrayList;
import java.util.List;

public class AlbumCatalogueViewModel extends AndroidViewModel {
    private final MutableLiveData<List<AlbumID3>> albumList = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(true);
    private final LibraryRepository libraryRepository = new LibraryRepository();

    public AlbumCatalogueViewModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<List<AlbumID3>> getAlbumList() {
        return albumList;
    }

    public LiveData<Boolean> getLoadingStatus() {
        return loading;
    }

    public void loadAlbums() {
        albumList.setValue(new ArrayList<>());
        loading.postValue(true);
        libraryRepository.loadAlbumsLegacy(items -> {
            albumList.postValue(CollectionUtil.arrayListOrEmpty(items));
            loading.postValue(false);
        });
    }

    public void stopLoading() {
        loading.postValue(false);
    }
}
