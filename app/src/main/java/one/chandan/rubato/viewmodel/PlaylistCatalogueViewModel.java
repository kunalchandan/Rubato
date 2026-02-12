package one.chandan.rubato.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import one.chandan.rubato.repository.LibraryRepository;
import one.chandan.rubato.subsonic.models.Playlist;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PlaylistCatalogueViewModel extends AndroidViewModel {
    private final LibraryRepository libraryRepository;

    private String type;

    private final MutableLiveData<List<Playlist>> playlistList = new MutableLiveData<>(Collections.emptyList());

    public PlaylistCatalogueViewModel(@NonNull Application application) {
        super(application);

        libraryRepository = new LibraryRepository();
    }

    public LiveData<List<Playlist>> getPlaylistList(LifecycleOwner owner) {
        if (playlistList.getValue() == null || playlistList.getValue().isEmpty()) {
            libraryRepository.loadPlaylistsLegacy(items -> playlistList.postValue(items != null ? new ArrayList<>(items) : new ArrayList<>()));
        }

        return playlistList;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }
}
