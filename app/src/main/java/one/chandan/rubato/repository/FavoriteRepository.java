package one.chandan.rubato.repository;

import androidx.annotation.NonNull;

import one.chandan.rubato.App;
import one.chandan.rubato.database.AppDatabase;
import one.chandan.rubato.database.dao.FavoriteDao;
import one.chandan.rubato.interfaces.StarCallback;
import one.chandan.rubato.model.Favorite;
import one.chandan.rubato.subsonic.base.ApiResponse;
import one.chandan.rubato.util.AppExecutors;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class FavoriteRepository {
    private final FavoriteDao favoriteDao = AppDatabase.getInstance().favoriteDao();

    public void star(String id, String albumId, String artistId, StarCallback starCallback) {
        App.getSubsonicClientInstance(false)
                .getMediaAnnotationClient()
                .star(id, albumId, artistId)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful()) {
                            starCallback.onSuccess();
                        } else {
                            starCallback.onError();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        starCallback.onError();
                    }
                });
    }

    public void unstar(String id, String albumId, String artistId, StarCallback starCallback) {
        App.getSubsonicClientInstance(false)
                .getMediaAnnotationClient()
                .unstar(id, albumId, artistId)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful()) {
                            starCallback.onSuccess();
                        } else {
                            starCallback.onError();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        starCallback.onError();
                    }
                });
    }

    public List<Favorite> getFavorites() {
        Future<List<Favorite>> future = AppExecutors.io().submit(favoriteDao::getAll);
        try {
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
        }
        return new ArrayList<>();
    }

    public void starLater(String id, String albumId, String artistId, boolean toStar) {
        Favorite favorite = new Favorite(System.currentTimeMillis(), id, albumId, artistId, toStar);
        AppExecutors.io().execute(() -> favoriteDao.insert(favorite));
    }

    public void delete(Favorite favorite) {
        AppExecutors.io().execute(() -> favoriteDao.delete(favorite));
    }
}
