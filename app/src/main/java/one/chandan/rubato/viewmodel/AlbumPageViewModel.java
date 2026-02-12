package one.chandan.rubato.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import one.chandan.rubato.interfaces.StarCallback;
import one.chandan.rubato.repository.FavoriteRepository;
import one.chandan.rubato.repository.LibraryRepository;
import one.chandan.rubato.subsonic.models.AlbumID3;
import one.chandan.rubato.subsonic.models.AlbumInfo;
import one.chandan.rubato.subsonic.models.ArtistID3;
import one.chandan.rubato.subsonic.models.Child;
import one.chandan.rubato.util.NetworkUtil;

import java.util.Date;
import java.util.List;

public class AlbumPageViewModel extends AndroidViewModel {
    private final LibraryRepository libraryRepository;
    private final FavoriteRepository favoriteRepository;
    private String albumId;
    private final MutableLiveData<AlbumID3> album = new MutableLiveData<>(null);
    private LiveData<List<Child>> albumTracks;

    public AlbumPageViewModel(@NonNull Application application) {
        super(application);

        libraryRepository = new LibraryRepository();
        favoriteRepository = new FavoriteRepository();
    }

    public LiveData<List<Child>> getAlbumSongLiveList() {
        if (albumTracks != null) {
            return albumTracks;
        }
        AlbumID3 current = album.getValue();
        if (current != null) {
            albumTracks = libraryRepository.getAlbumTracks(current);
            return albumTracks;
        }
        albumTracks = libraryRepository.getAlbumTracks(albumId);
        return albumTracks;
    }

    public MutableLiveData<AlbumID3> getAlbum() {
        return album;
    }

    public void setAlbum(LifecycleOwner owner, AlbumID3 album) {
        if (album == null) {
            this.albumId = null;
            this.album.setValue(null);
            albumTracks = libraryRepository.getAlbumTracks((String) null);
            return;
        }

        this.albumId = album.getId();
        this.album.setValue(album);
        albumTracks = libraryRepository.getAlbumTracks(album);

        libraryRepository.getAlbum(album.getId()).observe(owner, albums -> {
            if (albums != null) this.album.setValue(albums);
        });
    }

    public LiveData<ArtistID3> getArtist() {
        return libraryRepository.getArtistInfo(albumId);
    }

    public LiveData<AlbumInfo> getAlbumInfo() {
        return libraryRepository.getAlbumInfo(albumId);
    }

    public boolean toggleFavorite() {
        AlbumID3 current = album.getValue();
        if (current == null) return false;

        if (current.getStarred() != null) {
            if (NetworkUtil.isOffline()) {
                favoriteRepository.starLater(null, current.getId(), null, false);
            } else {
                favoriteRepository.unstar(null, current.getId(), null, new StarCallback() {
                    @Override
                    public void onError() {
                        favoriteRepository.starLater(null, current.getId(), null, false);
                    }
                });
            }
            current.setStarred(null);
            album.setValue(current);
            return false;
        }

        if (NetworkUtil.isOffline()) {
            favoriteRepository.starLater(null, current.getId(), null, true);
        } else {
            favoriteRepository.star(null, current.getId(), null, new StarCallback() {
                @Override
                public void onError() {
                    favoriteRepository.starLater(null, current.getId(), null, true);
                }
            });
        }

        current.setStarred(new Date());
        album.setValue(current);
        return true;
    }
}
