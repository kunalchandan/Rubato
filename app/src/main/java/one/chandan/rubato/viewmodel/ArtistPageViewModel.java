package one.chandan.rubato.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import one.chandan.rubato.repository.LibraryRepository;
import one.chandan.rubato.subsonic.models.AlbumID3;
import one.chandan.rubato.subsonic.models.ArtistID3;
import one.chandan.rubato.subsonic.models.ArtistInfo2;
import one.chandan.rubato.subsonic.models.Child;

import java.util.List;

public class ArtistPageViewModel extends AndroidViewModel {
    private final LibraryRepository libraryRepository;

    private ArtistID3 artist;

    public ArtistPageViewModel(@NonNull Application application) {
        super(application);

        libraryRepository = new LibraryRepository();
    }

    public LiveData<List<AlbumID3>> getAlbumList() {
        return libraryRepository.getArtistAlbums(artist);
    }

    public LiveData<ArtistInfo2> getArtistInfo(String id) {
        return libraryRepository.getArtistFullInfo(id);
    }

    public LiveData<List<Child>> getArtistTopSongList() {
        return libraryRepository.getArtistTopSongs(artist.getName(), 20);
    }

    public LiveData<List<Child>> getArtistShuffleList() {
        return libraryRepository.getArtistShuffleSongs(artist, 50);
    }

    public LiveData<List<Child>> getArtistInstantMix() {
        return libraryRepository.getArtistInstantMix(artist, 20);
    }

    public ArtistID3 getArtist() {
        return artist;
    }

    public void setArtist(ArtistID3 artist) {
        this.artist = artist;
    }
}
