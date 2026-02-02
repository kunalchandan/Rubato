package one.chandan.rubato.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import one.chandan.rubato.interfaces.StarCallback;
import one.chandan.rubato.repository.AlbumRepository;
import one.chandan.rubato.repository.ArtistRepository;
import one.chandan.rubato.repository.FavoriteRepository;
import one.chandan.rubato.repository.SharingRepository;
import one.chandan.rubato.subsonic.models.AlbumID3;
import one.chandan.rubato.subsonic.models.ArtistID3;
import one.chandan.rubato.subsonic.models.Child;
import one.chandan.rubato.subsonic.models.Share;
import one.chandan.rubato.util.NetworkUtil;

import java.util.Date;
import java.util.List;

public class AlbumBottomSheetViewModel extends AndroidViewModel {
    private final AlbumRepository albumRepository;
    private final ArtistRepository artistRepository;
    private final FavoriteRepository favoriteRepository;
    private final SharingRepository sharingRepository;

    private AlbumID3 album;

    public AlbumBottomSheetViewModel(@NonNull Application application) {
        super(application);

        albumRepository = new AlbumRepository();
        artistRepository = new ArtistRepository();
        favoriteRepository = new FavoriteRepository();
        sharingRepository = new SharingRepository();
    }

    public AlbumID3 getAlbum() {
        return album;
    }

    public void setAlbum(AlbumID3 album) {
        this.album = album;
    }

    public LiveData<ArtistID3> getArtist() {
        return artistRepository.getArtist(album.getArtistId());
    }

    public MutableLiveData<List<Child>> getAlbumTracks() {
        return albumRepository.getAlbumTracks(album.getId());
    }

    public void setFavorite() {
        if (album.getStarred() != null) {
            if (NetworkUtil.isOffline()) {
                removeFavoriteOffline();
            } else {
                removeFavoriteOnline();
            }
        } else {
            if (NetworkUtil.isOffline()) {
                setFavoriteOffline();
            } else {
                setFavoriteOnline();
            }
        }
    }

    public MutableLiveData<Share> shareAlbum() {
        return sharingRepository.createShare(album.getId(), album.getName(), null);
    }

    private void removeFavoriteOffline() {
        favoriteRepository.starLater(null, album.getId(), null, false);
        album.setStarred(null);
    }

    private void removeFavoriteOnline() {
        favoriteRepository.unstar(null, album.getId(), null, new StarCallback() {
            @Override
            public void onError() {
                // album.setStarred(new Date());
                favoriteRepository.starLater(null, album.getId(), null, false);
            }
        });

        album.setStarred(null);
    }

    private void setFavoriteOffline() {
        favoriteRepository.starLater(null, album.getId(), null, true);
        album.setStarred(new Date());
    }

    private void setFavoriteOnline() {
        favoriteRepository.star(null, album.getId(), null, new StarCallback() {
            @Override
            public void onError() {
                // album.setStarred(null);
                favoriteRepository.starLater(null, album.getId(), null, true);
            }
        });

        album.setStarred(new Date());
    }
}
