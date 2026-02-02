package one.chandan.rubato.repository;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import one.chandan.rubato.App;
import one.chandan.rubato.subsonic.base.ApiResponse;
import one.chandan.rubato.subsonic.models.LyricsList;
import one.chandan.rubato.util.NetworkUtil;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class OpenRepository {
    private final CacheRepository cacheRepository = new CacheRepository();

    public MutableLiveData<LyricsList> getLyricsBySongId(String id) {
        MutableLiveData<LyricsList> lyricsList = new MutableLiveData<>();
        String cacheKey = "lyrics_song_" + id;

        if (NetworkUtil.isOffline()) {
            loadCachedLyrics(cacheKey, lyricsList);
            return lyricsList;
        }

        loadCachedLyrics(cacheKey, lyricsList);

        App.getSubsonicClientInstance(false)
                .getOpenClient()
                .getLyricsBySongId(id)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getLyricsList() != null) {
                            LyricsList result = response.body().getSubsonicResponse().getLyricsList();
                            lyricsList.setValue(result);
                            cacheRepository.save(cacheKey, result);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {

                    }
                });

        return lyricsList;
    }

    private void loadCachedLyrics(String cacheKey, MutableLiveData<LyricsList> lyricsList) {
        cacheRepository.load(cacheKey, LyricsList.class, new CacheRepository.CacheResult<LyricsList>() {
            @Override
            public void onLoaded(LyricsList cached) {
                lyricsList.postValue(cached);
            }
        });
    }
}
