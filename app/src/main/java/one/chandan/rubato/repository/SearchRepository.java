package one.chandan.rubato.repository;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import one.chandan.rubato.App;
import one.chandan.rubato.database.AppDatabase;
import one.chandan.rubato.database.dao.RecentSearchDao;
import one.chandan.rubato.model.RecentSearch;
import one.chandan.rubato.model.LibrarySearchEntry;
import one.chandan.rubato.model.SearchSuggestion;
import one.chandan.rubato.domain.LibrarySearchEntryMapper;
import one.chandan.rubato.domain.LegacyMediaMapper;
import one.chandan.rubato.domain.MediaAlbum;
import one.chandan.rubato.domain.MediaArtist;
import one.chandan.rubato.domain.MediaDedupeUtil;
import one.chandan.rubato.domain.MediaPlaylist;
import one.chandan.rubato.domain.MediaSong;
import one.chandan.rubato.domain.MediaSourceType;
import one.chandan.rubato.source.LocalSourceAdapter;
import one.chandan.rubato.source.SubsonicSourceAdapter;
import one.chandan.rubato.subsonic.base.ApiResponse;
import one.chandan.rubato.subsonic.models.AlbumID3;
import one.chandan.rubato.subsonic.models.ArtistID3;
import one.chandan.rubato.subsonic.models.Child;
import one.chandan.rubato.subsonic.models.Playlist;
import one.chandan.rubato.subsonic.models.SearchResult2;
import one.chandan.rubato.subsonic.models.SearchResult3;
import one.chandan.rubato.repository.LocalMusicRepository;
import one.chandan.rubato.util.OfflinePolicy;
import one.chandan.rubato.util.Preferences;
import one.chandan.rubato.util.SearchIndexBuilder;
import one.chandan.rubato.util.AppExecutors;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.lang.reflect.Type;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import one.chandan.rubato.util.SearchIndexUtil;

public class SearchRepository {
    private static final long MAX_SONGS_CACHE_SEARCH_CHARS = 3_000_000L;
    private final RecentSearchDao recentSearchDao = AppDatabase.getInstance().recentSearchDao();
    private final CacheRepository cacheRepository = new CacheRepository();
    private final LibrarySearchIndexRepository searchIndexRepository = new LibrarySearchIndexRepository();

    public MutableLiveData<SearchResult2> search2(String query) {
        MutableLiveData<SearchResult2> result = new MutableLiveData<>();

        App.getSubsonicClientInstance(false)
                .getSearchingClient()
                .search3(query, 20, 20, 20)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            result.setValue(response.body().getSubsonicResponse().getSearchResult2());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {

                    }
                });

        return result;
    }

    public MutableLiveData<SearchResult3> search3(String query) {
        MutableLiveData<SearchResult3> result = new MutableLiveData<>();

        if (query == null || query.trim().isEmpty()) {
            result.setValue(new SearchResult3());
            return result;
        }

        SearchAccumulator accumulator = new SearchAccumulator(query, result);

        LocalMusicRepository.search(App.getContext(), query, localResult -> {
            synchronized (accumulator) {
                accumulator.localArtists = mapLocalArtists(localResult != null ? localResult.artists : null);
                accumulator.localAlbums = mapLocalAlbums(localResult != null ? localResult.albums : null);
                accumulator.localSongs = mapLocalSongs(localResult != null ? localResult.songs : null);
            }
            emitMerged(accumulator);
        });

        searchIndexRepository.search(query, 20, 20, 20, (artists, albums, songs) -> {
            synchronized (accumulator) {
                accumulator.indexArtists = mapIndexArtists(artists);
                accumulator.indexAlbums = mapIndexAlbums(albums);
                accumulator.indexSongs = mapIndexSongs(songs);
            }
            emitMerged(accumulator);
        });

        if (OfflinePolicy.isOffline()) {
            return result;
        }

        App.getSubsonicClientInstance(false)
                .getSearchingClient()
                .search3(query, 20, 20, 20)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            SearchResult3 remote = response.body().getSubsonicResponse().getSearchResult3();
                            synchronized (accumulator) {
                                accumulator.remoteArtists = mapRemoteArtists(remote != null ? remote.getArtists() : null);
                                accumulator.remoteAlbums = mapRemoteAlbums(remote != null ? remote.getAlbums() : null);
                                accumulator.remoteSongs = mapRemoteSongs(remote != null ? remote.getSongs() : null);
                            }
                            emitMerged(accumulator);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        emitMerged(accumulator);
                    }
                });

        return result;
    }

    public MutableLiveData<List<Playlist>> searchPlaylists(String query) {
        MutableLiveData<List<Playlist>> result = new MutableLiveData<>(new ArrayList<>());
        if (query == null || query.trim().length() < 1) {
            return result;
        }

        PlaylistAccumulator accumulator = new PlaylistAccumulator(query, result);

        searchIndexRepository.searchPlaylists(query, 20, entries -> {
            synchronized (accumulator) {
                accumulator.indexPlaylists = mapIndexPlaylists(entries);
            }
            emitPlaylists(accumulator);
        });

        if (OfflinePolicy.isOffline()) {
            return result;
        }

        App.getSubsonicClientInstance(false)
                .getPlaylistClient()
                .getPlaylists()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getSubsonicResponse().getPlaylists() != null
                                && response.body().getSubsonicResponse().getPlaylists().getPlaylists() != null) {
                            List<Playlist> playlists = response.body().getSubsonicResponse().getPlaylists().getPlaylists();
                            cacheRepository.save("playlists", playlists);
                            synchronized (accumulator) {
                                accumulator.remotePlaylists = mapRemotePlaylists(filterPlaylists(playlists, query));
                            }
                            emitPlaylists(accumulator);
                            return;
                        }
                        loadCachedPlaylistsDomain(query, accumulator);
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        loadCachedPlaylistsDomain(query, accumulator);
                    }
                });

        return result;
    }

    public MutableLiveData<List<SearchSuggestion>> getSuggestions(String query) {
        MutableLiveData<List<SearchSuggestion>> suggestions = new MutableLiveData<>();
        Map<String, SearchSuggestion> suggestionMap = new LinkedHashMap<>();
        Object lock = new Object();

        LocalMusicRepository.search(App.getContext(), query, localResult -> {
            synchronized (lock) {
                if (localResult.artists != null) {
                    for (ArtistID3 artistID3 : localResult.artists) {
                        addSuggestion(suggestionMap, artistID3 != null ? artistID3.getName() : null,
                                artistID3 != null ? artistID3.getCoverArtId() : null,
                                SearchSuggestion.Kind.ARTIST);
                    }
                }
                if (localResult.albums != null) {
                    for (AlbumID3 albumID3 : localResult.albums) {
                        addSuggestion(suggestionMap, albumID3 != null ? albumID3.getName() : null,
                                albumID3 != null ? albumID3.getCoverArtId() : null,
                                SearchSuggestion.Kind.ALBUM);
                    }
                }
                if (localResult.songs != null) {
                    for (Child song : localResult.songs) {
                        addSuggestion(suggestionMap, song != null ? song.getTitle() : null,
                                song != null ? song.getCoverArtId() : null,
                                SearchSuggestion.Kind.SONG);
                    }
                }
                suggestions.postValue(new ArrayList<>(suggestionMap.values()));
            }
        });

        if (OfflinePolicy.isOffline()) {
            loadIndexedSuggestions(query, suggestionMap, suggestions, lock);
            return suggestions;
        }

        App.getSubsonicClientInstance(false)
                .getSearchingClient()
                .search3(query, 5, 5, 5)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getSearchResult3() != null) {
                            synchronized (lock) {
                                if (response.body().getSubsonicResponse().getSearchResult3().getArtists() != null) {
                                    for (ArtistID3 artistID3 : response.body().getSubsonicResponse().getSearchResult3().getArtists()) {
                                        addSuggestion(suggestionMap, artistID3 != null ? artistID3.getName() : null,
                                                artistID3 != null ? artistID3.getCoverArtId() : null,
                                                SearchSuggestion.Kind.ARTIST);
                                    }
                                }

                                if (response.body().getSubsonicResponse().getSearchResult3().getAlbums() != null) {
                                    for (AlbumID3 albumID3 : response.body().getSubsonicResponse().getSearchResult3().getAlbums()) {
                                        addSuggestion(suggestionMap, albumID3 != null ? albumID3.getName() : null,
                                                albumID3 != null ? albumID3.getCoverArtId() : null,
                                                SearchSuggestion.Kind.ALBUM);
                                    }
                                }

                                if (response.body().getSubsonicResponse().getSearchResult3().getSongs() != null) {
                                    for (Child song : response.body().getSubsonicResponse().getSearchResult3().getSongs()) {
                                        addSuggestion(suggestionMap, song != null ? song.getTitle() : null,
                                                song != null ? song.getCoverArtId() : null,
                                                SearchSuggestion.Kind.SONG);
                                    }
                                }

                                suggestions.postValue(new ArrayList<>(suggestionMap.values()));
                            }
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        loadIndexedSuggestions(query, suggestionMap, suggestions, lock);
                    }
                });

        return suggestions;
    }

    private void addSuggestion(Map<String, SearchSuggestion> map, String title, String coverArtId, SearchSuggestion.Kind kind) {
        if (title == null) return;
        String key = title.trim();
        if (key.isEmpty()) return;
        SearchSuggestion next = new SearchSuggestion(key, coverArtId, kind);
        SearchSuggestion existing = map.get(key);
        if (existing == null) {
            map.put(key, next);
            return;
        }
        boolean hasCover = coverArtId != null && !coverArtId.isEmpty();
        boolean existingHasCover = existing.getCoverArtId() != null && !existing.getCoverArtId().isEmpty();
        if (!existingHasCover && hasCover) {
            map.put(key, next);
            return;
        }
        if (next.getPriority() > existing.getPriority()) {
            map.put(key, next);
        }
    }

    private void mergeLocalSearch(String query, SearchResult3 remote, MutableLiveData<SearchResult3> result) {
        LocalMusicRepository.search(App.getContext(), query, localResult -> {
            SearchResult3 merged = remote != null ? remote : new SearchResult3();

            List<ArtistID3> artistList = new ArrayList<>();
            if (remote != null && remote.getArtists() != null) {
                artistList.addAll(remote.getArtists());
            }
            if (localResult.artists != null) {
                for (ArtistID3 artist : localResult.artists) {
                    if (artist == null) continue;
                    boolean exists = false;
                    for (ArtistID3 existing : artistList) {
                        if (existing != null && existing.getId() != null && existing.getId().equals(artist.getId())) {
                            exists = true;
                            break;
                        }
                    }
                    if (!exists) {
                        artistList.add(artist);
                    }
                }
            }

            List<AlbumID3> albumList = new ArrayList<>();
            if (remote != null && remote.getAlbums() != null) {
                albumList.addAll(remote.getAlbums());
            }
            if (localResult.albums != null) {
                for (AlbumID3 album : localResult.albums) {
                    if (album == null) continue;
                    boolean exists = false;
                    for (AlbumID3 existing : albumList) {
                        if (existing != null && existing.getId() != null && existing.getId().equals(album.getId())) {
                            exists = true;
                            break;
                        }
                    }
                    if (!exists) {
                        albumList.add(album);
                    }
                }
            }

            List<Child> songList = new ArrayList<>();
            if (remote != null && remote.getSongs() != null) {
                songList.addAll(remote.getSongs());
            }
            if (localResult.songs != null) {
                for (Child song : localResult.songs) {
                    if (song == null) continue;
                    boolean exists = false;
                    for (Child existing : songList) {
                        if (existing != null && existing.getId() != null && existing.getId().equals(song.getId())) {
                            exists = true;
                            break;
                        }
                    }
                    if (!exists) {
                        songList.add(song);
                    }
                }
            }

            merged.setArtists(dedupeArtistsBySignature(artistList));
            merged.setAlbums(dedupeAlbumsBySignature(albumList));
            merged.setSongs(dedupeSongsBySignature(songList));

            result.postValue(merged);
            mergeIndexedSearch(query, merged, result);
        });
    }

    private void mergeIndexedSearch(String query, SearchResult3 base, MutableLiveData<SearchResult3> result) {
        if (query == null || query.trim().isEmpty()) {
            return;
        }
        SearchResult3 localBase = base != null ? base : new SearchResult3();
        searchIndexRepository.search(query, 20, 20, 20, (artists, albums, songs) -> {
            List<LibrarySearchEntry> dedupedArtists = dedupeEntries(artists, SearchIndexUtil.TYPE_ARTIST);
            List<LibrarySearchEntry> dedupedAlbums = dedupeEntries(albums, SearchIndexUtil.TYPE_ALBUM);
            List<LibrarySearchEntry> dedupedSongs = dedupeEntries(songs, SearchIndexUtil.TYPE_SONG);
            List<ArtistID3> mappedArtists = mapArtists(dedupedArtists);
            List<AlbumID3> mappedAlbums = mapAlbums(dedupedAlbums);
            List<Child> mappedSongs = mapSongs(dedupedSongs);

            SearchResult3 merged = new SearchResult3();
            merged.setArtists(dedupeArtistsBySignature(mergeArtists(localBase.getArtists(), mappedArtists)));
            merged.setAlbums(dedupeAlbumsBySignature(mergeAlbums(localBase.getAlbums(), mappedAlbums)));
            merged.setSongs(dedupeSongsBySignature(mergeSongs(localBase.getSongs(), mappedSongs)));
            result.postValue(merged);
        });
    }

    private void loadCachedSearch(String query, SearchResult3 base, MutableLiveData<SearchResult3> result) {
        if (query == null || query.trim().isEmpty()) {
            return;
        }

        SearchResult3 localBase = base != null ? base : new SearchResult3();
        Type artistType = new TypeToken<List<ArtistID3>>() {}.getType();
        Type albumType = new TypeToken<List<AlbumID3>>() {}.getType();
        Type songType = new TypeToken<List<Child>>() {}.getType();
        Type playlistType = new TypeToken<List<Playlist>>() {}.getType();

        cacheRepository.loadOrNull("artists_all", artistType, new CacheRepository.CacheResult<List<ArtistID3>>() {
            @Override
            public void onLoaded(List<ArtistID3> artists) {
                cacheRepository.loadOrNull("albums_all", albumType, new CacheRepository.CacheResult<List<AlbumID3>>() {
                    @Override
                    public void onLoaded(List<AlbumID3> albums) {
                        cacheRepository.loadPayloadSize("songs_all", size -> {
                            long safeSize = size != null ? size : 0L;
                            if (safeSize > MAX_SONGS_CACHE_SEARCH_CHARS) {
                                cacheRepository.loadOrNull("playlists", playlistType, new CacheRepository.CacheResult<List<Playlist>>() {
                                    @Override
                                    public void onLoaded(List<Playlist> playlists) {
                                        maybeSeedIndex(artists, albums, Collections.emptyList(), playlists);
                                        SearchResult3 merged = new SearchResult3();
                                        merged.setArtists(mergeArtists(localBase.getArtists(), filterArtists(artists, query)));
                                        merged.setAlbums(mergeAlbums(localBase.getAlbums(), filterAlbums(albums, query)));
                                        merged.setSongs(mergeSongs(localBase.getSongs(), Collections.emptyList()));
                                        result.postValue(merged);
                                    }
                                });
                                return;
                            }
                            cacheRepository.loadOrNull("songs_all", songType, new CacheRepository.CacheResult<List<Child>>() {
                                @Override
                                public void onLoaded(List<Child> songs) {
                                    cacheRepository.loadOrNull("playlists", playlistType, new CacheRepository.CacheResult<List<Playlist>>() {
                                        @Override
                                        public void onLoaded(List<Playlist> playlists) {
                                            maybeSeedIndex(artists, albums, songs, playlists);
                                            SearchResult3 merged = new SearchResult3();
                                            merged.setArtists(mergeArtists(localBase.getArtists(), filterArtists(artists, query)));
                                            merged.setAlbums(mergeAlbums(localBase.getAlbums(), filterAlbums(albums, query)));
                                            merged.setSongs(mergeSongs(localBase.getSongs(), filterSongs(songs, query)));
                                            result.postValue(merged);
                                        }
                                    });
                                }
                            });
                        });
                    }
                });
            }
        });
    }

    private void loadIndexedSearch(String query, SearchResult3 base, MutableLiveData<SearchResult3> result) {
        if (query == null || query.trim().isEmpty()) {
            return;
        }

        SearchResult3 localBase = base != null ? base : new SearchResult3();
        searchIndexRepository.search(query, 20, 20, 20, (artists, albums, songs) -> {
            List<LibrarySearchEntry> dedupedArtists = dedupeEntries(artists, SearchIndexUtil.TYPE_ARTIST);
            List<LibrarySearchEntry> dedupedAlbums = dedupeEntries(albums, SearchIndexUtil.TYPE_ALBUM);
            List<LibrarySearchEntry> dedupedSongs = dedupeEntries(songs, SearchIndexUtil.TYPE_SONG);
            List<ArtistID3> mappedArtists = mapArtists(dedupedArtists);
            List<AlbumID3> mappedAlbums = mapAlbums(dedupedAlbums);
            List<Child> mappedSongs = mapSongs(dedupedSongs);

            if (mappedArtists.isEmpty() && mappedAlbums.isEmpty() && mappedSongs.isEmpty()) {
                loadCachedSearch(query, localBase, result);
                return;
            }

            SearchResult3 merged = new SearchResult3();
            merged.setArtists(dedupeArtistsBySignature(mergeArtists(localBase.getArtists(), mappedArtists)));
            merged.setAlbums(dedupeAlbumsBySignature(mergeAlbums(localBase.getAlbums(), mappedAlbums)));
            merged.setSongs(dedupeSongsBySignature(mergeSongs(localBase.getSongs(), mappedSongs)));
            result.postValue(merged);
        });
    }

    private void loadCachedSuggestions(String query,
                                       Map<String, SearchSuggestion> suggestionMap,
                                       MutableLiveData<List<SearchSuggestion>> suggestions,
                                       Object lock) {
        if (query == null || query.trim().isEmpty()) {
            return;
        }

        Type artistType = new TypeToken<List<ArtistID3>>() {}.getType();
        Type albumType = new TypeToken<List<AlbumID3>>() {}.getType();
        Type songType = new TypeToken<List<Child>>() {}.getType();
        Type playlistType = new TypeToken<List<Playlist>>() {}.getType();

        cacheRepository.loadOrNull("artists_all", artistType, new CacheRepository.CacheResult<List<ArtistID3>>() {
            @Override
            public void onLoaded(List<ArtistID3> artists) {
                cacheRepository.loadOrNull("albums_all", albumType, new CacheRepository.CacheResult<List<AlbumID3>>() {
                    @Override
                    public void onLoaded(List<AlbumID3> albums) {
                        cacheRepository.loadPayloadSize("songs_all", size -> {
                            long safeSize = size != null ? size : 0L;
                            if (safeSize > MAX_SONGS_CACHE_SEARCH_CHARS) {
                                cacheRepository.loadOrNull("playlists", playlistType, new CacheRepository.CacheResult<List<Playlist>>() {
                                    @Override
                                    public void onLoaded(List<Playlist> playlists) {
                                        maybeSeedIndex(artists, albums, Collections.emptyList(), playlists);
                                        synchronized (lock) {
                                            for (ArtistID3 artistID3 : filterArtists(artists, query)) {
                                                addSuggestion(suggestionMap, artistID3 != null ? artistID3.getName() : null,
                                                        artistID3 != null ? artistID3.getCoverArtId() : null,
                                                        SearchSuggestion.Kind.ARTIST);
                                            }
                                            for (AlbumID3 albumID3 : filterAlbums(albums, query)) {
                                                addSuggestion(suggestionMap, albumID3 != null ? albumID3.getName() : null,
                                                        albumID3 != null ? albumID3.getCoverArtId() : null,
                                                        SearchSuggestion.Kind.ALBUM);
                                            }
                                            suggestions.postValue(new ArrayList<>(suggestionMap.values()));
                                        }
                                    }
                                });
                                return;
                            }
                            cacheRepository.loadOrNull("songs_all", songType, new CacheRepository.CacheResult<List<Child>>() {
                                @Override
                                public void onLoaded(List<Child> songs) {
                                    cacheRepository.loadOrNull("playlists", playlistType, new CacheRepository.CacheResult<List<Playlist>>() {
                                        @Override
                                        public void onLoaded(List<Playlist> playlists) {
                                            maybeSeedIndex(artists, albums, songs, playlists);
                                            synchronized (lock) {
                                                for (ArtistID3 artistID3 : filterArtists(artists, query)) {
                                                    addSuggestion(suggestionMap, artistID3 != null ? artistID3.getName() : null,
                                                            artistID3 != null ? artistID3.getCoverArtId() : null,
                                                            SearchSuggestion.Kind.ARTIST);
                                                }
                                                for (AlbumID3 albumID3 : filterAlbums(albums, query)) {
                                                    addSuggestion(suggestionMap, albumID3 != null ? albumID3.getName() : null,
                                                            albumID3 != null ? albumID3.getCoverArtId() : null,
                                                            SearchSuggestion.Kind.ALBUM);
                                                }
                                                for (Child song : filterSongs(songs, query)) {
                                                    addSuggestion(suggestionMap, song != null ? song.getTitle() : null,
                                                            song != null ? song.getCoverArtId() : null,
                                                            SearchSuggestion.Kind.SONG);
                                                }
                                                suggestions.postValue(new ArrayList<>(suggestionMap.values()));
                                            }
                                        }
                                    });
                                }
                            });
                        });
                    }
                });
            }
        });
    }

    private void loadIndexedSuggestions(String query,
                                        Map<String, SearchSuggestion> suggestionMap,
                                        MutableLiveData<List<SearchSuggestion>> suggestions,
                                        Object lock) {
        if (query == null || query.trim().isEmpty()) {
            return;
        }

        searchIndexRepository.search(query, 5, 5, 5, (artists, albums, songs) -> {
            synchronized (lock) {
                for (ArtistID3 artistID3 : mapArtists(dedupeEntries(artists, SearchIndexUtil.TYPE_ARTIST))) {
                    addSuggestion(suggestionMap, artistID3 != null ? artistID3.getName() : null,
                            artistID3 != null ? artistID3.getCoverArtId() : null,
                            SearchSuggestion.Kind.ARTIST);
                }
                for (AlbumID3 albumID3 : mapAlbums(dedupeEntries(albums, SearchIndexUtil.TYPE_ALBUM))) {
                    addSuggestion(suggestionMap, albumID3 != null ? albumID3.getName() : null,
                            albumID3 != null ? albumID3.getCoverArtId() : null,
                            SearchSuggestion.Kind.ALBUM);
                }
                for (Child song : mapSongs(dedupeEntries(songs, SearchIndexUtil.TYPE_SONG))) {
                    addSuggestion(suggestionMap, song != null ? song.getTitle() : null,
                            song != null ? song.getCoverArtId() : null,
                            SearchSuggestion.Kind.SONG);
                }
                suggestions.postValue(new ArrayList<>(suggestionMap.values()));
            }
        });
    }

    private void maybeSeedIndex(List<ArtistID3> artists, List<AlbumID3> albums, List<Child> songs, List<Playlist> playlists) {
        searchIndexRepository.count(count -> {
            if (count != null && count > 0) return;
            List<LibrarySearchEntry> entries = SearchIndexBuilder.buildFromSubsonic(artists, albums, songs, playlists);
            searchIndexRepository.replaceSource(SearchIndexUtil.SOURCE_SUBSONIC, entries);
        });
    }

    private List<ArtistID3> mapArtists(List<LibrarySearchEntry> entries) {
        if (entries == null || entries.isEmpty()) return Collections.emptyList();
        List<ArtistID3> mapped = new ArrayList<>();
        for (LibrarySearchEntry entry : entries) {
            if (entry == null) continue;
            ArtistID3 artist = new ArtistID3();
            artist.setId(tagEntryId(entry, entry.getItemId()));
            artist.setName(entry.getTitle());
            artist.setCoverArtId(tagCoverArt(entry));
            mapped.add(artist);
        }
        return mapped;
    }

    private List<AlbumID3> mapAlbums(List<LibrarySearchEntry> entries) {
        if (entries == null || entries.isEmpty()) return Collections.emptyList();
        List<AlbumID3> mapped = new ArrayList<>();
        for (LibrarySearchEntry entry : entries) {
            if (entry == null) continue;
            AlbumID3 album = new AlbumID3();
            album.setId(tagEntryId(entry, entry.getItemId()));
            album.setName(entry.getTitle());
            album.setArtist(entry.getArtist());
            album.setArtistId(tagEntryId(entry, entry.getArtistId()));
            album.setCoverArtId(tagCoverArt(entry));
            mapped.add(album);
        }
        return mapped;
    }

    private List<Child> mapSongs(List<LibrarySearchEntry> entries) {
        if (entries == null || entries.isEmpty()) return Collections.emptyList();
        List<Child> mapped = new ArrayList<>();
        for (LibrarySearchEntry entry : entries) {
            if (entry == null || entry.getItemId() == null) continue;
            String taggedId = tagEntryId(entry, entry.getItemId());
            Child song = new Child(taggedId);
            song.setTitle(entry.getTitle());
            song.setArtist(entry.getArtist());
            song.setAlbum(entry.getAlbum());
            song.setAlbumId(tagEntryId(entry, entry.getAlbumId()));
            song.setArtistId(tagEntryId(entry, entry.getArtistId()));
            song.setCoverArtId(tagCoverArt(entry));
            mapped.add(song);
        }
        return mapped;
    }

    private String tagEntryId(LibrarySearchEntry entry, String rawId) {
        if (entry == null || rawId == null) return rawId;
        return SearchIndexUtil.tagSourceId(entry.getSource(), rawId);
    }

    private String tagCoverArt(LibrarySearchEntry entry) {
        if (entry == null) return null;
        return SearchIndexUtil.tagSourceId(entry.getSource(), entry.getCoverArt());
    }

    private List<LibrarySearchEntry> dedupeEntries(List<LibrarySearchEntry> entries, String mediaType) {
        if (entries == null || entries.isEmpty()) return Collections.emptyList();
        Map<String, LibrarySearchEntry> deduped = new LinkedHashMap<>();
        for (LibrarySearchEntry entry : entries) {
            if (entry == null) continue;
            String key = buildSignature(entry, mediaType);
            if (key == null || key.isEmpty()) {
                key = entry.getUid();
            }
            LibrarySearchEntry existing = deduped.get(key);
            if (existing == null || shouldPrefer(entry, existing)) {
                deduped.put(key, entry);
            }
        }
        return new ArrayList<>(deduped.values());
    }

    private String buildSignature(LibrarySearchEntry entry, String mediaType) {
        if (entry == null) return "";
        String title = entry.getTitle();
        String artist = entry.getArtist();
        String album = entry.getAlbum();
        if (SearchIndexUtil.TYPE_ARTIST.equals(mediaType)) {
            return SearchIndexUtil.normalize(title);
        }
        if (SearchIndexUtil.TYPE_ALBUM.equals(mediaType)) {
            return SearchIndexUtil.normalize((title != null ? title : "") + "|" + (artist != null ? artist : ""));
        }
        if (SearchIndexUtil.TYPE_SONG.equals(mediaType)) {
            return SearchIndexUtil.normalize((title != null ? title : "") + "|" + (artist != null ? artist : "") + "|" + (album != null ? album : ""));
        }
        if (SearchIndexUtil.TYPE_PLAYLIST.equals(mediaType)) {
            return SearchIndexUtil.normalize(title);
        }
        return SearchIndexUtil.normalize(title);
    }

    private boolean shouldPrefer(LibrarySearchEntry candidate, LibrarySearchEntry existing) {
        int candidateRank = SearchIndexUtil.sourcePriority(candidate.getSource());
        int existingRank = SearchIndexUtil.sourcePriority(existing.getSource());
        if (candidateRank != existingRank) {
            return candidateRank < existingRank;
        }
        boolean candidateHasCover = candidate.getCoverArt() != null && !candidate.getCoverArt().isEmpty();
        boolean existingHasCover = existing.getCoverArt() != null && !existing.getCoverArt().isEmpty();
        if (candidateHasCover != existingHasCover) {
            return candidateHasCover;
        }
        return candidate.getUpdatedAt() > existing.getUpdatedAt();
    }

    private List<MediaPlaylist> mapIndexPlaylists(List<LibrarySearchEntry> entries) {
        if (entries == null || entries.isEmpty()) return Collections.emptyList();
        String subsonicSourceId = resolveSubsonicSourceId();
        List<MediaPlaylist> mapped = new ArrayList<>();
        for (LibrarySearchEntry entry : entries) {
            MediaPlaylist playlist = LibrarySearchEntryMapper.toPlaylist(entry, subsonicSourceId);
            if (playlist != null) {
                mapped.add(playlist);
            }
        }
        return mapped;
    }

    private String resolveSubsonicSourceId() {
        String serverId = Preferences.getServerId();
        if (serverId != null && !serverId.trim().isEmpty()) {
            return serverId.trim();
        }
        return MediaSourceType.SUBSONIC.getId();
    }

    private SubsonicSourceAdapter subsonicAdapter() {
        return new SubsonicSourceAdapter(resolveSubsonicSourceId(), MediaSourceType.SUBSONIC.getId());
    }

    private List<MediaArtist> mapLocalArtists(List<ArtistID3> artists) {
        if (artists == null || artists.isEmpty()) return Collections.emptyList();
        LocalSourceAdapter adapter = new LocalSourceAdapter();
        List<MediaArtist> mapped = new ArrayList<>();
        for (ArtistID3 artist : artists) {
            MediaArtist item = adapter.mapArtist(artist);
            if (item != null) mapped.add(item);
        }
        return mapped;
    }

    private List<MediaAlbum> mapLocalAlbums(List<AlbumID3> albums) {
        if (albums == null || albums.isEmpty()) return Collections.emptyList();
        LocalSourceAdapter adapter = new LocalSourceAdapter();
        List<MediaAlbum> mapped = new ArrayList<>();
        for (AlbumID3 album : albums) {
            MediaAlbum item = adapter.mapAlbum(album);
            if (item != null) mapped.add(item);
        }
        return mapped;
    }

    private List<MediaSong> mapLocalSongs(List<Child> songs) {
        if (songs == null || songs.isEmpty()) return Collections.emptyList();
        LocalSourceAdapter adapter = new LocalSourceAdapter();
        List<MediaSong> mapped = new ArrayList<>();
        for (Child song : songs) {
            MediaSong item = adapter.mapSong(song);
            if (item != null) mapped.add(item);
        }
        return mapped;
    }

    private List<MediaArtist> mapRemoteArtists(List<ArtistID3> artists) {
        if (artists == null || artists.isEmpty()) return Collections.emptyList();
        SubsonicSourceAdapter adapter = subsonicAdapter();
        List<MediaArtist> mapped = new ArrayList<>();
        for (ArtistID3 artist : artists) {
            MediaArtist item = adapter.mapArtist(artist);
            if (item != null) mapped.add(item);
        }
        return mapped;
    }

    private List<MediaAlbum> mapRemoteAlbums(List<AlbumID3> albums) {
        if (albums == null || albums.isEmpty()) return Collections.emptyList();
        SubsonicSourceAdapter adapter = subsonicAdapter();
        List<MediaAlbum> mapped = new ArrayList<>();
        for (AlbumID3 album : albums) {
            MediaAlbum item = adapter.mapAlbum(album);
            if (item != null) mapped.add(item);
        }
        return mapped;
    }

    private List<MediaSong> mapRemoteSongs(List<Child> songs) {
        if (songs == null || songs.isEmpty()) return Collections.emptyList();
        SubsonicSourceAdapter adapter = subsonicAdapter();
        List<MediaSong> mapped = new ArrayList<>();
        for (Child song : songs) {
            MediaSong item = adapter.mapSong(song);
            if (item != null) mapped.add(item);
        }
        return mapped;
    }

    private List<MediaArtist> mapIndexArtists(List<LibrarySearchEntry> entries) {
        if (entries == null || entries.isEmpty()) return Collections.emptyList();
        String subsonicSourceId = resolveSubsonicSourceId();
        List<MediaArtist> mapped = new ArrayList<>();
        for (LibrarySearchEntry entry : entries) {
            MediaArtist artist = LibrarySearchEntryMapper.toArtist(entry, subsonicSourceId);
            if (artist != null) mapped.add(artist);
        }
        return mapped;
    }

    private List<MediaAlbum> mapIndexAlbums(List<LibrarySearchEntry> entries) {
        if (entries == null || entries.isEmpty()) return Collections.emptyList();
        String subsonicSourceId = resolveSubsonicSourceId();
        List<MediaAlbum> mapped = new ArrayList<>();
        for (LibrarySearchEntry entry : entries) {
            MediaAlbum album = LibrarySearchEntryMapper.toAlbum(entry, subsonicSourceId);
            if (album != null) mapped.add(album);
        }
        return mapped;
    }

    private List<MediaSong> mapIndexSongs(List<LibrarySearchEntry> entries) {
        if (entries == null || entries.isEmpty()) return Collections.emptyList();
        String subsonicSourceId = resolveSubsonicSourceId();
        List<MediaSong> mapped = new ArrayList<>();
        for (LibrarySearchEntry entry : entries) {
            MediaSong song = LibrarySearchEntryMapper.toSong(entry, subsonicSourceId);
            if (song != null) mapped.add(song);
        }
        return mapped;
    }

    private List<MediaPlaylist> mapRemotePlaylists(List<Playlist> playlists) {
        if (playlists == null || playlists.isEmpty()) return Collections.emptyList();
        SubsonicSourceAdapter adapter = subsonicAdapter();
        List<MediaPlaylist> mapped = new ArrayList<>();
        for (Playlist playlist : playlists) {
            MediaPlaylist item = adapter.mapPlaylist(playlist);
            if (item != null) mapped.add(item);
        }
        return mapped;
    }

    private void emitMerged(SearchAccumulator accumulator) {
        List<MediaArtist> artists;
        List<MediaAlbum> albums;
        List<MediaSong> songs;
        synchronized (accumulator) {
            artists = MediaDedupeUtil.INSTANCE.mergeArtists(
                    accumulator.localArtists,
                    accumulator.indexArtists,
                    accumulator.remoteArtists
            );
            albums = MediaDedupeUtil.INSTANCE.mergeAlbums(
                    accumulator.localAlbums,
                    accumulator.indexAlbums,
                    accumulator.remoteAlbums
            );
            songs = MediaDedupeUtil.INSTANCE.mergeSongs(
                    accumulator.localSongs,
                    accumulator.indexSongs,
                    accumulator.remoteSongs
            );
        }

        artists = rankArtists(accumulator.query, artists);
        albums = rankAlbums(accumulator.query, albums);
        songs = rankSongs(accumulator.query, songs);

        SearchResult3 merged = new SearchResult3();
        merged.setArtists(toLegacyArtists(artists, 20));
        merged.setAlbums(toLegacyAlbums(albums, 20));
        merged.setSongs(toLegacySongs(songs, 20));
        accumulator.result.postValue(merged);
    }

    private void emitPlaylists(PlaylistAccumulator accumulator) {
        List<MediaPlaylist> merged;
        synchronized (accumulator) {
            merged = MediaDedupeUtil.INSTANCE.mergePlaylists(
                    accumulator.indexPlaylists,
                    accumulator.remotePlaylists
            );
        }
        merged = rankPlaylists(accumulator.query, merged);
        accumulator.result.postValue(toLegacyPlaylists(merged, 20));
    }

    private List<MediaArtist> rankArtists(String query, List<MediaArtist> items) {
        if (items == null || items.isEmpty()) return Collections.emptyList();
        List<MediaArtist> ranked = new ArrayList<>(items);
        ranked.sort((a, b) -> {
            int scoreA = scoreArtist(query, a);
            int scoreB = scoreArtist(query, b);
            if (scoreA != scoreB) return Integer.compare(scoreB, scoreA);
            int sourceA = sourceRank(a != null ? a.getId().getSourceType() : null);
            int sourceB = sourceRank(b != null ? b.getId().getSourceType() : null);
            if (sourceA != sourceB) return Integer.compare(sourceA, sourceB);
            return compareText(a != null ? a.getName() : null, b != null ? b.getName() : null);
        });
        return ranked;
    }

    private List<MediaAlbum> rankAlbums(String query, List<MediaAlbum> items) {
        if (items == null || items.isEmpty()) return Collections.emptyList();
        List<MediaAlbum> ranked = new ArrayList<>(items);
        ranked.sort((a, b) -> {
            int scoreA = scoreAlbum(query, a);
            int scoreB = scoreAlbum(query, b);
            if (scoreA != scoreB) return Integer.compare(scoreB, scoreA);
            int sourceA = sourceRank(a != null ? a.getId().getSourceType() : null);
            int sourceB = sourceRank(b != null ? b.getId().getSourceType() : null);
            if (sourceA != sourceB) return Integer.compare(sourceA, sourceB);
            return compareText(a != null ? a.getTitle() : null, b != null ? b.getTitle() : null);
        });
        return ranked;
    }

    private List<MediaSong> rankSongs(String query, List<MediaSong> items) {
        if (items == null || items.isEmpty()) return Collections.emptyList();
        List<MediaSong> ranked = new ArrayList<>(items);
        ranked.sort((a, b) -> {
            int scoreA = scoreSong(query, a);
            int scoreB = scoreSong(query, b);
            if (scoreA != scoreB) return Integer.compare(scoreB, scoreA);
            int sourceA = sourceRank(a != null ? a.getId().getSourceType() : null);
            int sourceB = sourceRank(b != null ? b.getId().getSourceType() : null);
            if (sourceA != sourceB) return Integer.compare(sourceA, sourceB);
            return compareText(a != null ? a.getTitle() : null, b != null ? b.getTitle() : null);
        });
        return ranked;
    }

    private List<MediaPlaylist> rankPlaylists(String query, List<MediaPlaylist> items) {
        if (items == null || items.isEmpty()) return Collections.emptyList();
        List<MediaPlaylist> ranked = new ArrayList<>(items);
        ranked.sort((a, b) -> {
            int scoreA = scorePlaylist(query, a);
            int scoreB = scorePlaylist(query, b);
            if (scoreA != scoreB) return Integer.compare(scoreB, scoreA);
            int sourceA = sourceRank(a != null ? a.getId().getSourceType() : null);
            int sourceB = sourceRank(b != null ? b.getId().getSourceType() : null);
            if (sourceA != sourceB) return Integer.compare(sourceA, sourceB);
            return compareText(a != null ? a.getName() : null, b != null ? b.getName() : null);
        });
        return ranked;
    }

    private int scoreArtist(String query, MediaArtist artist) {
        if (artist == null) return 0;
        return scoreMatch(query, artist.getName(), null, null)
                + scoreSource(artist.getId().getSourceType())
                + artist.detailScore();
    }

    private int scoreAlbum(String query, MediaAlbum album) {
        if (album == null) return 0;
        return scoreMatch(query, album.getTitle(), album.getArtist(), null)
                + scoreSource(album.getId().getSourceType())
                + album.detailScore();
    }

    private int scoreSong(String query, MediaSong song) {
        if (song == null) return 0;
        return scoreMatch(query, song.getTitle(), song.getArtist(), song.getAlbum())
                + scoreSource(song.getId().getSourceType())
                + song.detailScore();
    }

    private int scorePlaylist(String query, MediaPlaylist playlist) {
        if (playlist == null) return 0;
        return scoreMatch(query, playlist.getName(), null, null)
                + scoreSource(playlist.getId().getSourceType())
                + playlist.detailScore();
    }

    private int scoreMatch(String query, String title, String artist, String album) {
        String needle = SearchIndexUtil.normalize(query);
        if (needle.isEmpty()) return 0;
        int score = 0;
        String titleNorm = SearchIndexUtil.normalize(title);
        if (!titleNorm.isEmpty()) {
            if (titleNorm.equals(needle)) score += 60;
            else if (titleNorm.startsWith(needle)) score += 45;
            else if (titleNorm.contains(needle)) score += 25;
        }
        String artistNorm = SearchIndexUtil.normalize(artist);
        if (!artistNorm.isEmpty()) {
            if (artistNorm.equals(needle)) score += 25;
            else if (artistNorm.startsWith(needle)) score += 15;
            else if (artistNorm.contains(needle)) score += 10;
        }
        String albumNorm = SearchIndexUtil.normalize(album);
        if (!albumNorm.isEmpty()) {
            if (albumNorm.equals(needle)) score += 15;
            else if (albumNorm.startsWith(needle)) score += 10;
            else if (albumNorm.contains(needle)) score += 5;
        }
        return score;
    }

    private int scoreSource(MediaSourceType type) {
        if (type == null) return 0;
        int rank = SearchIndexUtil.sourcePriority(type.getId());
        return Math.max(0, 6 - rank);
    }

    private int sourceRank(MediaSourceType type) {
        if (type == null) return Integer.MAX_VALUE;
        return SearchIndexUtil.sourcePriority(type.getId());
    }

    private int compareText(String left, String right) {
        String a = left != null ? left : "";
        String b = right != null ? right : "";
        return a.compareToIgnoreCase(b);
    }

    private List<ArtistID3> toLegacyArtists(List<MediaArtist> items, int limit) {
        if (items == null || items.isEmpty()) return Collections.emptyList();
        List<ArtistID3> mapped = new ArrayList<>();
        for (MediaArtist item : items) {
            if (mapped.size() >= limit) break;
            ArtistID3 legacy = LegacyMediaMapper.INSTANCE.toArtistId3(item);
            if (legacy != null) mapped.add(legacy);
        }
        return mapped;
    }

    private List<AlbumID3> toLegacyAlbums(List<MediaAlbum> items, int limit) {
        if (items == null || items.isEmpty()) return Collections.emptyList();
        List<AlbumID3> mapped = new ArrayList<>();
        for (MediaAlbum item : items) {
            if (mapped.size() >= limit) break;
            AlbumID3 legacy = LegacyMediaMapper.INSTANCE.toAlbumId3(item);
            if (legacy != null) mapped.add(legacy);
        }
        return mapped;
    }

    private List<Child> toLegacySongs(List<MediaSong> items, int limit) {
        if (items == null || items.isEmpty()) return Collections.emptyList();
        List<Child> mapped = new ArrayList<>();
        for (MediaSong item : items) {
            if (mapped.size() >= limit) break;
            Child legacy = LegacyMediaMapper.INSTANCE.toSong(item);
            if (legacy != null) mapped.add(legacy);
        }
        return mapped;
    }

    private List<Playlist> toLegacyPlaylists(List<MediaPlaylist> items, int limit) {
        if (items == null || items.isEmpty()) return Collections.emptyList();
        List<Playlist> mapped = new ArrayList<>();
        for (MediaPlaylist item : items) {
            if (mapped.size() >= limit) break;
            Playlist legacy = LegacyMediaMapper.INSTANCE.toPlaylist(item);
            if (legacy != null) mapped.add(legacy);
        }
        return mapped;
    }

    private void loadCachedPlaylistsDomain(String query, PlaylistAccumulator accumulator) {
        if (query == null || query.trim().isEmpty()) return;
        Type playlistType = new TypeToken<List<Playlist>>() {}.getType();
        cacheRepository.loadOrNull("playlists", playlistType, new CacheRepository.CacheResult<List<Playlist>>() {
            @Override
            public void onLoaded(List<Playlist> playlists) {
                synchronized (accumulator) {
                    accumulator.remotePlaylists = mapRemotePlaylists(filterPlaylists(playlists, query));
                }
                emitPlaylists(accumulator);
            }
        });
    }

    private static class SearchAccumulator {
        private final String query;
        private final MutableLiveData<SearchResult3> result;
        private List<MediaArtist> localArtists = Collections.emptyList();
        private List<MediaAlbum> localAlbums = Collections.emptyList();
        private List<MediaSong> localSongs = Collections.emptyList();
        private List<MediaArtist> indexArtists = Collections.emptyList();
        private List<MediaAlbum> indexAlbums = Collections.emptyList();
        private List<MediaSong> indexSongs = Collections.emptyList();
        private List<MediaArtist> remoteArtists = Collections.emptyList();
        private List<MediaAlbum> remoteAlbums = Collections.emptyList();
        private List<MediaSong> remoteSongs = Collections.emptyList();

        private SearchAccumulator(String query, MutableLiveData<SearchResult3> result) {
            this.query = query;
            this.result = result;
        }
    }

    private static class PlaylistAccumulator {
        private final String query;
        private final MutableLiveData<List<Playlist>> result;
        private List<MediaPlaylist> indexPlaylists = Collections.emptyList();
        private List<MediaPlaylist> remotePlaylists = Collections.emptyList();

        private PlaylistAccumulator(String query, MutableLiveData<List<Playlist>> result) {
            this.query = query;
            this.result = result;
        }
    }

    private List<ArtistID3> filterArtists(List<ArtistID3> artists, String query) {
        if (artists == null || artists.isEmpty()) return Collections.emptyList();
        String needle = query.toLowerCase().trim();
        List<ArtistID3> filtered = new ArrayList<>();
        for (ArtistID3 artist : artists) {
            if (artist == null || artist.getName() == null) continue;
            if (artist.getName().toLowerCase().contains(needle)) {
                filtered.add(artist);
            }
        }
        return filtered.size() > 20 ? filtered.subList(0, 20) : filtered;
    }

    private List<AlbumID3> filterAlbums(List<AlbumID3> albums, String query) {
        if (albums == null || albums.isEmpty()) return Collections.emptyList();
        String needle = query.toLowerCase().trim();
        List<AlbumID3> filtered = new ArrayList<>();
        for (AlbumID3 album : albums) {
            if (album == null) continue;
            String name = album.getName();
            String artist = album.getArtist();
            if ((name != null && name.toLowerCase().contains(needle)) ||
                    (artist != null && artist.toLowerCase().contains(needle))) {
                filtered.add(album);
            }
        }
        return filtered.size() > 20 ? filtered.subList(0, 20) : filtered;
    }

    private List<Child> filterSongs(List<Child> songs, String query) {
        if (songs == null || songs.isEmpty()) return Collections.emptyList();
        String needle = query.toLowerCase().trim();
        List<Child> filtered = new ArrayList<>();
        for (Child song : songs) {
            if (song == null) continue;
            String title = song.getTitle();
            String artist = song.getArtist();
            String album = song.getAlbum();
            if ((title != null && title.toLowerCase().contains(needle)) ||
                    (artist != null && artist.toLowerCase().contains(needle)) ||
                    (album != null && album.toLowerCase().contains(needle))) {
                filtered.add(song);
            }
        }
        return filtered.size() > 20 ? filtered.subList(0, 20) : filtered;
    }

    private List<Playlist> filterPlaylists(List<Playlist> playlists, String query) {
        if (playlists == null || playlists.isEmpty()) return Collections.emptyList();
        String needle = query.toLowerCase().trim();
        List<Playlist> filtered = new ArrayList<>();
        for (Playlist playlist : playlists) {
            if (playlist == null || playlist.getName() == null) continue;
            if (playlist.getName().toLowerCase().contains(needle)) {
                filtered.add(playlist);
            }
        }
        return filtered.size() > 20 ? filtered.subList(0, 20) : filtered;
    }

    private List<ArtistID3> mergeArtists(List<ArtistID3> base, List<ArtistID3> extra) {
        List<ArtistID3> merged = new ArrayList<>();
        if (base != null) merged.addAll(base);
        if (extra == null) return merged;
        for (ArtistID3 artist : extra) {
            if (artist == null) continue;
            boolean exists = false;
            for (ArtistID3 existing : merged) {
                if (existing != null && existing.getId() != null && existing.getId().equals(artist.getId())) {
                    exists = true;
                    break;
                }
            }
            if (!exists) merged.add(artist);
        }
        return merged;
    }

    private List<AlbumID3> mergeAlbums(List<AlbumID3> base, List<AlbumID3> extra) {
        List<AlbumID3> merged = new ArrayList<>();
        if (base != null) merged.addAll(base);
        if (extra == null) return merged;
        for (AlbumID3 album : extra) {
            if (album == null) continue;
            boolean exists = false;
            for (AlbumID3 existing : merged) {
                if (existing != null && existing.getId() != null && existing.getId().equals(album.getId())) {
                    exists = true;
                    break;
                }
            }
            if (!exists) merged.add(album);
        }
        return merged;
    }

    private List<Child> mergeSongs(List<Child> base, List<Child> extra) {
        List<Child> merged = new ArrayList<>();
        if (base != null) merged.addAll(base);
        if (extra == null) return merged;
        for (Child song : extra) {
            if (song == null) continue;
            boolean exists = false;
            for (Child existing : merged) {
                if (existing != null && existing.getId() != null && existing.getId().equals(song.getId())) {
                    exists = true;
                    break;
                }
            }
            if (!exists) merged.add(song);
        }
        return merged;
    }

    private List<ArtistID3> dedupeArtistsBySignature(List<ArtistID3> artists) {
        if (artists == null || artists.isEmpty()) return Collections.emptyList();
        Map<String, ArtistID3> deduped = new LinkedHashMap<>();
        for (ArtistID3 artist : artists) {
            if (artist == null) continue;
            String key = SearchIndexUtil.normalize(artist.getName());
            if (key.isEmpty()) {
                key = artist.getId() != null ? artist.getId() : String.valueOf(artist.hashCode());
            }
            ArtistID3 existing = deduped.get(key);
            if (existing == null || shouldPreferArtist(artist, existing)) {
                deduped.put(key, artist);
            }
        }
        return new ArrayList<>(deduped.values());
    }

    private List<AlbumID3> dedupeAlbumsBySignature(List<AlbumID3> albums) {
        if (albums == null || albums.isEmpty()) return Collections.emptyList();
        Map<String, AlbumID3> deduped = new LinkedHashMap<>();
        for (AlbumID3 album : albums) {
            if (album == null) continue;
            String key = SearchIndexUtil.normalize((album.getName() != null ? album.getName() : "") + "|" + (album.getArtist() != null ? album.getArtist() : ""));
            if (key.isEmpty()) {
                key = album.getId() != null ? album.getId() : String.valueOf(album.hashCode());
            }
            AlbumID3 existing = deduped.get(key);
            if (existing == null || shouldPreferAlbum(album, existing)) {
                deduped.put(key, album);
            }
        }
        return new ArrayList<>(deduped.values());
    }

    private List<Child> dedupeSongsBySignature(List<Child> songs) {
        if (songs == null || songs.isEmpty()) return Collections.emptyList();
        Map<String, Child> deduped = new LinkedHashMap<>();
        for (Child song : songs) {
            if (song == null) continue;
            String key = SearchIndexUtil.normalize((song.getTitle() != null ? song.getTitle() : "") + "|" + (song.getArtist() != null ? song.getArtist() : "") + "|" + (song.getAlbum() != null ? song.getAlbum() : ""));
            if (key.isEmpty()) {
                key = song.getId() != null ? song.getId() : String.valueOf(song.hashCode());
            }
            Child existing = deduped.get(key);
            if (existing == null || shouldPreferSong(song, existing)) {
                deduped.put(key, song);
            }
        }
        return new ArrayList<>(deduped.values());
    }

    private boolean shouldPreferArtist(ArtistID3 candidate, ArtistID3 existing) {
        int candidateRank = SearchIndexUtil.sourcePriority(resolveArtistSource(candidate));
        int existingRank = SearchIndexUtil.sourcePriority(resolveArtistSource(existing));
        if (candidateRank != existingRank) {
            return candidateRank < existingRank;
        }
        boolean candidateHasCover = candidate.getCoverArtId() != null && !candidate.getCoverArtId().isEmpty();
        boolean existingHasCover = existing.getCoverArtId() != null && !existing.getCoverArtId().isEmpty();
        if (candidateHasCover != existingHasCover) {
            return candidateHasCover;
        }
        return false;
    }

    private boolean shouldPreferAlbum(AlbumID3 candidate, AlbumID3 existing) {
        int candidateRank = SearchIndexUtil.sourcePriority(resolveAlbumSource(candidate));
        int existingRank = SearchIndexUtil.sourcePriority(resolveAlbumSource(existing));
        if (candidateRank != existingRank) {
            return candidateRank < existingRank;
        }
        boolean candidateHasCover = candidate.getCoverArtId() != null && !candidate.getCoverArtId().isEmpty();
        boolean existingHasCover = existing.getCoverArtId() != null && !existing.getCoverArtId().isEmpty();
        if (candidateHasCover != existingHasCover) {
            return candidateHasCover;
        }
        return false;
    }

    private boolean shouldPreferSong(Child candidate, Child existing) {
        int candidateRank = SearchIndexUtil.sourcePriority(resolveSongSource(candidate));
        int existingRank = SearchIndexUtil.sourcePriority(resolveSongSource(existing));
        if (candidateRank != existingRank) {
            return candidateRank < existingRank;
        }
        boolean candidateHasCover = candidate.getCoverArtId() != null && !candidate.getCoverArtId().isEmpty();
        boolean existingHasCover = existing.getCoverArtId() != null && !existing.getCoverArtId().isEmpty();
        if (candidateHasCover != existingHasCover) {
            return candidateHasCover;
        }
        return false;
    }

    private String resolveArtistSource(ArtistID3 artist) {
        if (artist == null) return SearchIndexUtil.SOURCE_SUBSONIC;
        String id = artist.getId();
        if (SearchIndexUtil.isJellyfinTagged(id)) return SearchIndexUtil.SOURCE_JELLYFIN;
        if (LocalMusicRepository.isLocalArtistId(id)) return SearchIndexUtil.SOURCE_LOCAL;
        return SearchIndexUtil.SOURCE_SUBSONIC;
    }

    private String resolveAlbumSource(AlbumID3 album) {
        if (album == null) return SearchIndexUtil.SOURCE_SUBSONIC;
        String id = album.getId();
        if (SearchIndexUtil.isJellyfinTagged(id)) return SearchIndexUtil.SOURCE_JELLYFIN;
        if (LocalMusicRepository.isLocalAlbumId(id)) return SearchIndexUtil.SOURCE_LOCAL;
        return SearchIndexUtil.SOURCE_SUBSONIC;
    }

    private String resolveSongSource(Child song) {
        if (song == null) return SearchIndexUtil.SOURCE_SUBSONIC;
        String id = song.getId();
        if (SearchIndexUtil.isJellyfinTagged(id)) return SearchIndexUtil.SOURCE_JELLYFIN;
        if (LocalMusicRepository.isLocalSong(song)) return SearchIndexUtil.SOURCE_LOCAL;
        return SearchIndexUtil.SOURCE_SUBSONIC;
    }

    public void insert(RecentSearch recentSearch) {
        AppExecutors.io().execute(() -> recentSearchDao.insert(recentSearch));
    }

    public void delete(RecentSearch recentSearch) {
        AppExecutors.io().execute(() -> recentSearchDao.delete(recentSearch));
    }

    public List<String> getRecentSearchSuggestion() {
        Future<List<String>> future = AppExecutors.io().submit(recentSearchDao::getRecent);
        try {
            List<String> recent = future.get();
            return recent != null ? recent : new ArrayList<>();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            return new ArrayList<>();
        }
    }
}
