package one.chandan.rubato.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import one.chandan.rubato.App;
import one.chandan.rubato.repository.CacheRepository;
import one.chandan.rubato.repository.JellyfinCacheRepository;
import one.chandan.rubato.repository.LocalMusicRepository;
import one.chandan.rubato.subsonic.base.ApiResponse;
import one.chandan.rubato.subsonic.models.ArtistID3;
import one.chandan.rubato.subsonic.models.IndexID3;
import one.chandan.rubato.util.NetworkUtil;
import one.chandan.rubato.util.LibraryDedupeUtil;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Type;

import retrofit2.Call;
import retrofit2.Callback;

public class ArtistCatalogueViewModel extends AndroidViewModel {
    private final MutableLiveData<List<ArtistID3>> artistList = new MutableLiveData<>(new ArrayList<>());
    private final CacheRepository cacheRepository = new CacheRepository();
    private final JellyfinCacheRepository jellyfinCacheRepository = new JellyfinCacheRepository();
    private final List<ArtistID3> remoteArtists = new ArrayList<>();

    public ArtistCatalogueViewModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<List<ArtistID3>> getArtistList() {
        return artistList;
    }

    public void loadArtists() {
        remoteArtists.clear();
        if (NetworkUtil.isOffline()) {
            Type type = new TypeToken<List<ArtistID3>>() {
            }.getType();
            cacheRepository.load("artists_all", type, new CacheRepository.CacheResult<List<ArtistID3>>() {
                @Override
                public void onLoaded(List<ArtistID3> artists) {
                    List<ArtistID3> base = artists != null ? artists : new ArrayList<>();
                    LocalMusicRepository.appendLocalArtists(getApplication(), base, merged -> mergeWithJellyfinArtists(merged));
                }
            });
            return;
        }

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getArtists()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull retrofit2.Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getArtists() != null) {
                            List<ArtistID3> artists = new ArrayList<>();

                            for (IndexID3 index : response.body().getSubsonicResponse().getArtists().getIndices()) {
                                artists.addAll(index.getArtists());
                            }

                            remoteArtists.addAll(artists);
                            LocalMusicRepository.appendLocalArtists(getApplication(), remoteArtists, merged -> mergeWithJellyfinArtists(merged));
                            cacheRepository.save("artists_all", artists);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {

                    }
                });
    }

    private void mergeWithJellyfinArtists(List<ArtistID3> base) {
        List<ArtistID3> snapshot = base != null ? base : new ArrayList<>();
        jellyfinCacheRepository.loadAllArtists(jellyfinArtists -> {
            List<ArtistID3> merged = LibraryDedupeUtil.mergeArtists(snapshot, jellyfinArtists);
            artistList.postValue(merged);
        });
    }
}
