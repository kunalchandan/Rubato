package one.chandan.rubato.repository;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import one.chandan.rubato.App;
import one.chandan.rubato.database.AppDatabase;
import one.chandan.rubato.database.dao.PlaylistDao;
import one.chandan.rubato.subsonic.base.ApiResponse;
import one.chandan.rubato.subsonic.models.Child;
import one.chandan.rubato.subsonic.models.Playlist;
import one.chandan.rubato.util.OfflinePolicy;
import one.chandan.rubato.util.LibraryDedupeUtil;
import one.chandan.rubato.util.SearchIndexUtil;
import one.chandan.rubato.util.AppExecutors;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.lang.reflect.Type;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PlaylistRepository {
    private final PlaylistDao playlistDao = AppDatabase.getInstance().playlistDao();
    private final CacheRepository cacheRepository = new CacheRepository();
    private final JellyfinCacheRepository jellyfinCacheRepository = new JellyfinCacheRepository();
    private static final String CACHE_KEY_PLAYLISTS = "playlists";
    public MutableLiveData<List<Playlist>> getPlaylists(boolean random, int size) {
        MutableLiveData<List<Playlist>> listLivePlaylists = new MutableLiveData<>(new ArrayList<>());

        loadCachedPlaylists(listLivePlaylists, random, size);
        mergeWithJellyfinPlaylists(listLivePlaylists);

        if (OfflinePolicy.isOffline()) {
            return listLivePlaylists;
        }

        App.getSubsonicClientInstance(false)
                .getPlaylistClient()
                .getPlaylists()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getPlaylists() != null && response.body().getSubsonicResponse().getPlaylists().getPlaylists() != null) {
                            List<Playlist> playlists = response.body().getSubsonicResponse().getPlaylists().getPlaylists();

                            if (random) {
                                Collections.shuffle(playlists);
                                listLivePlaylists.setValue(playlists.subList(0, Math.min(playlists.size(), size)));
                            } else {
                                listLivePlaylists.setValue(playlists);
                            }

                            cacheRepository.save(CACHE_KEY_PLAYLISTS, playlists);
                            mergeWithJellyfinPlaylists(listLivePlaylists);
                            return;
                        }

                        loadCachedPlaylists(listLivePlaylists, random, size);
                        mergeWithJellyfinPlaylists(listLivePlaylists);
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        loadCachedPlaylists(listLivePlaylists, random, size);
                        mergeWithJellyfinPlaylists(listLivePlaylists);
                    }
                });

        return listLivePlaylists;
    }

    public MutableLiveData<List<Child>> getPlaylistSongs(String id) {
        MutableLiveData<List<Child>> listLivePlaylistSongs = new MutableLiveData<>();
        String cacheKey = "playlist_songs_" + id;

        if (SearchIndexUtil.isJellyfinTagged(id)) {
            jellyfinCacheRepository.loadPlaylistSongs(id, listLivePlaylistSongs::postValue);
            return listLivePlaylistSongs;
        }

        loadCachedPlaylistSongs(cacheKey, listLivePlaylistSongs);

        if (OfflinePolicy.isOffline()) {
            return listLivePlaylistSongs;
        }

        App.getSubsonicClientInstance(false)
                .getPlaylistClient()
                .getPlaylist(id)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getPlaylist() != null) {
                            List<Child> songs = response.body().getSubsonicResponse().getPlaylist().getEntries();
                            listLivePlaylistSongs.setValue(songs);
                            cacheRepository.save(cacheKey, songs);
                            return;
                        }

                        loadCachedPlaylistSongs(cacheKey, listLivePlaylistSongs);
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        loadCachedPlaylistSongs(cacheKey, listLivePlaylistSongs);
                    }
                });

        return listLivePlaylistSongs;
    }

    public void addSongToPlaylist(String playlistId, ArrayList<String> songsId) {
        if (OfflinePolicy.isOffline()) return;
        if (SearchIndexUtil.isJellyfinTagged(playlistId)) return;
        App.getSubsonicClientInstance(false)
                .getPlaylistClient()
                .updatePlaylist(playlistId, null, true, songsId, null)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {

                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {

                    }
                });
    }

    public void createPlaylist(String playlistId, String name, ArrayList<String> songsId) {
        if (OfflinePolicy.isOffline()) return;
        if (SearchIndexUtil.isJellyfinTagged(playlistId)) return;
        App.getSubsonicClientInstance(false)
                .getPlaylistClient()
                .createPlaylist(playlistId, name, songsId)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {

                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {

                    }
                });
    }

    public void updatePlaylist(String playlistId, String name, ArrayList<String> songsId) {
        if (OfflinePolicy.isOffline()) return;
        if (SearchIndexUtil.isJellyfinTagged(playlistId)) return;
        App.getSubsonicClientInstance(false)
                .getPlaylistClient()
                .deletePlaylist(playlistId)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        createPlaylist(null, name, songsId);
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {

                    }
                });
    }

    public void updatePlaylist(String playlistId, String name, boolean isPublic, ArrayList<String> songIdToAdd, ArrayList<Integer> songIndexToRemove) {
        if (OfflinePolicy.isOffline()) return;
        if (SearchIndexUtil.isJellyfinTagged(playlistId)) return;
        App.getSubsonicClientInstance(false)
                .getPlaylistClient()
                .updatePlaylist(playlistId, name, isPublic, songIdToAdd, songIndexToRemove)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {

                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {

                    }
                });
    }

    public void deletePlaylist(String playlistId) {
        if (OfflinePolicy.isOffline()) return;
        if (SearchIndexUtil.isJellyfinTagged(playlistId)) return;
        App.getSubsonicClientInstance(false)
                .getPlaylistClient()
                .deletePlaylist(playlistId)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {

                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {

                    }
                });
    }

    public LiveData<List<Playlist>> getPinnedPlaylists() {
        return playlistDao.getAll();
    }

    private void loadCachedPlaylists(MutableLiveData<List<Playlist>> listLivePlaylists, boolean random, int size) {
        List<Playlist> current = listLivePlaylists.getValue();
        if (current != null && !current.isEmpty()) {
            return;
        }
        Type type = new TypeToken<List<Playlist>>() {
        }.getType();
        cacheRepository.load(CACHE_KEY_PLAYLISTS, type, new CacheRepository.CacheResult<List<Playlist>>() {
            @Override
            public void onLoaded(List<Playlist> playlists) {
                if (playlists == null) return;
                if (random) {
                    Collections.shuffle(playlists);
                    listLivePlaylists.postValue(playlists.subList(0, Math.min(playlists.size(), size)));
                } else {
                    listLivePlaylists.postValue(playlists);
                }
            }
        });
    }

    private void mergeWithJellyfinPlaylists(MutableLiveData<List<Playlist>> listLivePlaylists) {
        jellyfinCacheRepository.loadAllPlaylists(jellyfinPlaylists -> {
            List<Playlist> base = listLivePlaylists.getValue();
            List<Playlist> merged = LibraryDedupeUtil.mergePlaylists(base != null ? base : new ArrayList<>(), jellyfinPlaylists);
            listLivePlaylists.postValue(merged);
        });
    }

    private void loadCachedPlaylistSongs(String cacheKey, MutableLiveData<List<Child>> listLivePlaylistSongs) {
        Type type = new TypeToken<List<Child>>() {
        }.getType();
        cacheRepository.load(cacheKey, type, new CacheRepository.CacheResult<List<Child>>() {
            @Override
            public void onLoaded(List<Child> songs) {
                listLivePlaylistSongs.postValue(songs);
            }
        });
    }

    public void insert(Playlist playlist) {
        AppExecutors.io().execute(() -> playlistDao.insert(playlist));
    }

    public void delete(Playlist playlist) {
        AppExecutors.io().execute(() -> playlistDao.delete(playlist));
    }
}
