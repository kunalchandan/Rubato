package one.chandan.rubato.repository;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import one.chandan.rubato.App;
import one.chandan.rubato.subsonic.base.ApiResponse;
import one.chandan.rubato.subsonic.models.ArtistID3;
import one.chandan.rubato.subsonic.models.ArtistInfo2;
import one.chandan.rubato.subsonic.models.Child;
import one.chandan.rubato.subsonic.models.IndexID3;
import one.chandan.rubato.util.NetworkUtil;
import one.chandan.rubato.repository.LocalMusicRepository;
import one.chandan.rubato.util.LibraryDedupeUtil;
import one.chandan.rubato.util.SearchIndexUtil;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.lang.reflect.Type;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ArtistRepository {
    private final CacheRepository cacheRepository = new CacheRepository();
    private final JellyfinCacheRepository jellyfinCacheRepository = new JellyfinCacheRepository();
    public MutableLiveData<List<ArtistID3>> getStarredArtists(boolean random, int size) {
        MutableLiveData<List<ArtistID3>> starredArtists = new MutableLiveData<>(new ArrayList<>());
        String cacheKey = "starred_artists";

        loadCachedStarredArtists(cacheKey, starredArtists, random, size);

        if (NetworkUtil.isOffline()) {
            return starredArtists;
        }

        App.getSubsonicClientInstance(false)
                .getAlbumSongListClient()
                .getStarred2()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getStarred2() != null) {
                            List<ArtistID3> artists = response.body().getSubsonicResponse().getStarred2().getArtists();

                            if (artists != null) {
                                cacheRepository.save(cacheKey, artists);
                                List<ArtistID3> selection;
                                if (!random) {
                                    selection = artists;
                                } else {
                                    Collections.shuffle(artists);
                                    selection = artists.subList(0, Math.min(size, artists.size()));
                                }
                                starredArtists.postValue(new ArrayList<>(selection));
                                loadCachedArtistsForSelection(selection, starredArtists);
                                getArtistInfo(selection, starredArtists);
                            }
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {

                    }
                });

        return starredArtists;
    }

    public MutableLiveData<List<ArtistID3>> getArtists(boolean random, int size) {
        MutableLiveData<List<ArtistID3>> listLiveArtists = new MutableLiveData<>();
        String cacheKey = "artists_all";

        if (NetworkUtil.isOffline()) {
            loadCachedArtists(cacheKey, listLiveArtists, random, size);
            mergeJellyfinArtists(listLiveArtists);
            return listLiveArtists;
        }

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getArtists()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            List<ArtistID3> artists = new ArrayList<>();

                            if(response.body().getSubsonicResponse().getArtists() != null && response.body().getSubsonicResponse().getArtists().getIndices() != null) {
                                for (IndexID3 index : response.body().getSubsonicResponse().getArtists().getIndices()) {
                                    if(index != null && index.getArtists() != null) {
                                        artists.addAll(index.getArtists());
                                    }
                                }
                            }

                            if (random) {
                                Collections.shuffle(artists);
                                getArtistInfo(artists.subList(0, artists.size() / size > 0 ? size : artists.size()), listLiveArtists);
                            } else {
                                LocalMusicRepository.appendLocalArtists(App.getContext(), artists, merged -> {
                                    listLiveArtists.postValue(merged);
                                    mergeJellyfinArtists(listLiveArtists);
                                });
                            }

                            cacheRepository.save(cacheKey, artists);
                            return;
                        }

                        loadCachedArtists(cacheKey, listLiveArtists, random, size);
                        mergeJellyfinArtists(listLiveArtists);
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        loadCachedArtists(cacheKey, listLiveArtists, random, size);
                        mergeJellyfinArtists(listLiveArtists);
                    }
                });

        return listLiveArtists;
    }

    /*
     * Metodo che mi restituisce le informazioni essenzionali dell'artista (cover, numero di album...)
     */
    public void getArtistInfo(List<ArtistID3> artists, MutableLiveData<List<ArtistID3>> list) {
        List<ArtistID3> liveArtists = list.getValue();
        if (liveArtists == null) liveArtists = new ArrayList<>();
        list.setValue(liveArtists);

        if (NetworkUtil.isOffline()) {
            loadCachedArtistsForSelection(artists, list);
            return;
        }

        for (ArtistID3 artist : artists) {
            if (artist != null && SearchIndexUtil.isJellyfinTagged(artist.getId())) {
                addToMutableLiveData(list, artist);
                continue;
            }
            if (artist != null && LocalMusicRepository.isLocalArtistId(artist.getId())) {
                addToMutableLiveData(list, artist);
                continue;
            }
            App.getSubsonicClientInstance(false)
                    .getBrowsingClient()
                    .getArtist(artist.getId())
                    .enqueue(new Callback<ApiResponse>() {
                        @Override
                        public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                            if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getArtist() != null) {
                                addToMutableLiveData(list, response.body().getSubsonicResponse().getArtist());
                            }
                        }

                        @Override
                        public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {

                        }
                    });
        }
    }

    public MutableLiveData<ArtistID3> getArtistInfo(String id) {
        MutableLiveData<ArtistID3> artist = new MutableLiveData<>();

        if (SearchIndexUtil.isJellyfinTagged(id)) {
            jellyfinCacheRepository.loadArtist(id, artist::postValue);
            return artist;
        }

        if (LocalMusicRepository.isLocalArtistId(id)) {
            LocalMusicRepository.getLocalArtist(App.getContext(), id, artist::postValue);
            return artist;
        }

        if (NetworkUtil.isOffline()) {
            loadCachedArtistFromAll(id, artist);
            return artist;
        }

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getArtist(id)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getArtist() != null) {
                            artist.setValue(response.body().getSubsonicResponse().getArtist());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {

                    }
                });

        return artist;
    }

    public MutableLiveData<ArtistInfo2> getArtistFullInfo(String id) {
        MutableLiveData<ArtistInfo2> artistFullInfo = new MutableLiveData<>(null);
        String cacheKey = "artist_info_" + id;

        if (SearchIndexUtil.isJellyfinTagged(id)) {
            artistFullInfo.setValue(null);
            return artistFullInfo;
        }

        if (LocalMusicRepository.isLocalArtistId(id)) {
            artistFullInfo.setValue(null);
            return artistFullInfo;
        }

        if (NetworkUtil.isOffline()) {
            loadCachedArtistInfo(cacheKey, artistFullInfo);
            return artistFullInfo;
        }

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getArtistInfo2(id)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getArtistInfo2() != null) {
                            ArtistInfo2 info = response.body().getSubsonicResponse().getArtistInfo2();
                            artistFullInfo.setValue(info);
                            cacheRepository.save(cacheKey, info);
                            return;
                        }

                        loadCachedArtistInfo(cacheKey, artistFullInfo);
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        loadCachedArtistInfo(cacheKey, artistFullInfo);
                    }
                });

        return artistFullInfo;
    }

    public void setRating(String id, int rating) {
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

    public MutableLiveData<ArtistID3> getArtist(String id) {
        MutableLiveData<ArtistID3> artist = new MutableLiveData<>();
        String cacheKey = "artist_" + id;

        if (SearchIndexUtil.isJellyfinTagged(id)) {
            jellyfinCacheRepository.loadArtist(id, artist::postValue);
            return artist;
        }

        if (LocalMusicRepository.isLocalArtistId(id)) {
            LocalMusicRepository.getLocalArtist(App.getContext(), id, artist::postValue);
            return artist;
        }

        if (NetworkUtil.isOffline()) {
            loadCachedArtist(cacheKey, artist);
            loadCachedArtistFromAll(id, artist);
            return artist;
        }

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getArtist(id)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getArtist() != null) {
                            ArtistID3 artistResponse = response.body().getSubsonicResponse().getArtist();
                            artist.setValue(artistResponse);
                            cacheRepository.save(cacheKey, artistResponse);
                            return;
                        }

                        loadCachedArtist(cacheKey, artist);
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        loadCachedArtist(cacheKey, artist);

                    }
                });

        return artist;
    }

    public MutableLiveData<List<Child>> getInstantMix(ArtistID3 artist, int count) {
        MutableLiveData<List<Child>> instantMix = new MutableLiveData<>();

        if (artist != null && LocalMusicRepository.isLocalArtistId(artist.getId())) {
            LocalMusicRepository.getLocalArtistSongs(App.getContext(), artist.getId(), instantMix::postValue);
            return instantMix;
        }

        if (artist != null && SearchIndexUtil.isJellyfinTagged(artist.getId())) {
            jellyfinCacheRepository.loadSongsForArtist(artist.getId(), songs -> {
                List<Child> shuffled = new ArrayList<>(songs != null ? songs : new ArrayList<>());
                if (!shuffled.isEmpty()) {
                    Collections.shuffle(shuffled);
                }
                if (count > 0 && shuffled.size() > count) {
                    shuffled = new ArrayList<>(shuffled.subList(0, count));
                }
                instantMix.postValue(shuffled);
            });
            return instantMix;
        }

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getSimilarSongs2(artist.getId(), count)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getSimilarSongs2() != null) {
                            instantMix.setValue(response.body().getSubsonicResponse().getSimilarSongs2().getSongs());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {

                    }
                });

        return instantMix;
    }

    public MutableLiveData<List<Child>> getRandomSong(ArtistID3 artist, int count) {
        MutableLiveData<List<Child>> randomSongs = new MutableLiveData<>();

        if (artist != null && LocalMusicRepository.isLocalArtistId(artist.getId())) {
            LocalMusicRepository.getLocalArtistSongs(App.getContext(), artist.getId(), songs -> {
                List<Child> localSongs = new ArrayList<>(songs != null ? songs : new ArrayList<>());
                if (!localSongs.isEmpty()) {
                    Collections.shuffle(localSongs);
                    if (count > 0 && localSongs.size() > count) {
                        localSongs = new ArrayList<>(localSongs.subList(0, count));
                    }
                }
                randomSongs.postValue(localSongs);
            });
            return randomSongs;
        }

        if (artist != null && SearchIndexUtil.isJellyfinTagged(artist.getId())) {
            jellyfinCacheRepository.loadSongsForArtist(artist.getId(), songs -> {
                List<Child> shuffled = new ArrayList<>(songs != null ? songs : new ArrayList<>());
                if (!shuffled.isEmpty()) {
                    Collections.shuffle(shuffled);
                }
                if (count > 0 && shuffled.size() > count) {
                    shuffled = new ArrayList<>(shuffled.subList(0, count));
                }
                randomSongs.postValue(shuffled);
            });
            return randomSongs;
        }

        if (NetworkUtil.isOffline()) {
            loadCachedSongsForArtist(artist, count, true, randomSongs, false);
            return randomSongs;
        }

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getTopSongs(artist.getName(), count)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getTopSongs() != null && response.body().getSubsonicResponse().getTopSongs().getSongs() != null) {
                            List<Child> songs = response.body().getSubsonicResponse().getTopSongs().getSongs();

                            if (songs != null && !songs.isEmpty()) {
                                Collections.shuffle(songs);
                            }

                            randomSongs.setValue(songs);
                            mergeJellyfinSongsByName(artist != null ? artist.getName() : null, songs, randomSongs);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {

                    }
                });

        return randomSongs;
    }

    public MutableLiveData<List<Child>> getTopSongs(String artistName, int count) {
        MutableLiveData<List<Child>> topSongs = new MutableLiveData<>(new ArrayList<>());

        if (NetworkUtil.isOffline()) {
            loadCachedSongsForArtistName(artistName, count, topSongs);
            return topSongs;
        }

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getTopSongs(artistName, count)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getTopSongs() != null && response.body().getSubsonicResponse().getTopSongs().getSongs() != null) {
                            List<Child> songs = response.body().getSubsonicResponse().getTopSongs().getSongs();
                            LocalMusicRepository.getLocalArtistSongsByName(App.getContext(), artistName, localSongs -> {
                                List<Child> merged = new ArrayList<>(songs != null ? songs : new ArrayList<>());
                                if (localSongs != null) {
                                    merged.addAll(localSongs);
                                }
                                topSongs.setValue(merged);
                                mergeJellyfinSongsByName(artistName, merged, topSongs);
                            });
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {

                    }
                });

        return topSongs;
    }

    private void addToMutableLiveData(MutableLiveData<List<ArtistID3>> liveData, ArtistID3 artist) {
        List<ArtistID3> liveArtists = liveData.getValue();
        if (liveArtists == null) {
            liveArtists = new ArrayList<>();
        }
        boolean replaced = false;
        if (artist != null && artist.getId() != null) {
            for (int i = 0; i < liveArtists.size(); i++) {
                ArtistID3 existing = liveArtists.get(i);
                if (existing != null && artist.getId().equals(existing.getId())) {
                    liveArtists.set(i, artist);
                    replaced = true;
                    break;
                }
            }
        }
        if (!replaced) {
            liveArtists.add(artist);
        }
        liveData.setValue(liveArtists);
    }

    private void loadCachedArtists(String cacheKey, MutableLiveData<List<ArtistID3>> listLiveArtists, boolean random, int size) {
        Type type = new TypeToken<List<ArtistID3>>() {
        }.getType();
        cacheRepository.load(cacheKey, type, new CacheRepository.CacheResult<List<ArtistID3>>() {
            @Override
            public void onLoaded(List<ArtistID3> artists) {
                if (artists == null) return;
                if (random) {
                    Collections.shuffle(artists);
                    List<ArtistID3> selection = artists.subList(0, artists.size() / size > 0 ? size : artists.size());
                    LocalMusicRepository.appendLocalArtists(App.getContext(), selection, listLiveArtists::postValue);
                } else {
                    LocalMusicRepository.appendLocalArtists(App.getContext(), artists, listLiveArtists::postValue);
                }
            }
        });
    }

    private void loadCachedArtist(String cacheKey, MutableLiveData<ArtistID3> artist) {
        cacheRepository.load(cacheKey, ArtistID3.class, new CacheRepository.CacheResult<ArtistID3>() {
            @Override
            public void onLoaded(ArtistID3 cachedArtist) {
                artist.postValue(cachedArtist);
            }
        });
    }

    private void loadCachedArtistInfo(String cacheKey, MutableLiveData<ArtistInfo2> artistInfo) {
        cacheRepository.load(cacheKey, ArtistInfo2.class, new CacheRepository.CacheResult<ArtistInfo2>() {
            @Override
            public void onLoaded(ArtistInfo2 cachedInfo) {
                artistInfo.postValue(cachedInfo);
            }
        });
    }

    private void loadCachedArtistFromAll(String artistId, MutableLiveData<ArtistID3> artist) {
        Type type = new TypeToken<List<ArtistID3>>() {
        }.getType();
        cacheRepository.load("artists_all", type, new CacheRepository.CacheResult<List<ArtistID3>>() {
            @Override
            public void onLoaded(List<ArtistID3> artists) {
                if (artists == null) return;
                for (ArtistID3 entry : artists) {
                    if (entry != null && artistId != null && artistId.equals(entry.getId())) {
                        artist.postValue(entry);
                        return;
                    }
                }
            }
        });
    }

    private void loadCachedStarredArtists(String cacheKey, MutableLiveData<List<ArtistID3>> listLiveArtists, boolean random, int size) {
        List<ArtistID3> current = listLiveArtists.getValue();
        if (current != null && !current.isEmpty()) {
            return;
        }
        Type type = new TypeToken<List<ArtistID3>>() {
        }.getType();
        cacheRepository.load(cacheKey, type, new CacheRepository.CacheResult<List<ArtistID3>>() {
            @Override
            public void onLoaded(List<ArtistID3> artists) {
                if (artists == null) return;
                List<ArtistID3> selection;
                if (random) {
                    Collections.shuffle(artists);
                    selection = artists.subList(0, Math.min(size, artists.size()));
                } else {
                    selection = artists;
                }
                listLiveArtists.postValue(new ArrayList<>(selection));
                loadCachedArtistsForSelection(selection, listLiveArtists);
            }
        });
    }

    private void loadCachedArtistsForSelection(List<ArtistID3> selection, MutableLiveData<List<ArtistID3>> list) {
        Type type = new TypeToken<List<ArtistID3>>() {
        }.getType();
        cacheRepository.load("artists_all", type, new CacheRepository.CacheResult<List<ArtistID3>>() {
            @Override
            public void onLoaded(List<ArtistID3> artists) {
                if (artists == null) {
                    list.postValue(selection);
                    return;
                }

                List<ArtistID3> result = new ArrayList<>();
                for (ArtistID3 input : selection) {
                    if (input == null) continue;
                    ArtistID3 match = null;
                    for (ArtistID3 cached : artists) {
                        if (cached != null && cached.getId() != null && cached.getId().equals(input.getId())) {
                            match = cached;
                            break;
                        }
                    }
                    result.add(match != null ? match : input);
                }

                list.postValue(result);
            }
        });
    }

    private void loadCachedSongsForArtist(ArtistID3 artist, int count, boolean shuffle, MutableLiveData<List<Child>> songsLiveData, boolean sortByPlayCount) {
        if (artist == null) {
            songsLiveData.postValue(new ArrayList<>());
            return;
        }
        String artistId = artist.getId();
        String artistName = artist.getName();
        loadCachedSongsForArtistCommon(artistId, artistName, count, shuffle, sortByPlayCount, songsLiveData);
    }

    private void loadCachedSongsForArtistName(String artistName, int count, MutableLiveData<List<Child>> songsLiveData) {
        loadCachedSongsForArtistCommon(null, artistName, count, false, true, songsLiveData);
    }

    private void loadCachedSongsForArtistCommon(String artistId, String artistName, int count, boolean shuffle, boolean sortByPlayCount, MutableLiveData<List<Child>> songsLiveData) {
        Type type = new TypeToken<List<Child>>() {
        }.getType();
        cacheRepository.load("songs_all", type, new CacheRepository.CacheResult<List<Child>>() {
            @Override
            public void onLoaded(List<Child> songs) {
                if (songs == null) {
                    songsLiveData.postValue(new ArrayList<>());
                    return;
                }

                List<Child> filtered = new ArrayList<>();
                for (Child song : songs) {
                    if (song == null) continue;
                    if (artistId != null && artistId.equals(song.getArtistId())) {
                        filtered.add(song);
                    } else if (artistName != null && song.getArtist() != null && song.getArtist().equalsIgnoreCase(artistName)) {
                        filtered.add(song);
                    }
                }

                if (sortByPlayCount) {
                    filtered.sort((a, b) -> {
                        long aCount = a != null && a.getPlayCount() != null ? a.getPlayCount() : 0;
                        long bCount = b != null && b.getPlayCount() != null ? b.getPlayCount() : 0;
                        return Long.compare(bCount, aCount);
                    });
                } else if (shuffle) {
                    Collections.shuffle(filtered);
                }

                List<Child> finalFiltered = filtered;
                if (count > 0 && filtered.size() > count) {
                    finalFiltered = new ArrayList<>(filtered.subList(0, count));
                }

                List<Child> snapshot = finalFiltered;
                LocalMusicRepository.loadLibrary(App.getContext(), library -> {
                    List<Child> merged = new ArrayList<>(snapshot);
                    if (library != null && library.songs != null) {
                        for (Child song : library.songs) {
                            if (song == null) continue;
                            if (artistId != null && artistId.equals(song.getArtistId())) {
                                merged.add(song);
                            } else if (artistName != null && song.getArtist() != null && song.getArtist().equalsIgnoreCase(artistName)) {
                                merged.add(song);
                            }
                        }
                    }
                    mergeJellyfinSongsForArtist(artistId, artistName, merged, songsLiveData);
                });
            }
        });
    }

    private void mergeJellyfinArtists(MutableLiveData<List<ArtistID3>> listLiveArtists) {
        jellyfinCacheRepository.loadAllArtists(jellyfinArtists -> {
            List<ArtistID3> base = listLiveArtists.getValue();
            List<ArtistID3> merged = LibraryDedupeUtil.mergeArtists(base != null ? base : new ArrayList<>(), jellyfinArtists);
            listLiveArtists.postValue(merged);
        });
    }

    private void mergeJellyfinSongsByName(String artistName, List<Child> base, MutableLiveData<List<Child>> target) {
        if (artistName == null || artistName.trim().isEmpty()) return;
        jellyfinCacheRepository.loadSongsForArtistName(artistName, jellyfinSongs -> {
            List<Child> merged = LibraryDedupeUtil.mergeSongs(base != null ? base : new ArrayList<>(), jellyfinSongs);
            target.postValue(merged);
        });
    }

    private void mergeJellyfinSongsForArtist(String artistId, String artistName, List<Child> base, MutableLiveData<List<Child>> target) {
        if (artistId != null && SearchIndexUtil.isJellyfinTagged(artistId)) {
            jellyfinCacheRepository.loadSongsForArtist(artistId, jellyfinSongs -> {
                List<Child> merged = LibraryDedupeUtil.mergeSongs(base != null ? base : new ArrayList<>(), jellyfinSongs);
                target.postValue(merged);
            });
            return;
        }
        if (artistName != null && !artistName.trim().isEmpty()) {
            mergeJellyfinSongsByName(artistName, base, target);
        } else {
            target.postValue(base != null ? base : new ArrayList<>());
        }
    }
}
