package one.chandan.rubato.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import one.chandan.rubato.App;
import one.chandan.rubato.interfaces.MediaCallback;
import one.chandan.rubato.repository.CacheRepository;
import one.chandan.rubato.repository.JellyfinCacheRepository;
import one.chandan.rubato.repository.LocalMusicRepository;
import one.chandan.rubato.subsonic.base.ApiResponse;
import one.chandan.rubato.subsonic.models.AlbumID3;
import one.chandan.rubato.util.NetworkUtil;
import one.chandan.rubato.util.LibraryDedupeUtil;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Type;

import retrofit2.Call;
import retrofit2.Callback;

public class AlbumCatalogueViewModel extends AndroidViewModel {
    private final MutableLiveData<List<AlbumID3>> albumList = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(true);
    private final CacheRepository cacheRepository = new CacheRepository();
    private final JellyfinCacheRepository jellyfinCacheRepository = new JellyfinCacheRepository();
    private final List<AlbumID3> remoteAlbums = new ArrayList<>();

    private int page = 0;
    private Status status = Status.STOPPED;

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
        page = 0;
        status = Status.RUNNING;
        remoteAlbums.clear();
        albumList.setValue(new ArrayList<>());

        if (NetworkUtil.isOffline()) {
            Type type = new TypeToken<List<AlbumID3>>() {
            }.getType();
            cacheRepository.load("albums_all", type, new CacheRepository.CacheResult<List<AlbumID3>>() {
                @Override
                public void onLoaded(List<AlbumID3> albums) {
                    List<AlbumID3> base = albums != null ? albums : new ArrayList<>();
                    LocalMusicRepository.appendLocalAlbums(getApplication(), base, merged -> mergeWithJellyfinAlbums(merged));
                    loading.postValue(false);
                    status = Status.STOPPED;
                }
            });
            return;
        }

        loadAlbums(500);
    }

    public void stopLoading() {
        status = Status.STOPPED;
    }

    private void loadAlbums(int size) {
        retrieveAlbums(new MediaCallback() {
            @Override
            public void onError(Exception exception) {
            }

            @Override
            public void onLoadMedia(List<?> media) {
                if (status == Status.STOPPED) {
                    loading.setValue(false);
                    return;
                }

                remoteAlbums.addAll((List<AlbumID3>) media);
                cacheRepository.save("albums_all", remoteAlbums);
                LocalMusicRepository.appendLocalAlbums(getApplication(), remoteAlbums, merged -> mergeWithJellyfinAlbums(merged));

                if (media.size() == size) {
                    loadAlbums(size);
                    loading.setValue(true);
                } else {
                    status = Status.STOPPED;
                    loading.setValue(false);
                }
            }
        }, size, size * page++);
    }


    private void retrieveAlbums(MediaCallback callback, int size, int offset) {
        App.getSubsonicClientInstance(false)
                .getAlbumSongListClient()
                .getAlbumList2("alphabeticalByName", size, offset, null, null)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull retrofit2.Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getAlbumList2() != null && response.body().getSubsonicResponse().getAlbumList2().getAlbums() != null) {
                            List<AlbumID3> albumList = new ArrayList<>(response.body().getSubsonicResponse().getAlbumList2().getAlbums());
                            callback.onLoadMedia(albumList);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        callback.onError(new Exception(t.getMessage()));
                    }
                });
    }

    private void mergeWithJellyfinAlbums(List<AlbumID3> base) {
        List<AlbumID3> snapshot = base != null ? base : new ArrayList<>();
        jellyfinCacheRepository.loadAllAlbums(jellyfinAlbums -> {
            List<AlbumID3> merged = LibraryDedupeUtil.mergeAlbums(snapshot, jellyfinAlbums);
            albumList.postValue(merged);
        });
    }

    private enum Status {
        RUNNING,
        STOPPED
    }
}
