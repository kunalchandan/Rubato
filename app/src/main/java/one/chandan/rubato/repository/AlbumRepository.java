package one.chandan.rubato.repository;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import one.chandan.rubato.App;
import one.chandan.rubato.interfaces.DecadesCallback;
import one.chandan.rubato.interfaces.MediaCallback;
import one.chandan.rubato.repository.LocalMusicRepository;
import one.chandan.rubato.subsonic.base.ApiResponse;
import one.chandan.rubato.subsonic.models.AlbumID3;
import one.chandan.rubato.subsonic.models.AlbumInfo;
import one.chandan.rubato.subsonic.models.ArtistID3;
import one.chandan.rubato.subsonic.models.Child;
import one.chandan.rubato.subsonic.models.SubsonicResponse;
import one.chandan.rubato.util.JellyfinTagUtil;
import one.chandan.rubato.util.LibraryDedupeUtil;
import one.chandan.rubato.util.OfflinePolicy;
import one.chandan.rubato.util.SearchIndexUtil;
import one.chandan.rubato.util.Constants;
import one.chandan.rubato.model.SearchSongLite;
import com.google.gson.reflect.TypeToken;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.lang.reflect.Type;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AlbumRepository {
    private final CacheRepository cacheRepository = new CacheRepository();
    private final JellyfinCacheRepository jellyfinCacheRepository = new JellyfinCacheRepository();
    private final LibrarySearchIndexRepository searchIndexRepository = new LibrarySearchIndexRepository();
    public MutableLiveData<List<AlbumID3>> getAlbums(String type, int size, Integer fromYear, Integer toYear) {
        MutableLiveData<List<AlbumID3>> listLiveAlbums = new MutableLiveData<>(new ArrayList<>());
        String cacheKey = "albums_" + type + "_" + size + "_" + fromYear + "_" + toYear;

        loadCachedAlbumsFromAll(type, size, fromYear, toYear, listLiveAlbums);
        loadCachedAlbums(cacheKey, type, size, listLiveAlbums);

        if (OfflinePolicy.isOffline()) {
            loadCachedAlbumsFromAll(type, size, fromYear, toYear, listLiveAlbums);
            return listLiveAlbums;
        }

        App.getSubsonicClientInstance(false)
                .getAlbumSongListClient()
                .getAlbumList2(type, size, 0, fromYear, toYear)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getAlbumList2() != null && response.body().getSubsonicResponse().getAlbumList2().getAlbums() != null) {
                            List<AlbumID3> albums = response.body().getSubsonicResponse().getAlbumList2().getAlbums();
                            cacheRepository.save(cacheKey, albums);
                            LocalMusicRepository.appendLocalAlbums(App.getContext(), albums, listLiveAlbums::postValue);
                            return;
                        }

                        loadCachedAlbums(cacheKey, type, size, listLiveAlbums);
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        loadCachedAlbums(cacheKey, type, size, listLiveAlbums);

                    }
                });

        return listLiveAlbums;
    }

    public MutableLiveData<List<AlbumID3>> getStarredAlbums(boolean random, int size) {
        MutableLiveData<List<AlbumID3>> starredAlbums = new MutableLiveData<>(new ArrayList<>());
        String cacheKey = "starred_albums";

        loadCachedStarredAlbums(cacheKey, starredAlbums, random, size);

        if (OfflinePolicy.isOffline()) {
            return starredAlbums;
        }

        App.getSubsonicClientInstance(false)
                .getAlbumSongListClient()
                .getStarred2()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            SubsonicResponse subsonicResponse = response.body().getSubsonicResponse();
                            List<AlbumID3> albums = null;

                            if (subsonicResponse != null && subsonicResponse.getStarred2() != null) {
                                albums = subsonicResponse.getStarred2().getAlbums();
                            }

                            if (albums == null && subsonicResponse != null && subsonicResponse.getStarred() != null) {
                                albums = mapLegacyStarredAlbums(subsonicResponse.getStarred().getAlbums());
                            }

                            if (albums != null) {
                                if (random) {
                                    Collections.shuffle(albums);
                                    int limit = Math.min(size, albums.size());
                                    starredAlbums.setValue(new ArrayList<>(albums.subList(0, limit)));
                                } else {
                                    starredAlbums.setValue(albums);
                                }
                                cacheRepository.save(cacheKey, albums);
                            }
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {

                    }
                });

        return starredAlbums;
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

    public MutableLiveData<List<Child>> getAlbumTracks(String id) {
        return getAlbumTracks(id, null, null);
    }

    public MutableLiveData<List<Child>> getAlbumTracks(AlbumID3 album) {
        if (album == null) {
            return new MutableLiveData<>(new ArrayList<>());
        }
        return getAlbumTracks(album.getId(), album.getName(), album.getArtist());
    }

    public MutableLiveData<List<Child>> getAlbumTracks(String id, String albumName, String artistName) {
        MutableLiveData<List<Child>> albumTracks = new MutableLiveData<>();
        String cacheKey = id != null ? "album_tracks_" + id : null;

        if (id != null && SearchIndexUtil.isJellyfinTagged(id)) {
            jellyfinCacheRepository.loadSongsForAlbum(id, songs -> {
                if (songs != null && !songs.isEmpty()) {
                    albumTracks.postValue(songs);
                    return;
                }
                loadCachedAlbumTracksByIdOrMetadata(id, albumName, artistName, albumTracks);
            });
            return albumTracks;
        }

        if (id != null && LocalMusicRepository.isLocalAlbumId(id)) {
            LocalMusicRepository.getLocalAlbumSongs(App.getContext(), id, songs -> {
                if (songs != null && !songs.isEmpty()) {
                    albumTracks.postValue(songs);
                    return;
                }
                loadLocalAlbumTracksByMetadata(id, albumName, artistName, albumTracks);
            });
            return albumTracks;
        }

        if (OfflinePolicy.isOffline()) {
            if (cacheKey != null) {
                loadCachedAlbumTracks(cacheKey, albumTracks);
            }
            loadLocalAlbumTracksByMetadata(id, albumName, artistName, albumTracks);
            return albumTracks;
        }

        if (id == null || id.trim().isEmpty()) {
            loadLocalAlbumTracksByMetadata(id, albumName, artistName, albumTracks);
            return albumTracks;
        }

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getAlbum(id)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        List<Child> tracks = new ArrayList<>();

                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getAlbum() != null) {
                            if (response.body().getSubsonicResponse().getAlbum().getSongs() != null) {
                                tracks.addAll(response.body().getSubsonicResponse().getAlbum().getSongs());
                            }
                        }

                        if (!tracks.isEmpty()) {
                            albumTracks.setValue(tracks);
                        } else {
                            loadRemoteAlbumTracksBySearch(id, albumName, artistName, albumTracks, () ->
                                    loadLocalAlbumTracksByMetadata(id, albumName, artistName, albumTracks));
                        }
                        if (cacheKey != null) {
                            cacheRepository.save(cacheKey, tracks);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        if (cacheKey != null) {
                            loadCachedAlbumTracks(cacheKey, albumTracks);
                        }
                        loadRemoteAlbumTracksBySearch(id, albumName, artistName, albumTracks, () ->
                                loadLocalAlbumTracksByMetadata(id, albumName, artistName, albumTracks));
                    }
                });

        return albumTracks;
    }

    private void loadLocalAlbumTracksByMetadata(String albumId, String albumName, String artistName, MutableLiveData<List<Child>> albumTracks) {
        if (TextUtils.isEmpty(albumName)) {
            if (!TextUtils.isEmpty(albumId)) {
                loadCachedAlbumTracksByIdOrMetadata(albumId, null, artistName, albumTracks);
                return;
            }
            postEmptyIfNeeded(albumTracks);
            return;
        }
        String effectiveArtist = isGenericArtistName(artistName) ? null : artistName;
        LocalMusicRepository.getLocalAlbumSongsByMetadata(App.getContext(), albumName, effectiveArtist, songs -> {
            if (songs != null && !songs.isEmpty()) {
                postIfEmpty(albumTracks, songs);
                return;
            }
            LocalMusicRepository.getLocalAlbumSongsByMetadata(App.getContext(), albumName, null, fallback -> {
                if (fallback != null && !fallback.isEmpty()) {
                    postIfEmpty(albumTracks, fallback);
                    return;
                }
                loadCachedAlbumTracksByIdOrMetadata(albumId, albumName, effectiveArtist, albumTracks);
            });
        });
    }

    private void postIfEmpty(MutableLiveData<List<Child>> target, List<Child> songs) {
        if (target == null) return;
        List<Child> current = target.getValue();
        if (current != null && !current.isEmpty()) {
            return;
        }
        target.postValue(songs);
    }

    private void postEmptyIfNeeded(MutableLiveData<List<Child>> target) {
        if (target == null) return;
        List<Child> current = target.getValue();
        if (current != null && !current.isEmpty()) {
            return;
        }
        target.postValue(new ArrayList<>());
    }

    private void loadCachedAlbumTracksByIdOrMetadata(String albumId, String albumName, String artistName, MutableLiveData<List<Child>> albumTracks) {
        Type type = new TypeToken<List<Child>>() {
        }.getType();
        String albumKey = normalizeMatchKey(albumName);
        String artistKey = normalizeMatchKey(artistName);
        boolean matchArtist = !TextUtils.isEmpty(artistKey);
        String rawAlbumId = JellyfinTagUtil.toRaw(albumId);
        cacheRepository.loadOrNull("songs_all", type, new CacheRepository.CacheResult<List<Child>>() {
            @Override
            public void onLoaded(List<Child> cached) {
                List<Child> cachedMatches = filterSongsByIdOrMetadata(cached, albumId, rawAlbumId, albumKey, artistKey, matchArtist);
                if (!cachedMatches.isEmpty()) {
                    postIfEmpty(albumTracks, cachedMatches);
                    return;
                }
                if (matchArtist) {
                    List<Child> relaxed = filterSongsByMetadata(cached, albumKey, "", false);
                    if (!relaxed.isEmpty()) {
                        postIfEmpty(albumTracks, relaxed);
                        return;
                    }
                }
                jellyfinCacheRepository.loadAllSongs(jellySongs -> {
                    List<Child> jellyMatches = filterSongsByIdOrMetadata(jellySongs, albumId, rawAlbumId, albumKey, artistKey, matchArtist);
                    if (!jellyMatches.isEmpty()) {
                        postIfEmpty(albumTracks, jellyMatches);
                        return;
                    }
                    if (matchArtist) {
                        List<Child> relaxed = filterSongsByMetadata(jellySongs, albumKey, "", false);
                        if (!relaxed.isEmpty()) {
                            postIfEmpty(albumTracks, relaxed);
                            return;
                        }
                    }
                    loadIndexAlbumTracks(albumId, albumName, artistName, albumTracks);
                });
            }
        });
    }

    private void loadIndexAlbumTracks(String albumId, String albumName, String artistName, MutableLiveData<List<Child>> albumTracks) {
        searchIndexRepository.getSongsByAlbum(albumId, albumName, artistName, entries -> {
            List<Child> mapped = mapSearchSongs(entries);
            if (!mapped.isEmpty()) {
                postIfEmpty(albumTracks, mapped);
                return;
            }
            postEmptyIfNeeded(albumTracks);
        });
    }

    private void loadRemoteAlbumTracksBySearch(String albumId,
                                               String albumName,
                                               String artistName,
                                               MutableLiveData<List<Child>> albumTracks,
                                               Runnable onEmpty) {
        if (OfflinePolicy.isOffline()) {
            if (onEmpty != null) onEmpty.run();
            return;
        }
        String query = albumName != null ? albumName.trim() : "";
        if (query.isEmpty()) {
            if (onEmpty != null) onEmpty.run();
            return;
        }
        if (artistName != null && !artistName.trim().isEmpty()) {
            query = query + " " + artistName.trim();
        }
        String albumKey = normalizeMatchKey(albumName);
        String artistKey = normalizeMatchKey(artistName);
        boolean matchArtist = !TextUtils.isEmpty(artistKey);
        String rawAlbumId = JellyfinTagUtil.toRaw(albumId);
        App.getSubsonicClientInstance(false)
                .getSearchingClient()
                .search3(query, 200, 0, 0)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        List<Child> candidates = new ArrayList<>();
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getSubsonicResponse().getSearchResult3() != null
                                && response.body().getSubsonicResponse().getSearchResult3().getSongs() != null) {
                            candidates.addAll(response.body().getSubsonicResponse().getSearchResult3().getSongs());
                        }
                        List<Child> filtered = filterSongsByIdOrMetadata(candidates, albumId, rawAlbumId, albumKey, artistKey, matchArtist);
                        if (!filtered.isEmpty()) {
                            postIfEmpty(albumTracks, filtered);
                            return;
                        }
                        if (onEmpty != null) onEmpty.run();
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        if (onEmpty != null) onEmpty.run();
                    }
                });
    }

    private List<Child> mapSearchSongs(List<SearchSongLite> entries) {
        if (entries == null || entries.isEmpty()) {
            return new ArrayList<>();
        }
        List<Child> mapped = new ArrayList<>();
        for (SearchSongLite entry : entries) {
            if (entry == null) continue;
            if (SearchIndexUtil.SOURCE_LOCAL.equals(entry.getSource())) {
                continue;
            }
            String id = entry.getItemId();
            if (id == null || id.trim().isEmpty()) {
                continue;
            }
            Child song = new Child(id);
            song.setTitle(entry.getTitle());
            song.setArtist(entry.getArtist());
            song.setAlbum(entry.getAlbum());
            song.setAlbumId(entry.getAlbumId());
            song.setArtistId(entry.getArtistId());
            song.setCoverArtId(entry.getCoverArt());
            song.setType(Constants.MEDIA_TYPE_MUSIC);
            mapped.add(song);
        }
        return LibraryDedupeUtil.dedupeSongsBySignature(mapped);
    }

    private List<Child> filterSongsByIdOrMetadata(List<Child> songs,
                                                  String albumId,
                                                  String rawAlbumId,
                                                  String albumKey,
                                                  String artistKey,
                                                  boolean matchArtist) {
        if (songs == null || songs.isEmpty()) {
            return new ArrayList<>();
        }
        if (!TextUtils.isEmpty(albumId) || !TextUtils.isEmpty(rawAlbumId)) {
            List<Child> byId = new ArrayList<>();
            for (Child song : songs) {
                if (song == null) continue;
                String candidate = song.getAlbumId();
                if (candidate == null) continue;
                if (!TextUtils.isEmpty(albumId) && albumId.equals(candidate)) {
                    byId.add(song);
                    continue;
                }
                if (!TextUtils.isEmpty(rawAlbumId) && rawAlbumId.equals(candidate)) {
                    byId.add(song);
                }
            }
            if (!byId.isEmpty()) {
                return LibraryDedupeUtil.dedupeSongsBySignature(byId);
            }
        }
        return filterSongsByMetadata(songs, albumKey, artistKey, matchArtist);
    }

    private List<Child> filterSongsByMetadata(List<Child> songs, String albumKey, String artistKey, boolean matchArtist) {
        if (songs == null || songs.isEmpty() || TextUtils.isEmpty(albumKey)) {
            return new ArrayList<>();
        }
        List<Child> filtered = new ArrayList<>();
        for (Child song : songs) {
            if (song == null) continue;
            String candidateAlbum = normalizeMatchKey(song.getAlbum());
            if (!albumKey.equals(candidateAlbum)) continue;
            if (matchArtist) {
                String candidateArtist = normalizeMatchKey(song.getArtist());
                if (!artistKey.equals(candidateArtist)) continue;
            }
            filtered.add(song);
        }
        return LibraryDedupeUtil.dedupeSongsBySignature(filtered);
    }

    private String normalizeMatchKey(String value) {
        if (value == null) return "";
        String normalized = SearchIndexUtil.normalize(value);
        if (normalized.isEmpty()) return "";
        return normalized.replaceAll("[^a-z0-9 ]", "").trim();
    }

    private boolean isGenericArtistName(String artistName) {
        if (TextUtils.isEmpty(artistName)) return true;
        String normalized = SearchIndexUtil.normalize(artistName);
        return "various artists".equals(normalized) || "various".equals(normalized) || "va".equals(normalized);
    }

    public MutableLiveData<List<AlbumID3>> getArtistAlbums(ArtistID3 artist) {
        MutableLiveData<List<AlbumID3>> artistsAlbum = new MutableLiveData<>(new ArrayList<>());
        String artistId = artist != null ? artist.getId() : null;
        String artistName = artist != null ? artist.getName() : null;
        String cacheKey = "artist_albums_" + artistId;

        if (SearchIndexUtil.isJellyfinTagged(artistId)) {
            jellyfinCacheRepository.loadAlbumsForArtist(artistId, albums -> artistsAlbum.postValue(albums));
            return artistsAlbum;
        }

        if (LocalMusicRepository.isLocalArtistId(artistId)) {
            LocalMusicRepository.getLocalArtistAlbums(App.getContext(), artistId, artistsAlbum::postValue);
            return artistsAlbum;
        }

        if (OfflinePolicy.isOffline()) {
            loadCachedArtistAlbumsFromAll(artistId, artistsAlbum);
            mergeJellyfinArtistAlbums(artistName, artistsAlbum);
            return artistsAlbum;
        }

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getArtist(artistId)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getArtist() != null && response.body().getSubsonicResponse().getArtist().getAlbums() != null) {
                            List<AlbumID3> albums = response.body().getSubsonicResponse().getArtist().getAlbums();
                            albums.sort(Comparator.comparing(AlbumID3::getYear));
                            Collections.reverse(albums);
                            artistsAlbum.setValue(albums);
                            cacheRepository.save(cacheKey, albums);
                            mergeJellyfinArtistAlbums(artistName, artistsAlbum);
                            return;
                        }

                        loadCachedArtistAlbums(cacheKey, artistsAlbum);
                        mergeJellyfinArtistAlbums(artistName, artistsAlbum);
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        loadCachedArtistAlbums(cacheKey, artistsAlbum);
                        mergeJellyfinArtistAlbums(artistName, artistsAlbum);

                    }
                });

        return artistsAlbum;
    }

    public MutableLiveData<AlbumID3> getAlbum(String id) {
        MutableLiveData<AlbumID3> album = new MutableLiveData<>();
        String cacheKey = "album_" + id;

        if (SearchIndexUtil.isJellyfinTagged(id)) {
            jellyfinCacheRepository.loadAlbum(id, album::postValue);
            return album;
        }

        if (LocalMusicRepository.isLocalAlbumId(id)) {
            LocalMusicRepository.getLocalAlbum(App.getContext(), id, album::postValue);
            return album;
        }

        if (OfflinePolicy.isOffline()) {
            loadCachedAlbum(cacheKey, album);
            loadCachedAlbumFromAll(id, album);
            return album;
        }

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getAlbum(id)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getAlbum() != null) {
                            AlbumID3 albumResponse = response.body().getSubsonicResponse().getAlbum();
                            album.setValue(albumResponse);
                            cacheRepository.save(cacheKey, albumResponse);
                            return;
                        }

                        loadCachedAlbum(cacheKey, album);
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        loadCachedAlbum(cacheKey, album);

                    }
                });

        return album;
    }

    public MutableLiveData<AlbumInfo> getAlbumInfo(String id) {
        MutableLiveData<AlbumInfo> albumInfo = new MutableLiveData<>();
        String cacheKey = "album_info_" + id;

        if (SearchIndexUtil.isJellyfinTagged(id)) {
            albumInfo.setValue(null);
            return albumInfo;
        }

        if (LocalMusicRepository.isLocalAlbumId(id)) {
            albumInfo.setValue(null);
            return albumInfo;
        }

        if (OfflinePolicy.isOffline()) {
            loadCachedAlbumInfo(cacheKey, albumInfo);
            return albumInfo;
        }

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getAlbumInfo2(id)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getAlbumInfo() != null) {
                            AlbumInfo info = response.body().getSubsonicResponse().getAlbumInfo();
                            albumInfo.setValue(info);
                            cacheRepository.save(cacheKey, info);
                            return;
                        }

                        loadCachedAlbumInfo(cacheKey, albumInfo);
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        loadCachedAlbumInfo(cacheKey, albumInfo);

                    }
                });

        return albumInfo;
    }

    public void getInstantMix(AlbumID3 album, int count, MediaCallback callback) {
        if (album != null && LocalMusicRepository.isLocalAlbumId(album.getId())) {
            LocalMusicRepository.getLocalAlbumSongs(App.getContext(), album.getId(), callback::onLoadMedia);
            return;
        }
        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getSimilarSongs2(album.getId(), count)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        List<Child> songs = new ArrayList<>();

                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getSimilarSongs2() != null) {
                            songs.addAll(response.body().getSubsonicResponse().getSimilarSongs2().getSongs());
                        }

                        callback.onLoadMedia(songs);
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        callback.onLoadMedia(new ArrayList<>());
                    }
                });
    }

    public MutableLiveData<List<Integer>> getDecades() {
        MutableLiveData<List<Integer>> decades = new MutableLiveData<>();

        if (OfflinePolicy.isOffline()) {
            loadCachedDecadesFromAll(decades);
            return decades;
        }

        getFirstAlbum(new DecadesCallback() {
            @Override
            public void onLoadYear(int first) {
                getLastAlbum(new DecadesCallback() {
                    @Override
                    public void onLoadYear(int last) {
                        if (first != -1 && last != -1) {
                            List<Integer> decadeList = new ArrayList();

                            int startDecade = first - (first % 10);
                            int lastDecade = last - (last % 10);

                            while (startDecade <= lastDecade) {
                                decadeList.add(startDecade);
                                startDecade = startDecade + 10;
                            }

                            decades.setValue(decadeList);
                        }
                    }
                });
            }
        });

        return decades;
    }

    private void getFirstAlbum(DecadesCallback callback) {
        App.getSubsonicClientInstance(false)
                .getAlbumSongListClient()
                .getAlbumList2("byYear", 1, 0, 1900, Calendar.getInstance().get(Calendar.YEAR))
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getAlbumList2() != null && response.body().getSubsonicResponse().getAlbumList2().getAlbums() != null && !response.body().getSubsonicResponse().getAlbumList2().getAlbums().isEmpty()) {
                            callback.onLoadYear(response.body().getSubsonicResponse().getAlbumList2().getAlbums().get(0).getYear());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        callback.onLoadYear(-1);
                    }
                });
    }

    private void getLastAlbum(DecadesCallback callback) {
        App.getSubsonicClientInstance(false)
                .getAlbumSongListClient()
                .getAlbumList2("byYear", 1, 0, Calendar.getInstance().get(Calendar.YEAR), 1900)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getAlbumList2() != null && response.body().getSubsonicResponse().getAlbumList2().getAlbums() != null) {
                            if (!response.body().getSubsonicResponse().getAlbumList2().getAlbums().isEmpty() && !response.body().getSubsonicResponse().getAlbumList2().getAlbums().isEmpty()) {
                                callback.onLoadYear(response.body().getSubsonicResponse().getAlbumList2().getAlbums().get(0).getYear());
                            } else {
                                callback.onLoadYear(-1);
                            }
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        callback.onLoadYear(-1);
                    }
                });
    }

    private void loadCachedAlbums(String cacheKey, String type, int size, MutableLiveData<List<AlbumID3>> listLiveAlbums) {
        List<AlbumID3> current = listLiveAlbums.getValue();
        if (current != null && !current.isEmpty()) {
            return;
        }
        Type typeToken = new TypeToken<List<AlbumID3>>() {
        }.getType();
        cacheRepository.load(cacheKey, typeToken, new CacheRepository.CacheResult<List<AlbumID3>>() {
            @Override
            public void onLoaded(List<AlbumID3> albums) {
                LocalMusicRepository.appendLocalAlbums(App.getContext(), albums, merged -> {
                    List<AlbumID3> ordered = merged != null ? new ArrayList<>(merged) : new ArrayList<>();
                    applyAlbumOrdering(type, ordered);
                    if (size > 0 && ordered.size() > size) {
                        ordered = new ArrayList<>(ordered.subList(0, size));
                    }
                    listLiveAlbums.postValue(ordered);
                });
            }
        });
    }

    private void loadCachedAlbumsFromAll(String type, int size, Integer fromYear, Integer toYear, MutableLiveData<List<AlbumID3>> listLiveAlbums) {
        List<AlbumID3> current = listLiveAlbums.getValue();
        if (current != null && !current.isEmpty()) {
            return;
        }
        Type typeToken = new TypeToken<List<AlbumID3>>() {
        }.getType();
        cacheRepository.load("albums_all", typeToken, new CacheRepository.CacheResult<List<AlbumID3>>() {
            @Override
            public void onLoaded(List<AlbumID3> albums) {
                if (albums == null) {
                    listLiveAlbums.postValue(new ArrayList<>());
                    return;
                }

                List<AlbumID3> filtered = new ArrayList<>(albums);
                filtered.removeIf(album -> album == null);
                if (fromYear != null && toYear != null) {
                    filtered.removeIf(album -> album.getYear() < fromYear || album.getYear() > toYear);
                }

                applyAlbumOrdering(type, filtered);

                LocalMusicRepository.appendLocalAlbums(App.getContext(), filtered, merged -> {
                    List<AlbumID3> ordered = merged != null ? new ArrayList<>(merged) : new ArrayList<>();
                    applyAlbumOrdering(type, ordered);
                    if (size > 0 && ordered.size() > size) {
                        ordered = new ArrayList<>(ordered.subList(0, size));
                    }
                    listLiveAlbums.postValue(ordered);
                });
            }
        });
    }

    private void loadCachedStarredAlbums(String cacheKey, MutableLiveData<List<AlbumID3>> starredAlbums, boolean random, int size) {
        List<AlbumID3> current = starredAlbums.getValue();
        if (current != null && !current.isEmpty()) {
            return;
        }
        Type type = new TypeToken<List<AlbumID3>>() {
        }.getType();
        cacheRepository.load(cacheKey, type, new CacheRepository.CacheResult<List<AlbumID3>>() {
            @Override
            public void onLoaded(List<AlbumID3> albums) {
                if (albums == null) return;
                if (random) {
                    Collections.shuffle(albums);
                    int limit = Math.min(size, albums.size());
                    starredAlbums.postValue(new ArrayList<>(albums.subList(0, limit)));
                } else {
                    starredAlbums.postValue(albums);
                }
            }
        });
    }

    private List<AlbumID3> mapLegacyStarredAlbums(List<Child> legacyAlbums) {
        if (legacyAlbums == null) return null;
        List<AlbumID3> mapped = new ArrayList<>();
        for (Child legacy : legacyAlbums) {
            if (legacy == null) continue;
            AlbumID3 album = new AlbumID3();
            album.setId(legacy.getId());
            String name = legacy.getAlbum();
            if (name == null || name.isEmpty()) {
                name = legacy.getTitle();
            }
            album.setName(name);
            album.setArtist(legacy.getArtist());
            album.setArtistId(legacy.getArtistId());
            album.setCoverArtId(legacy.getCoverArtId());
            album.setYear(legacy.getYear() != null ? legacy.getYear() : 0);
            album.setStarred(legacy.getStarred());
            album.setGenre(legacy.getGenre());
            album.setDuration(legacy.getDuration() != null ? legacy.getDuration() : 0);
            album.setPlayCount(legacy.getPlayCount());
            album.setUserRating(legacy.getUserRating());
            mapped.add(album);
        }
        return mapped;
    }

    private void loadCachedAlbumTracks(String cacheKey, MutableLiveData<List<Child>> albumTracks) {
        Type type = new TypeToken<List<Child>>() {
        }.getType();
        cacheRepository.load(cacheKey, type, new CacheRepository.CacheResult<List<Child>>() {
            @Override
            public void onLoaded(List<Child> tracks) {
                albumTracks.postValue(tracks);
            }
        });
    }

    private void loadCachedArtistAlbums(String cacheKey, MutableLiveData<List<AlbumID3>> artistsAlbum) {
        Type type = new TypeToken<List<AlbumID3>>() {
        }.getType();
        cacheRepository.load(cacheKey, type, new CacheRepository.CacheResult<List<AlbumID3>>() {
            @Override
            public void onLoaded(List<AlbumID3> albums) {
                artistsAlbum.postValue(albums);
            }
        });
    }

    private void loadCachedArtistAlbumsFromAll(String artistId, MutableLiveData<List<AlbumID3>> artistsAlbum) {
        Type type = new TypeToken<List<AlbumID3>>() {
        }.getType();
        cacheRepository.load("albums_all", type, new CacheRepository.CacheResult<List<AlbumID3>>() {
            @Override
            public void onLoaded(List<AlbumID3> albums) {
                if (albums == null) {
                    artistsAlbum.postValue(new ArrayList<>());
                    return;
                }

                List<AlbumID3> filtered = new ArrayList<>();
                for (AlbumID3 album : albums) {
                    if (album == null) continue;
                    if (artistId != null && artistId.equals(album.getArtistId())) {
                        filtered.add(album);
                    }
                }

                filtered.sort(Comparator.comparing(AlbumID3::getYear));
                Collections.reverse(filtered);
                artistsAlbum.postValue(filtered);
            }
        });
    }

    private void mergeJellyfinArtistAlbums(String artistName, MutableLiveData<List<AlbumID3>> artistsAlbum) {
        if (artistName == null || artistName.trim().isEmpty()) return;
        jellyfinCacheRepository.loadAlbumsForArtistName(artistName, jellyfinAlbums -> {
            List<AlbumID3> base = artistsAlbum.getValue();
            List<AlbumID3> merged = LibraryDedupeUtil.mergeAlbums(base != null ? base : new ArrayList<>(), jellyfinAlbums);
            if (merged != null) {
                merged.sort(Comparator.comparing(AlbumID3::getYear));
                Collections.reverse(merged);
            }
            artistsAlbum.postValue(merged);
        });
    }

    private void loadCachedAlbum(String cacheKey, MutableLiveData<AlbumID3> album) {
        cacheRepository.load(cacheKey, AlbumID3.class, new CacheRepository.CacheResult<AlbumID3>() {
            @Override
            public void onLoaded(AlbumID3 cachedAlbum) {
                album.postValue(cachedAlbum);
            }
        });
    }

    private void loadCachedAlbumFromAll(String albumId, MutableLiveData<AlbumID3> album) {
        Type type = new TypeToken<List<AlbumID3>>() {
        }.getType();
        cacheRepository.load("albums_all", type, new CacheRepository.CacheResult<List<AlbumID3>>() {
            @Override
            public void onLoaded(List<AlbumID3> albums) {
                if (albums == null) return;
                for (AlbumID3 entry : albums) {
                    if (entry != null && albumId != null && albumId.equals(entry.getId())) {
                        album.postValue(entry);
                        return;
                    }
                }
            }
        });
    }

    private void loadCachedAlbumInfo(String cacheKey, MutableLiveData<AlbumInfo> albumInfo) {
        cacheRepository.load(cacheKey, AlbumInfo.class, new CacheRepository.CacheResult<AlbumInfo>() {
            @Override
            public void onLoaded(AlbumInfo info) {
                albumInfo.postValue(info);
            }
        });
    }

    private void loadCachedDecadesFromAll(MutableLiveData<List<Integer>> decades) {
        Type type = new TypeToken<List<AlbumID3>>() {
        }.getType();
        cacheRepository.load("albums_all", type, new CacheRepository.CacheResult<List<AlbumID3>>() {
            @Override
            public void onLoaded(List<AlbumID3> albums) {
                if (albums == null || albums.isEmpty()) {
                    decades.postValue(new ArrayList<>());
                    return;
                }

                int minYear = Integer.MAX_VALUE;
                int maxYear = Integer.MIN_VALUE;
                for (AlbumID3 album : albums) {
                    if (album == null) continue;
                    int year = album.getYear();
                    if (year > 0) {
                        minYear = Math.min(minYear, year);
                        maxYear = Math.max(maxYear, year);
                    }
                }

                if (minYear == Integer.MAX_VALUE || maxYear == Integer.MIN_VALUE) {
                    decades.postValue(new ArrayList<>());
                    return;
                }

                List<Integer> decadeList = new ArrayList<>();
                int startDecade = minYear - (minYear % 10);
                int lastDecade = maxYear - (maxYear % 10);
                while (startDecade <= lastDecade) {
                    decadeList.add(startDecade);
                    startDecade = startDecade + 10;
                }

                decades.postValue(decadeList);
            }
        });
    }

    private void applyAlbumOrdering(String type, List<AlbumID3> albums) {
        if (albums == null || albums.isEmpty()) return;

        if ("random".equals(type)) {
            Collections.shuffle(albums);
            return;
        }

        if ("alphabeticalByName".equals(type)) {
            albums.sort(Comparator.comparing(album -> album == null || album.getName() == null ? "" : album.getName(), String.CASE_INSENSITIVE_ORDER));
            return;
        }

        if ("byYear".equals(type)) {
            albums.sort(Comparator.comparingInt(AlbumID3::getYear).reversed());
            return;
        }

        if ("newest".equals(type)) {
            albums.sort((a, b) -> {
                if (a == null && b == null) return 0;
                if (a == null) return 1;
                if (b == null) return -1;
                if (a.getCreated() != null || b.getCreated() != null) {
                    if (a.getCreated() == null) return 1;
                    if (b.getCreated() == null) return -1;
                    return b.getCreated().compareTo(a.getCreated());
                }
                return Integer.compare(b.getYear(), a.getYear());
            });
            return;
        }

        if ("recent".equals(type)) {
            albums.sort((a, b) -> {
                if (a == null && b == null) return 0;
                if (a == null) return 1;
                if (b == null) return -1;
                if (a.getPlayed() != null || b.getPlayed() != null) {
                    if (a.getPlayed() == null) return 1;
                    if (b.getPlayed() == null) return -1;
                    return b.getPlayed().compareTo(a.getPlayed());
                }
                if (a.getCreated() != null || b.getCreated() != null) {
                    if (a.getCreated() == null) return 1;
                    if (b.getCreated() == null) return -1;
                    return b.getCreated().compareTo(a.getCreated());
                }
                return Integer.compare(b.getYear(), a.getYear());
            });
            return;
        }

        if ("frequent".equals(type)) {
            albums.sort((a, b) -> {
                long aCount = a != null && a.getPlayCount() != null ? a.getPlayCount() : 0;
                long bCount = b != null && b.getPlayCount() != null ? b.getPlayCount() : 0;
                return Long.compare(bCount, aCount);
            });
        }
    }
}
