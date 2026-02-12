package one.chandan.rubato.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import one.chandan.rubato.repository.LibraryRepository;
import one.chandan.rubato.subsonic.models.AlbumID3;
import one.chandan.rubato.subsonic.models.ArtistID3;
import one.chandan.rubato.subsonic.models.Child;

public class RatingViewModel extends AndroidViewModel {
    private final LibraryRepository libraryRepository;

    private Child song;
    private AlbumID3 album;
    private ArtistID3 artist;

    public RatingViewModel(@NonNull Application application) {
        super(application);

        libraryRepository = new LibraryRepository();
    }

    public Child getSong() {
        return song;
    }

    public LiveData<Child> getLiveSong() {
        return libraryRepository.getSong(song.getId());
    }

    public void setSong(Child song) {
        this.song = song;
        this.album = null;
        this.artist = null;
    }

    public AlbumID3 getAlbum() {
        return album;
    }

    public LiveData<AlbumID3> getLiveAlbum() {
        return libraryRepository.getAlbum(album.getId());
    }

    public void setAlbum(AlbumID3 album) {
        this.song = null;
        this.album = album;
        this.artist = null;
    }

    public ArtistID3 getArtist() {
        return artist;
    }

    public LiveData<ArtistID3> getLiveArtist() {
        return libraryRepository.getArtistInfo(artist.getId());
    }

    public void setArtist(ArtistID3 artist) {
        this.song = null;
        this.album = null;
        this.artist = artist;
    }

    public void rate(int star) {
        if (song != null) {
            libraryRepository.setSongRating(song.getId(), star);
        } else if (album != null) {
            libraryRepository.setAlbumRating(album.getId(), star);
        } else if (artist != null) {
            libraryRepository.setArtistRating(artist.getId(), star);
        }
    }
}
