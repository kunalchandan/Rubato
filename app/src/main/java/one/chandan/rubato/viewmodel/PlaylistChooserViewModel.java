package one.chandan.rubato.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import one.chandan.rubato.repository.LibraryRepository;
import one.chandan.rubato.subsonic.models.Child;
import one.chandan.rubato.subsonic.models.Playlist;
import one.chandan.rubato.util.CollectionUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PlaylistChooserViewModel extends AndroidViewModel {
    private final LibraryRepository libraryRepository;

    private final MutableLiveData<List<Playlist>> playlists = new MutableLiveData<>(Collections.emptyList());
    private Child toAdd;

    public PlaylistChooserViewModel(@NonNull Application application) {
        super(application);

        libraryRepository = new LibraryRepository();
    }

    public LiveData<List<Playlist>> getPlaylistList(LifecycleOwner owner) {
        libraryRepository.getPlaylists(false, -1)
                .observe(owner, items -> playlists.postValue(CollectionUtil.arrayListOrEmpty(items)));
        return playlists;
    }

    public void addSongToPlaylist(String playlistId) {
        libraryRepository.addSongToPlaylist(playlistId, new ArrayList(Collections.singletonList(toAdd.getId())));
    }

    public void setSongToAdd(Child song) {
        toAdd = song;
    }

    public Child getSongToAdd() {
        return toAdd;
    }
}
