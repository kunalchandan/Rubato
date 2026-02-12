package one.chandan.rubato.repository;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import one.chandan.rubato.subsonic.base.ApiResponse;
import one.chandan.rubato.subsonic.models.Child;
import one.chandan.rubato.subsonic.models.SubsonicResponse;
import one.chandan.rubato.util.OfflinePolicy;
import one.chandan.rubato.App;
import one.chandan.rubato.repository.LocalMusicRepository;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.lang.reflect.Type;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SongRepository {
    private static final String TAG = "SongRepository";
    private final CacheRepository cacheRepository = new CacheRepository();

    public MutableLiveData<List<Child>> getStarredSongs(boolean random, int size) {
        MutableLiveData<List<Child>> starredSongs = new MutableLiveData<>(Collections.emptyList());
        String cacheKey = "starred_songs";

        loadCachedSongs(cacheKey, starredSongs, random, size);

        if (OfflinePolicy.isOffline()) {
            return starredSongs;
        }

        App.getSubsonicClientInstance(false)
                .getAlbumSongListClient()
                .getStarred2()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            SubsonicResponse subsonicResponse = response.body().getSubsonicResponse();
                            List<Child> songs = null;

                            if (subsonicResponse != null && subsonicResponse.getStarred2() != null) {
                                songs = subsonicResponse.getStarred2().getSongs();
                            }

                            if (songs == null && subsonicResponse != null && subsonicResponse.getStarred() != null) {
                                songs = subsonicResponse.getStarred().getSongs();
                            }

                            if (songs != null) {
                                List<Child> safeSongs = new ArrayList<>(songs);
                                if (!random) {
                                    starredSongs.setValue(safeSongs);
                                } else {
                                    Collections.shuffle(safeSongs);
                                    int limit = Math.min(size, safeSongs.size());
                                    starredSongs.setValue(new ArrayList<>(safeSongs.subList(0, limit)));
                                }

                                cacheRepository.save(cacheKey, safeSongs);
                                return;
                            }
                        }

                        loadCachedSongs(cacheKey, starredSongs, random, size);
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        loadCachedSongs(cacheKey, starredSongs, random, size);

                    }
                });

        return starredSongs;
    }

    public MutableLiveData<List<Child>> getInstantMix(String id, int count) {
        MutableLiveData<List<Child>> instantMix = new MutableLiveData<>();
        String cacheKey = "instant_mix_" + id + "_" + count;

        if (OfflinePolicy.isOffline()) {
            loadCachedSongs(cacheKey, instantMix);
            return instantMix;
        }

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getSimilarSongs2(id, count)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getSimilarSongs2() != null) {
                            List<Child> songs = response.body().getSubsonicResponse().getSimilarSongs2().getSongs();
                            instantMix.setValue(songs);
                            cacheRepository.save(cacheKey, songs);
                            return;
                        }

                        loadCachedSongs(cacheKey, instantMix);
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        loadCachedSongs(cacheKey, instantMix);
                    }
                });

        return instantMix;
    }

    public MutableLiveData<List<Child>> getRandomSample(int number, Integer fromYear, Integer toYear) {
        MutableLiveData<List<Child>> randomSongsSample = new MutableLiveData<>();
        String cacheKey = "random_sample_" + number + "_" + fromYear + "_" + toYear;

        loadCachedSongs(cacheKey, randomSongsSample);
        loadCachedSongsByYearRange(fromYear, toYear, number, randomSongsSample);

        if (OfflinePolicy.isOffline()) {
            loadCachedSongsByYearRange(fromYear, toYear, number, randomSongsSample);
            return randomSongsSample;
        }

        App.getSubsonicClientInstance(false)
                .getAlbumSongListClient()
                .getRandomSongs(number, fromYear, toYear)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        List<Child> songs = new ArrayList<>();

                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getRandomSongs() != null && response.body().getSubsonicResponse().getRandomSongs().getSongs() != null) {
                            songs.addAll(response.body().getSubsonicResponse().getRandomSongs().getSongs());
                        }

                        mergeLocalByYearRange(fromYear, toYear, number, songs, randomSongsSample);
                        cacheRepository.save(cacheKey, songs);
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        loadCachedSongs(cacheKey, randomSongsSample);

                    }
                });

        return randomSongsSample;
    }

    public void scrobble(String id, boolean submission) {
        App.getSubsonicClientInstance(false)
                .getMediaAnnotationClient()
                .scrobble(id, submission)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {

                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {

                    }
                });
    }

    public void setRating(String id, int rating) {
        if (OfflinePolicy.isOffline()) return;
        App.getSubsonicClientInstance(false)
                .getMediaAnnotationClient()
                .setRating(id, rating)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {

                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {

                    }
                });
    }

    public MutableLiveData<List<Child>> getSongsByGenre(String id, int page) {
        MutableLiveData<List<Child>> songsByGenre = new MutableLiveData<>();
        String cacheKey = "songs_by_genre_" + id + "_" + page;

        if (OfflinePolicy.isOffline()) {
            loadCachedSongsByGenre(id, page, songsByGenre);
            return songsByGenre;
        }

        App.getSubsonicClientInstance(false)
                .getAlbumSongListClient()
                .getSongsByGenre(id, 100, 100 * page)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getSongsByGenre() != null) {
                            List<Child> songs = response.body().getSubsonicResponse().getSongsByGenre().getSongs();
                            mergeLocalByGenre(id, songs, songsByGenre);
                            cacheRepository.save(cacheKey, songs);
                            return;
                        }

                        loadCachedSongs(cacheKey, songsByGenre);
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        loadCachedSongs(cacheKey, songsByGenre);

                    }
                });

        return songsByGenre;
    }

    public MutableLiveData<List<Child>> getSongsByGenres(ArrayList<String> genresId) {
        MutableLiveData<List<Child>> songsByGenre = new MutableLiveData<>();
        StringBuilder keyBuilder = new StringBuilder("songs_by_genres");
        for (String genreId : genresId) {
            keyBuilder.append("_").append(genreId);
        }
        String cacheKey = keyBuilder.toString();

        if (OfflinePolicy.isOffline()) {
            loadCachedSongsByGenres(genresId, songsByGenre);
            return songsByGenre;
        }

        for (String id : genresId)
            App.getSubsonicClientInstance(false)
                    .getAlbumSongListClient()
                    .getSongsByGenre(id, 500, 0)
                    .enqueue(new Callback<ApiResponse>() {
                        @Override
                        public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                            List<Child> songs = new ArrayList<>();

                            if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getSongsByGenre() != null) {
                                songs.addAll(response.body().getSubsonicResponse().getSongsByGenre().getSongs());
                            }

                            mergeLocalByGenres(genresId, songs, songsByGenre);
                            cacheRepository.save(cacheKey, songs);
                        }

                        @Override
                        public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                            loadCachedSongs(cacheKey, songsByGenre);

                        }
                    });

        return songsByGenre;
    }

    public MutableLiveData<Child> getSong(String id) {
        MutableLiveData<Child> song = new MutableLiveData<>();
        String cacheKey = "song_" + id;

        if (LocalMusicRepository.isLocalSongId(id)) {
            LocalMusicRepository.getLocalSong(App.getContext(), id, song::postValue);
            return song;
        }

        if (OfflinePolicy.isOffline()) {
            loadCachedSong(cacheKey, song);
            loadCachedSongFromAll(id, song);
            return song;
        }

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getSong(id)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            Child songResponse = response.body().getSubsonicResponse().getSong();
                            song.setValue(songResponse);
                            cacheRepository.save(cacheKey, songResponse);
                            return;
                        }

                        loadCachedSong(cacheKey, song);
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        loadCachedSong(cacheKey, song);

                    }
                });

        return song;
    }

    public MutableLiveData<String> getSongLyrics(Child song) {
        MutableLiveData<String> lyrics = new MutableLiveData<>(null);

        App.getSubsonicClientInstance(false)
                .getMediaRetrievalClient()
                .getLyrics(song.getArtist(), song.getTitle())
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getLyrics() != null) {
                            lyrics.setValue(response.body().getSubsonicResponse().getLyrics().getValue());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {

                    }
                });

        return lyrics;
    }

    private void loadCachedSongs(String cacheKey, MutableLiveData<List<Child>> songs) {
        List<Child> current = songs.getValue();
        if (current != null && !current.isEmpty()) {
            return;
        }
        Type type = new TypeToken<List<Child>>() {
        }.getType();
        cacheRepository.load(cacheKey, type, new CacheRepository.CacheResult<List<Child>>() {
            @Override
            public void onLoaded(List<Child> cachedSongs) {
                songs.postValue(cachedSongs);
            }
        });
    }

    private void loadCachedSongs(String cacheKey, MutableLiveData<List<Child>> songs, boolean random, int size) {
        List<Child> current = songs.getValue();
        if (current != null && !current.isEmpty()) {
            return;
        }
        Type type = new TypeToken<List<Child>>() {
        }.getType();
        cacheRepository.load(cacheKey, type, new CacheRepository.CacheResult<List<Child>>() {
            @Override
            public void onLoaded(List<Child> cachedSongs) {
                if (cachedSongs == null) return;
                if (random) {
                    List<Child> safeSongs = new ArrayList<>(cachedSongs);
                    Collections.shuffle(safeSongs);
                    int limit = Math.min(size, safeSongs.size());
                    songs.postValue(new ArrayList<>(safeSongs.subList(0, limit)));
                } else {
                    songs.postValue(new ArrayList<>(cachedSongs));
                }
            }
        });
    }

    private void loadCachedSong(String cacheKey, MutableLiveData<Child> song) {
        cacheRepository.load(cacheKey, Child.class, new CacheRepository.CacheResult<Child>() {
            @Override
            public void onLoaded(Child cachedSong) {
                song.postValue(cachedSong);
            }
        });
    }

    private void loadCachedSongsByYearRange(Integer fromYear, Integer toYear, int number, MutableLiveData<List<Child>> randomSongsSample) {
        List<Child> current = randomSongsSample.getValue();
        if (current != null && !current.isEmpty()) {
            return;
        }
        Type type = new TypeToken<List<Child>>() {
        }.getType();
        cacheRepository.load("songs_all", type, new CacheRepository.CacheResult<List<Child>>() {
            @Override
            public void onLoaded(List<Child> cachedSongs) {
                if (cachedSongs == null) {
                    mergeLocalByYearRange(fromYear, toYear, number, new ArrayList<>(), randomSongsSample);
                    return;
                }

                List<Child> filtered = new ArrayList<>();
                if (fromYear == null && toYear == null) {
                    filtered.addAll(cachedSongs);
                } else {
                    int minYear = fromYear != null ? fromYear : Integer.MIN_VALUE;
                    int maxYear = toYear != null ? toYear : Integer.MAX_VALUE;
                    for (Child song : cachedSongs) {
                        if (song == null) continue;
                        Integer year = song.getYear();
                        if (year == null) continue;
                        if (year >= minYear && year <= maxYear) {
                            filtered.add(song);
                        }
                    }
                }

                Collections.shuffle(filtered);
                if (number > 0 && filtered.size() > number) {
                    filtered = new ArrayList<>(filtered.subList(0, number));
                }

                mergeLocalByYearRange(fromYear, toYear, number, filtered, randomSongsSample);
            }
        });
    }

    private void loadCachedSongsByGenre(String genreId, int page, MutableLiveData<List<Child>> songsByGenre) {
        Type type = new TypeToken<List<Child>>() {
        }.getType();
        cacheRepository.load("songs_all", type, new CacheRepository.CacheResult<List<Child>>() {
            @Override
            public void onLoaded(List<Child> cachedSongs) {
                if (cachedSongs == null) {
                    songsByGenre.postValue(new ArrayList<>());
                    return;
                }

                List<Child> filtered = new ArrayList<>();
                for (Child song : cachedSongs) {
                    if (song == null) continue;
                    if (genreId == null) continue;
                    if (song.getGenre() != null && song.getGenre().equalsIgnoreCase(genreId)) {
                        filtered.add(song);
                    }
                }

                int startIndex = page * 100;
                if (startIndex >= filtered.size()) {
                    songsByGenre.postValue(new ArrayList<>());
                    return;
                }

                int endIndex = Math.min(filtered.size(), startIndex + 100);
                mergeLocalByGenre(genreId, new ArrayList<>(filtered.subList(startIndex, endIndex)), songsByGenre);
            }
        });
    }

    private void loadCachedSongsByGenres(ArrayList<String> genresId, MutableLiveData<List<Child>> songsByGenre) {
        Type type = new TypeToken<List<Child>>() {
        }.getType();
        cacheRepository.load("songs_all", type, new CacheRepository.CacheResult<List<Child>>() {
            @Override
            public void onLoaded(List<Child> cachedSongs) {
                if (cachedSongs == null) {
                    songsByGenre.postValue(new ArrayList<>());
                    return;
                }

                List<Child> filtered = new ArrayList<>();
                for (Child song : cachedSongs) {
                    if (song == null || song.getGenre() == null) continue;
                    for (String genreId : genresId) {
                        if (genreId != null && song.getGenre().equalsIgnoreCase(genreId)) {
                            filtered.add(song);
                            break;
                        }
                    }
                }

                mergeLocalByGenres(genresId, filtered, songsByGenre);
            }
        });
    }

    private void loadCachedSongFromAll(String songId, MutableLiveData<Child> song) {
        Type type = new TypeToken<List<Child>>() {
        }.getType();
        cacheRepository.load("songs_all", type, new CacheRepository.CacheResult<List<Child>>() {
            @Override
            public void onLoaded(List<Child> songs) {
                if (songs == null) return;
                for (Child entry : songs) {
                    if (entry != null && songId != null && songId.equals(entry.getId())) {
                        song.postValue(entry);
                        return;
                    }
                }
            }
        });
    }

    private void mergeLocalByYearRange(Integer fromYear, Integer toYear, int limit, List<Child> base, MutableLiveData<List<Child>> liveData) {
        LocalMusicRepository.getLocalSongsByYearRange(App.getContext(), fromYear, toYear, localSongs -> {
            List<Child> merged = new ArrayList<>(base != null ? base : new ArrayList<>());
            if (localSongs != null) {
                merged.addAll(localSongs);
            }
            Collections.shuffle(merged);
            if (limit > 0 && merged.size() > limit) {
                merged = new ArrayList<>(merged.subList(0, limit));
            }
            ensureRemoteFirstIfAvailable(merged);
            liveData.postValue(merged);
        });
    }

    private void mergeLocalByGenre(String genreId, List<Child> base, MutableLiveData<List<Child>> liveData) {
        LocalMusicRepository.getLocalSongsByGenre(App.getContext(), genreId, localSongs -> {
            List<Child> merged = new ArrayList<>(base != null ? base : new ArrayList<>());
            if (localSongs != null) {
                merged.addAll(localSongs);
            }
            liveData.postValue(merged);
        });
    }

    private void mergeLocalByGenres(List<String> genres, List<Child> base, MutableLiveData<List<Child>> liveData) {
        LocalMusicRepository.getLocalSongsByGenres(App.getContext(), genres, localSongs -> {
            List<Child> merged = new ArrayList<>(base != null ? base : new ArrayList<>());
            if (localSongs != null) {
                merged.addAll(localSongs);
            }
            liveData.postValue(merged);
        });
    }

    private void ensureRemoteFirstIfAvailable(List<Child> items) {
        if (items == null || items.size() < 2) {
            return;
        }
        Child first = items.get(0);
        if (first == null || !LocalMusicRepository.isLocalSong(first)) {
            return;
        }
        for (int i = 1; i < items.size(); i++) {
            Child candidate = items.get(i);
            if (candidate != null && !LocalMusicRepository.isLocalSong(candidate)) {
                items.set(0, candidate);
                items.set(i, first);
                break;
            }
        }
    }
}
