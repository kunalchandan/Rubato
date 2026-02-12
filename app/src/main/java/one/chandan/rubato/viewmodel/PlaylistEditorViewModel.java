package one.chandan.rubato.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import one.chandan.rubato.repository.LibraryRepository;
import one.chandan.rubato.repository.SharingRepository;
import one.chandan.rubato.subsonic.models.Child;
import one.chandan.rubato.subsonic.models.Playlist;
import one.chandan.rubato.subsonic.models.Share;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PlaylistEditorViewModel extends AndroidViewModel {
    private static final String TAG = "PlaylistEditorViewModel";

    private final LibraryRepository libraryRepository;
    private final SharingRepository sharingRepository;

    private Child toAdd;
    private Playlist toEdit;

    private MutableLiveData<List<Child>> songLiveList = new MutableLiveData<>(Collections.emptyList());

    public PlaylistEditorViewModel(@NonNull Application application) {
        super(application);

        libraryRepository = new LibraryRepository();
        sharingRepository = new SharingRepository();
    }

    public void createPlaylist(String name) {
        libraryRepository.createPlaylist(null, name, new ArrayList(Collections.singletonList(toAdd.getId())));
    }

    public void updatePlaylist(String name) {
        libraryRepository.updatePlaylist(toEdit.getId(), name, getPlaylistSongIds());
    }

    public void deletePlaylist() {
        if (toEdit != null) libraryRepository.deletePlaylist(toEdit.getId());
    }

    public Child getSongToAdd() {
        return toAdd;
    }

    public void setSongToAdd(Child song) {
        this.toAdd = song;
    }

    public Playlist getPlaylistToEdit() {
        return toEdit;
    }

    public void setPlaylistToEdit(Playlist playlist) {
        this.toEdit = playlist;

        if (playlist != null) {
            this.songLiveList = libraryRepository.getPlaylistSongs(toEdit.getId());
        } else {
            this.songLiveList = new MutableLiveData<>(Collections.emptyList());
        }
    }

    public LiveData<List<Child>> getPlaylistSongLiveList() {
        return songLiveList;
    }

    public void removeFromPlaylistSongLiveList(int position) {
        List<Child> songs = songLiveList.getValue();
        if (songs == null || position < 0 || position >= songs.size()) {
            return;
        }
        songs.remove(position);
        songLiveList.postValue(songs);
    }

    public void orderPlaylistSongLiveListAfterSwap(List<Child> songs) {
        songLiveList.postValue(songs);
    }

    private ArrayList<String> getPlaylistSongIds() {
        List<Child> songs = songLiveList.getValue();
        ArrayList<String> ids = new ArrayList<>();

        if (songs != null && !songs.isEmpty()) {
            for (Child song : songs) {
                ids.add(song.getId());
            }
        }

        return ids;
    }

    public MutableLiveData<Share> sharePlaylist() {
        return sharingRepository.createShare(toEdit.getId(), toEdit.getName(), null);
    }
}
