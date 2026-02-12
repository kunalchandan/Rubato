package one.chandan.rubato.jellyfin;

import androidx.annotation.Nullable;

import one.chandan.rubato.jellyfin.model.JellyfinAuthRequest;
import one.chandan.rubato.jellyfin.model.JellyfinAuthResponse;
import one.chandan.rubato.jellyfin.model.JellyfinView;
import one.chandan.rubato.jellyfin.model.JellyfinViewsResponse;
import one.chandan.rubato.util.Preferences;
import com.google.gson.GsonBuilder;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class JellyfinAuthRepository {
    public interface AuthCallback {
        void onSuccess(JellyfinAuthResponse response);
        void onError(Exception exception);
    }

    public interface ViewsCallback {
        void onSuccess(List<JellyfinView> views);
        void onError(Exception exception);
    }

    public void authenticate(String serverUrl, String username, String password, AuthCallback callback) {
        JellyfinApi api = createApi(serverUrl);
        JellyfinAuthRequest request = new JellyfinAuthRequest(username, password);
        api.authenticate(request, JellyfinAuthUtil.buildAuthHeader())
                .enqueue(new Callback<JellyfinAuthResponse>() {
                    @Override
                    public void onResponse(Call<JellyfinAuthResponse> call, Response<JellyfinAuthResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            callback.onSuccess(response.body());
                        } else {
                            callback.onError(new IllegalStateException("Authentication failed"));
                        }
                    }

                    @Override
                    public void onFailure(Call<JellyfinAuthResponse> call, Throwable t) {
                        callback.onError(new Exception(t));
                    }
                });
    }

    public void fetchLibraries(String serverUrl, String userId, String token, ViewsCallback callback) {
        JellyfinApi api = createApi(serverUrl);
        api.getViews(userId, token).enqueue(new Callback<JellyfinViewsResponse>() {
            @Override
            public void onResponse(Call<JellyfinViewsResponse> call, Response<JellyfinViewsResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body().getItems());
                } else {
                    callback.onError(new IllegalStateException("Failed to load libraries"));
                }
            }

            @Override
            public void onFailure(Call<JellyfinViewsResponse> call, Throwable t) {
                callback.onError(new Exception(t));
            }
        });
    }

    private JellyfinApi createApi(String serverUrl) {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(normalizeServerUrl(serverUrl))
                .addConverterFactory(GsonConverterFactory.create(
                        new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss").create()))
                .client(createClient())
                .build();

        return retrofit.create(JellyfinApi.class);
    }

    private OkHttpClient createClient() {
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

        return new OkHttpClient.Builder()
                .callTimeout(2, TimeUnit.MINUTES)
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(loggingInterceptor)
                .build();
    }

    private String normalizeServerUrl(@Nullable String input) {
        if (input == null || input.trim().isEmpty()) {
            return "https://";
        }
        String trimmed = input.trim();
        if (trimmed.startsWith("http://") && !Preferences.isLowScurity()) {
            trimmed = "https://" + trimmed.substring("http://".length());
        }
        return trimmed.endsWith("/") ? trimmed : trimmed + "/";
    }
}
