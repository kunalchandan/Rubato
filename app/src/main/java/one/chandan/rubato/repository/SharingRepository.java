package one.chandan.rubato.repository;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import one.chandan.rubato.App;
import one.chandan.rubato.subsonic.base.ApiResponse;
import one.chandan.rubato.subsonic.models.Share;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Type;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SharingRepository {
    private final CacheRepository cacheRepository = new CacheRepository();
    private static final String CACHE_KEY_SHARES = "shares";
    public MutableLiveData<List<Share>> getShares() {
        MutableLiveData<List<Share>> shares = new MutableLiveData<>(new ArrayList<>());

        loadCachedShares(shares);

        App.getSubsonicClientInstance(false)
                .getSharingClient()
                .getShares()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getShares() != null && response.body().getSubsonicResponse().getShares().getShares() != null) {
                            List<Share> responseShares = response.body().getSubsonicResponse().getShares().getShares();
                            shares.setValue(responseShares);
                            cacheRepository.save(CACHE_KEY_SHARES, responseShares);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {

                    }
                });

        return shares;
    }

    private void loadCachedShares(MutableLiveData<List<Share>> shares) {
        List<Share> current = shares.getValue();
        if (current != null && !current.isEmpty()) {
            return;
        }
        Type type = new TypeToken<List<Share>>() {
        }.getType();
        cacheRepository.load(CACHE_KEY_SHARES, type, shares::postValue);
    }

    public MutableLiveData<Share> createShare(String id, String description, Long expires) {
        MutableLiveData<Share> share = new MutableLiveData<>();

        App.getSubsonicClientInstance(false)
                .getSharingClient()
                .createShare(id, description, expires)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getShares() != null && response.body().getSubsonicResponse().getShares().getShares() != null && response.body().getSubsonicResponse().getShares().getShares().get(0) != null) {
                            share.setValue(response.body().getSubsonicResponse().getShares().getShares().get(0));
                        } else {
                            share.setValue(null);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        share.setValue(null);
                    }
                });

        return share;
    }

    public void updateShare(String id, String description, Long expires) {
        App.getSubsonicClientInstance(false)
                .getSharingClient()
                .updateShare(id, description, expires)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {

                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {

                    }
                });
    }

    public void deleteShare(String id) {
        App.getSubsonicClientInstance(false)
                .getSharingClient()
                .deleteShare(id)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {

                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {

                    }
                });
    }
}
