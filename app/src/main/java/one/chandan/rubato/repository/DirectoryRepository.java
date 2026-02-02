package one.chandan.rubato.repository;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import one.chandan.rubato.App;
import one.chandan.rubato.subsonic.base.ApiResponse;
import one.chandan.rubato.subsonic.models.Directory;
import one.chandan.rubato.subsonic.models.Indexes;
import one.chandan.rubato.subsonic.models.MusicFolder;
import one.chandan.rubato.util.NetworkUtil;
import com.google.gson.reflect.TypeToken;

import java.util.List;
import java.lang.reflect.Type;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DirectoryRepository {
    private static final String TAG = "DirectoryRepository";
    private final CacheRepository cacheRepository = new CacheRepository();

    public MutableLiveData<List<MusicFolder>> getMusicFolders() {
        MutableLiveData<List<MusicFolder>> liveMusicFolders = new MutableLiveData<>();
        String cacheKey = "music_folders";

        if (NetworkUtil.isOffline()) {
            loadCachedMusicFolders(cacheKey, liveMusicFolders);
            return liveMusicFolders;
        }

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getMusicFolders()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getMusicFolders() != null) {
                            List<MusicFolder> folders = response.body().getSubsonicResponse().getMusicFolders().getMusicFolders();
                            liveMusicFolders.setValue(folders);
                            cacheRepository.save(cacheKey, folders);
                            return;
                        }

                        loadCachedMusicFolders(cacheKey, liveMusicFolders);
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        loadCachedMusicFolders(cacheKey, liveMusicFolders);

                    }
                });

        return liveMusicFolders;
    }

    public MutableLiveData<Indexes> getIndexes(String musicFolderId, Long ifModifiedSince) {
        MutableLiveData<Indexes> liveIndexes = new MutableLiveData<>();
        String cacheKey = "indexes_" + musicFolderId;

        if (NetworkUtil.isOffline()) {
            loadCachedIndexes(cacheKey, liveIndexes);
            return liveIndexes;
        }

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getIndexes(musicFolderId, ifModifiedSince)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getIndexes() != null) {
                            Indexes indexes = response.body().getSubsonicResponse().getIndexes();
                            liveIndexes.setValue(indexes);
                            cacheRepository.save(cacheKey, indexes);
                            return;
                        }

                        loadCachedIndexes(cacheKey, liveIndexes);
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        loadCachedIndexes(cacheKey, liveIndexes);

                    }
                });

        return liveIndexes;
    }

    public MutableLiveData<Directory> getMusicDirectory(String id) {
        MutableLiveData<Directory> liveMusicDirectory = new MutableLiveData<>();
        String cacheKey = "music_directory_" + id;

        if (NetworkUtil.isOffline()) {
            loadCachedDirectory(cacheKey, liveMusicDirectory);
            return liveMusicDirectory;
        }

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getMusicDirectory(id)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getDirectory() != null) {
                            Directory directory = response.body().getSubsonicResponse().getDirectory();
                            liveMusicDirectory.setValue(directory);
                            cacheRepository.save(cacheKey, directory);
                            return;
                        }

                        loadCachedDirectory(cacheKey, liveMusicDirectory);
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        loadCachedDirectory(cacheKey, liveMusicDirectory);
                    }
                });

        return liveMusicDirectory;
    }

    private void loadCachedMusicFolders(String cacheKey, MutableLiveData<List<MusicFolder>> liveMusicFolders) {
        Type type = new TypeToken<List<MusicFolder>>() {
        }.getType();
        cacheRepository.load(cacheKey, type, new CacheRepository.CacheResult<List<MusicFolder>>() {
            @Override
            public void onLoaded(List<MusicFolder> musicFolders) {
                liveMusicFolders.postValue(musicFolders);
            }
        });
    }

    private void loadCachedIndexes(String cacheKey, MutableLiveData<Indexes> liveIndexes) {
        cacheRepository.load(cacheKey, Indexes.class, new CacheRepository.CacheResult<Indexes>() {
            @Override
            public void onLoaded(Indexes indexes) {
                liveIndexes.postValue(indexes);
            }
        });
    }

    private void loadCachedDirectory(String cacheKey, MutableLiveData<Directory> liveMusicDirectory) {
        cacheRepository.load(cacheKey, Directory.class, new CacheRepository.CacheResult<Directory>() {
            @Override
            public void onLoaded(Directory directory) {
                liveMusicDirectory.postValue(directory);
            }
        });
    }
}
