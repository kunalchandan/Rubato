package one.chandan.rubato.repository;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import one.chandan.rubato.App;
import one.chandan.rubato.subsonic.base.ApiResponse;
import one.chandan.rubato.subsonic.models.Genre;
import one.chandan.rubato.util.OfflinePolicy;
import one.chandan.rubato.repository.LocalMusicRepository;
import one.chandan.rubato.subsonic.models.Child;
import com.google.gson.reflect.TypeToken;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.lang.reflect.Type;
import java.util.ArrayList;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class GenreRepository {
    private final CacheRepository cacheRepository = new CacheRepository();
    public MutableLiveData<List<Genre>> getGenres(boolean random, int size) {
        MutableLiveData<List<Genre>> genres = new MutableLiveData<>();
        String cacheKey = "genres_all";

        if (OfflinePolicy.isOffline()) {
            loadCachedGenres(cacheKey, genres, random, size);
            return genres;
        }

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getGenres()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse() != null && response.body().getSubsonicResponse().getGenres() != null) {
                            List<Genre> genreList = response.body().getSubsonicResponse().getGenres().getGenres();

                            if (genreList == null || genreList.isEmpty()) {
                                genres.setValue(Collections.emptyList());
                                return;
                            }

                            if (random) {
                                Collections.shuffle(genreList);
                            }

                            if (size != -1) {
                                List<Genre> selection = genreList.subList(0, Math.min(size, genreList.size()));
                                LocalMusicRepository.appendLocalGenres(App.getContext(), selection, genres::postValue);
                            } else {
                                List<Genre> sorted = genreList.stream().sorted(Comparator.comparing(Genre::getGenre)).collect(Collectors.toList());
                                LocalMusicRepository.appendLocalGenres(App.getContext(), sorted, genres::postValue);
                            }

                            cacheRepository.save(cacheKey, genreList);
                            return;
                        }

                        loadCachedGenres(cacheKey, genres, random, size);
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        loadCachedGenres(cacheKey, genres, random, size);

                    }
                });

        return genres;
    }

    private void loadCachedGenres(String cacheKey, MutableLiveData<List<Genre>> genres, boolean random, int size) {
        Type type = new TypeToken<List<Genre>>() {
        }.getType();
        cacheRepository.load(cacheKey, type, new CacheRepository.CacheResult<List<Genre>>() {
            @Override
            public void onLoaded(List<Genre> genreList) {
                if (genreList == null || genreList.isEmpty()) {
                    genres.postValue(Collections.emptyList());
                    return;
                }

                if (random) {
                    Collections.shuffle(genreList);
                }

                if (size != -1) {
                    List<Genre> selection = genreList.subList(0, Math.min(size, genreList.size()));
                    LocalMusicRepository.appendLocalGenres(App.getContext(), selection, genres::postValue);
                } else {
                    List<Genre> sorted = genreList.stream().sorted(Comparator.comparing(Genre::getGenre)).collect(Collectors.toList());
                    LocalMusicRepository.appendLocalGenres(App.getContext(), sorted, genres::postValue);
                }
            }
        });
    }

    public MutableLiveData<List<Genre>> getRelatedGenres(String genreName, int limit) {
        MutableLiveData<List<Genre>> related = new MutableLiveData<>(Collections.emptyList());
        if (genreName == null || genreName.trim().isEmpty()) {
            return related;
        }

        String target = genreName.trim();
        Type songsType = new TypeToken<List<Child>>() {}.getType();
        Type genresType = new TypeToken<List<Genre>>() {}.getType();

        cacheRepository.loadOrNull("songs_all", songsType, new CacheRepository.CacheResult<List<Child>>() {
            @Override
            public void onLoaded(List<Child> songs) {
                cacheRepository.loadOrNull("genres_all", genresType, new CacheRepository.CacheResult<List<Genre>>() {
                    @Override
                    public void onLoaded(List<Genre> genreList) {
                        List<Genre> resolved = computeRelatedGenres(target, songs, genreList, limit);
                        related.postValue(resolved);
                    }
                });
            }
        });

        return related;
    }

    private List<Genre> computeRelatedGenres(String target, List<Child> songs, List<Genre> genreList, int limit) {
        String targetLower = target.toLowerCase().trim();
        String[] tokens = splitTokens(targetLower);
        Map<String, Genre> genreByName = new HashMap<>();
        if (genreList != null) {
            for (Genre genre : genreList) {
                if (genre == null || genre.getGenre() == null) continue;
                genreByName.put(genre.getGenre(), genre);
            }
        }

        Set<String> baseArtists = new HashSet<>();
        if (songs != null) {
            for (Child song : songs) {
                if (song == null || song.getGenre() == null) continue;
                if (targetLower.equalsIgnoreCase(song.getGenre().trim())) {
                    if (song.getArtist() != null) {
                        baseArtists.add(song.getArtist());
                    }
                }
            }
        }

        Map<String, Integer> overlapByGenre = new HashMap<>();
        Map<String, Integer> songCountByGenre = new HashMap<>();
        if (songs != null) {
            for (Child song : songs) {
                if (song == null || song.getGenre() == null) continue;
                String genreName = song.getGenre().trim();
                if (genreName.equalsIgnoreCase(target)) continue;

                songCountByGenre.put(genreName, songCountByGenre.getOrDefault(genreName, 0) + 1);
                if (song.getArtist() != null && baseArtists.contains(song.getArtist())) {
                    overlapByGenre.put(genreName, overlapByGenre.getOrDefault(genreName, 0) + 1);
                }
            }
        }

        List<ScoredGenre> scored = new ArrayList<>();
        if (genreList != null && !genreList.isEmpty()) {
            for (Genre genre : genreList) {
                if (genre == null || genre.getGenre() == null) continue;
                String name = genre.getGenre();
                if (name.equalsIgnoreCase(target)) continue;
                int score = computeNameScore(name.toLowerCase(), targetLower, tokens);
                int overlap = overlapByGenre.getOrDefault(name, 0);
                int songCount = Math.max(genre.getSongCount(), songCountByGenre.getOrDefault(name, 0));
                score += overlap * 3;
                score += Math.min(songCount, 200) / 50;
                scored.add(new ScoredGenre(genre, score));
            }
        } else if (songCountByGenre.size() > 0) {
            for (String name : songCountByGenre.keySet()) {
                int score = computeNameScore(name.toLowerCase(), targetLower, tokens);
                int overlap = overlapByGenre.getOrDefault(name, 0);
                int songCount = songCountByGenre.getOrDefault(name, 0);
                score += overlap * 3;
                score += Math.min(songCount, 200) / 50;
                Genre genre = genreByName.getOrDefault(name, new Genre(name, songCount, 0));
                scored.add(new ScoredGenre(genre, score));
            }
        }

        scored.sort((a, b) -> Integer.compare(b.score, a.score));
        List<Genre> result = new ArrayList<>();
        for (ScoredGenre item : scored) {
            if (item.score <= 0) continue;
            result.add(item.genre);
            if (result.size() >= limit) break;
        }
        return result;
    }

    private int computeNameScore(String name, String target, String[] tokens) {
        int score = 0;
        if (name.equals(target)) return 0;
        if (name.contains(target) || target.contains(name)) {
            score += 6;
        }
        for (String token : tokens) {
            if (token.length() < 2) continue;
            if (name.contains(token)) {
                score += 2;
            }
        }
        return score;
    }

    private String[] splitTokens(String value) {
        return value.split("[\\s,/&\\-()]+");
    }

    private static class ScoredGenre {
        private final Genre genre;
        private final int score;

        private ScoredGenre(Genre genre, int score) {
            this.genre = genre;
            this.score = score;
        }
    }
}
