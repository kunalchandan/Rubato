package one.chandan.rubato.viewmodel;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.media3.common.util.UnstableApi;

import one.chandan.rubato.interfaces.StarCallback;
import one.chandan.rubato.model.Download;
import one.chandan.rubato.repository.FavoriteRepository;
import one.chandan.rubato.repository.LibraryRepository;
import one.chandan.rubato.repository.SharingRepository;
import one.chandan.rubato.subsonic.models.AlbumID3;
import one.chandan.rubato.subsonic.models.ArtistID3;
import one.chandan.rubato.subsonic.models.Child;
import one.chandan.rubato.subsonic.models.Share;
import one.chandan.rubato.util.DownloadUtil;
import one.chandan.rubato.util.MappingUtil;
import one.chandan.rubato.util.NetworkUtil;
import one.chandan.rubato.util.Preferences;
import one.chandan.rubato.util.CollectionUtil;

import java.util.Collections;
import java.util.Date;
import java.util.List;

@UnstableApi
public class SongBottomSheetViewModel extends AndroidViewModel {
    private final LibraryRepository libraryRepository;
    private final FavoriteRepository favoriteRepository;
    private final SharingRepository sharingRepository;

    private Child song;

    private final MutableLiveData<List<Child>> instantMix = new MutableLiveData<>(Collections.emptyList());

    public SongBottomSheetViewModel(@NonNull Application application) {
        super(application);

        libraryRepository = new LibraryRepository();
        favoriteRepository = new FavoriteRepository();
        sharingRepository = new SharingRepository();
    }

    public Child getSong() {
        return song;
    }

    public void setSong(Child song) {
        this.song = song;
    }

    public void setFavorite(Context context) {
        if (song.getStarred() != null) {
            if (NetworkUtil.isOffline()) {
                removeFavoriteOffline(song);
            } else {
                removeFavoriteOnline(song);
            }
        } else {
            if (NetworkUtil.isOffline()) {
                setFavoriteOffline(song);
            } else {
                setFavoriteOnline(context, song);
            }
        }
    }

    private void removeFavoriteOffline(Child media) {
        favoriteRepository.starLater(media.getId(), null, null, false);
        media.setStarred(null);
    }

    private void removeFavoriteOnline(Child media) {
        favoriteRepository.unstar(media.getId(), null, null, new StarCallback() {
            @Override
            public void onError() {
                // media.setStarred(new Date());
                favoriteRepository.starLater(media.getId(), null, null, false);
            }
        });

        media.setStarred(null);
    }

    private void setFavoriteOffline(Child media) {
        favoriteRepository.starLater(media.getId(), null, null, true);
        media.setStarred(new Date());
    }

    private void setFavoriteOnline(Context context, Child media) {
        favoriteRepository.star(media.getId(), null, null, new StarCallback() {
            @Override
            public void onError() {
                // media.setStarred(null);
                favoriteRepository.starLater(media.getId(), null, null, true);
            }
        });

        media.setStarred(new Date());

        if (Preferences.isStarredSyncEnabled()) {
            DownloadUtil.getDownloadTracker(context).download(
                    MappingUtil.mapDownload(media),
                    new Download(media)
            );
        }
    }

    public LiveData<AlbumID3> getAlbum() {
        return libraryRepository.getAlbum(song.getAlbumId());
    }

    public LiveData<ArtistID3> getArtist() {
        return libraryRepository.getArtistInfo(song.getArtistId());
    }

    public LiveData<List<Child>> getInstantMix(LifecycleOwner owner, Child media) {
        instantMix.setValue(Collections.emptyList());

        libraryRepository.getSongInstantMix(media.getId(), 20)
                .observe(owner, items -> instantMix.postValue(CollectionUtil.arrayListOrEmpty(items)));

        return instantMix;
    }

    public MutableLiveData<Share> shareTrack() {
        return sharingRepository.createShare(song.getId(), song.getTitle(), null);
    }
}
