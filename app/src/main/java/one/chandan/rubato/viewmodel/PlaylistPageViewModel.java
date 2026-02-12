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

import java.util.ArrayList;
import java.util.List;

public class PlaylistPageViewModel extends AndroidViewModel {
    private final LibraryRepository libraryRepository;

    private Playlist playlist;
    private LiveData<List<Child>> playlistSongs;
    private String playlistId;

    public PlaylistPageViewModel(@NonNull Application application) {
        super(application);

        libraryRepository = new LibraryRepository();
    }

    public LiveData<List<Child>> getPlaylistSongLiveList() {
        if (playlistSongs == null && playlist != null) {
            playlistId = playlist.getId();
            playlistSongs = libraryRepository.getPlaylistSongs(playlistId);
        }
        return playlistSongs;
    }

    public Playlist getPlaylist() {
        return playlist;
    }

    public void setPlaylist(Playlist playlist) {
        this.playlist = playlist;
        if (playlist == null || playlist.getId() == null) {
            playlistId = null;
            playlistSongs = null;
            return;
        }
        if (playlistSongs == null || playlistId == null || !playlist.getId().equals(playlistId)) {
            playlistId = playlist.getId();
            playlistSongs = libraryRepository.getPlaylistSongs(playlistId);
        }
    }

    public LiveData<Boolean> isPinned(LifecycleOwner owner) {
        MutableLiveData<Boolean> isPinnedLive = new MutableLiveData<>();

        libraryRepository.getPinnedPlaylists().observe(owner, playlists -> {
            isPinnedLive.postValue(playlists.stream().anyMatch(obj -> obj.getId().equals(playlist.getId())));
        });

        return isPinnedLive;
    }

    public void setPinned(boolean isNowPinned) {
        libraryRepository.setPinned(playlist, isNowPinned);
    }

    public void updatePlaylistOrder(List<Child> songs) {
        if (playlist == null || songs == null) return;

        ArrayList<String> songIds = new ArrayList<>();
        ArrayList<Integer> songIndexToRemove = new ArrayList<>();

        for (int i = 0; i < songs.size(); i++) {
            Child song = songs.get(i);
            if (song != null) {
                songIds.add(song.getId());
                songIndexToRemove.add(i);
            }
        }

        boolean isPublic = playlist.isUniversal() == null || playlist.isUniversal();
        libraryRepository.updatePlaylist(playlist.getId(), playlist.getName(), isPublic, songIds, songIndexToRemove);
    }
}
