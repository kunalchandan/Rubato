package one.chandan.rubato.sync;

import android.content.Context;

import one.chandan.rubato.App;
import one.chandan.rubato.repository.CacheRepository;
import one.chandan.rubato.repository.LibrarySearchIndexRepository;
import one.chandan.rubato.subsonic.base.ApiResponse;
import one.chandan.rubato.subsonic.models.AlbumID3;
import one.chandan.rubato.subsonic.models.AlbumInfo;
import one.chandan.rubato.subsonic.models.AlbumWithSongsID3;
import one.chandan.rubato.subsonic.models.ArtistID3;
import one.chandan.rubato.subsonic.models.ArtistInfo2;
import one.chandan.rubato.subsonic.models.Child;
import one.chandan.rubato.subsonic.models.Genre;
import one.chandan.rubato.subsonic.models.IndexID3;
import one.chandan.rubato.subsonic.models.Playlist;
import one.chandan.rubato.subsonic.models.ScanStatus;
import one.chandan.rubato.util.MetadataSyncManager;
import one.chandan.rubato.util.OfflinePolicy;
import one.chandan.rubato.util.Preferences;
import one.chandan.rubato.util.SearchIndexBuilder;
import one.chandan.rubato.util.SearchIndexUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import retrofit2.Call;
import retrofit2.Response;

public final class SubsonicSyncProvider {
    private static final int ALBUM_PAGE_SIZE = 500;
    private static final int PARALLELISM = 4;
    private static final int LOG_INTERVAL_SMALL = 10;
    private static final int LOG_INTERVAL_MEDIUM = 25;
    private static final int LOG_INTERVAL_LARGE = 50;
    private static final int NETWORK_TIMEOUT_SECONDS = 20;
    private static final int ALBUM_TRACK_TIMEOUT_SECONDS = 30;
    private static final int SIGNATURE_TIMEOUT_SECONDS = 12;

    private SubsonicSyncProvider() {
    }

    public static Result sync(Context context, CacheRepository cacheRepository, LibrarySearchIndexRepository searchIndexRepository, SyncMode mode) {
        Result result = new Result();
        if (cacheRepository == null || searchIndexRepository == null) {
            return result;
        }
        long now = System.currentTimeMillis();
        String signature = fetchLibrarySignature();
        if (mode == SyncMode.DELTA
                && signature != null
                && signature.equals(Preferences.getMetadataSyncSubsonicSignature())
                && !SyncDeltaPolicy.shouldForceFull(Preferences.getMetadataSyncSubsonicFull())) {
            logSync(MetadataSyncManager.STAGE_PREPARING, "Subsonic delta: no changes", true);
            Preferences.setMetadataSyncSubsonicLast(now);
            return result;
        }
        if (mode == SyncMode.DELTA && signature == null) {
            logSync(MetadataSyncManager.STAGE_PREPARING, "Subsonic signature unavailable; forcing full sync", false);
        }

        List<Playlist> playlists = syncPlaylists(cacheRepository);
        if (playlists != null) {
            result.didWork = true;
            result.playlists = playlists;
            for (Playlist playlist : playlists) {
                if (playlist != null) {
                    syncPlaylistSongs(cacheRepository, playlist.getId());
                    if (playlist.getCoverArtId() != null) {
                        result.coverArtIds.add(playlist.getCoverArtId());
                    }
                }
            }
        }

        List<ArtistID3> artists = syncArtists(cacheRepository);
        if (artists != null) {
            result.didWork = true;
            result.artists = artists;
            List<ArtistID3> artistItems = new ArrayList<>();
            for (ArtistID3 artist : artists) {
                if (artist != null) artistItems.add(artist);
            }
            int artistTotal = artistItems.size();
            Preferences.setMetadataSyncProgress(MetadataSyncManager.STAGE_ARTIST_DETAILS, 0, artistTotal);
            if (artistTotal > 0) {
                ExecutorService executor = Executors.newFixedThreadPool(PARALLELISM);
                CompletionService<ArtistDetailsResult> completion = new ExecutorCompletionService<>(executor);
                for (ArtistID3 artist : artistItems) {
                    completion.submit(() -> new ArtistDetailsResult(artist, syncArtistDetails(context, cacheRepository, artist)));
                }
                int artistIndex = 0;
                try {
                    for (int i = 0; i < artistTotal; i++) {
                        Future<ArtistDetailsResult> future = completion.take();
                        ArtistDetailsResult resultItem;
                        try {
                            resultItem = future.get();
                        } catch (Exception ex) {
                            continue;
                        }
                        artistIndex++;
                        ArtistID3 artist = resultItem.artist;
                        if (artist != null && artist.getCoverArtId() != null) {
                            result.coverArtIds.add(artist.getCoverArtId());
                        }
                        collectInfoUrls(resultItem.info, result.coverArtUrls);
                        String name = artist != null && artist.getName() != null ? artist.getName() : (artist != null ? artist.getId() : null);
                        logProgress(MetadataSyncManager.STAGE_ARTIST_DETAILS, artistIndex, artistTotal, "Artist details: " + name, LOG_INTERVAL_MEDIUM);
                        if (artistIndex % 10 == 0 || artistIndex == artistTotal) {
                            Preferences.setMetadataSyncProgress(MetadataSyncManager.STAGE_ARTIST_DETAILS, artistIndex, artistTotal);
                        }
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } finally {
                    executor.shutdown();
                }
            }
        }

        syncGenres(cacheRepository);

        List<AlbumID3> albums = syncAlbums(cacheRepository);
        if (albums != null) {
            result.didWork = true;
            result.albums = albums;
            List<AlbumID3> albumItems = new ArrayList<>();
            for (AlbumID3 album : albums) {
                if (album != null) albumItems.add(album);
            }
            int estimatedSongTotal = 0;
            for (AlbumID3 album : albumItems) {
                if (album != null && album.getSongCount() != null) {
                    estimatedSongTotal += Math.max(album.getSongCount(), 0);
                }
            }
            Preferences.setMetadataSyncProgress(MetadataSyncManager.STAGE_SONGS, 0, estimatedSongTotal > 0 ? estimatedSongTotal : -1);
            int albumTotal = albumItems.size();
            int processedTracks = 0;
            ExecutorService executor = Executors.newFixedThreadPool(PARALLELISM);
            CompletionService<AlbumTracksResult> completion = new ExecutorCompletionService<>(executor);
            for (AlbumID3 album : albumItems) {
                completion.submit(() -> new AlbumTracksResult(album, syncAlbumTracks(cacheRepository, album)));
            }
            int albumIndex = 0;
            try {
                for (int i = 0; i < albumTotal; i++) {
                    Future<AlbumTracksResult> future = completion.take();
                    AlbumTracksResult resultItem;
                    try {
                        resultItem = future.get();
                    } catch (Exception ex) {
                        continue;
                    }
                    albumIndex++;
                    AlbumID3 album = resultItem.album;
                    if (album != null && album.getCoverArtId() != null) {
                        result.coverArtIds.add(album.getCoverArtId());
                    }
                    List<Child> tracks = resultItem.tracks;
                    if (tracks != null) {
                        processedTracks += tracks.size();
                        for (Child track : tracks) {
                            if (track != null) {
                                if (track.getId() != null && !result.allSongs.containsKey(track.getId())) {
                                    result.allSongs.put(track.getId(), track);
                                }
                                if (track.getCoverArtId() != null) {
                                    result.coverArtIds.add(track.getCoverArtId());
                                }
                            }
                        }
                    }
                    String name = album != null && album.getName() != null ? album.getName() : (album != null ? album.getId() : null);
                    logProgress(MetadataSyncManager.STAGE_SONGS, albumIndex, albumTotal, "Album tracks: " + name, LOG_INTERVAL_MEDIUM);
                    if (albumIndex % 10 == 0) {
                        int total = estimatedSongTotal > 0 ? estimatedSongTotal : -1;
                        Preferences.setMetadataSyncProgress(MetadataSyncManager.STAGE_SONGS, processedTracks, total);
                    }
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } finally {
                executor.shutdown();
            }
            Preferences.setMetadataSyncProgress(MetadataSyncManager.STAGE_SONGS, processedTracks, estimatedSongTotal > 0 ? estimatedSongTotal : -1);

            Preferences.setMetadataSyncProgress(MetadataSyncManager.STAGE_ALBUM_DETAILS, 0, albumTotal);
            if (albumTotal > 0) {
                ExecutorService detailExecutor = Executors.newFixedThreadPool(PARALLELISM);
                CompletionService<AlbumDetailsResult> detailCompletion = new ExecutorCompletionService<>(detailExecutor);
                for (AlbumID3 album : albumItems) {
                    detailCompletion.submit(() -> new AlbumDetailsResult(album, syncAlbumDetails(context, cacheRepository, album)));
                }
                int albumDetailsIndex = 0;
                try {
                    for (int i = 0; i < albumTotal; i++) {
                        Future<AlbumDetailsResult> future = detailCompletion.take();
                        AlbumDetailsResult resultItem;
                        try {
                            resultItem = future.get();
                        } catch (Exception ex) {
                            continue;
                        }
                        albumDetailsIndex++;
                        AlbumID3 album = resultItem.album;
                        collectInfoUrls(resultItem.info, result.coverArtUrls);
                        String name = album != null && album.getName() != null ? album.getName() : (album != null ? album.getId() : null);
                        logProgress(MetadataSyncManager.STAGE_ALBUM_DETAILS, albumDetailsIndex, albumTotal, "Album details: " + name, LOG_INTERVAL_MEDIUM);
                        if (albumDetailsIndex % 10 == 0 || albumDetailsIndex == albumTotal) {
                            Preferences.setMetadataSyncProgress(MetadataSyncManager.STAGE_ALBUM_DETAILS, albumDetailsIndex, albumTotal);
                        }
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } finally {
                    detailExecutor.shutdown();
                }
            }
            logSync(MetadataSyncManager.STAGE_ALBUM_DETAILS, "Album details cached (" + albumTotal + ")", true);
        }

        if (!result.allSongs.isEmpty()) {
            cacheRepository.save("songs_all", new ArrayList<>(result.allSongs.values()));
            logSync(MetadataSyncManager.STAGE_SONGS, "Songs cached (" + result.allSongs.size() + ")", true);
        }

        boolean hasData = hasAnyData(result);
        if (hasData) {
            searchIndexRepository.replaceSource(
                    SearchIndexUtil.SOURCE_SUBSONIC,
                    SearchIndexBuilder.buildFromSubsonic(result.artists, result.albums, new ArrayList<>(result.allSongs.values()), result.playlists)
            );
            Preferences.setMetadataSyncSubsonicLast(now);
            Preferences.setMetadataSyncSubsonicFull(now);
            String resolvedSignature = signature != null ? signature : fallbackSignature(result);
            if (resolvedSignature != null) {
                Preferences.setMetadataSyncSubsonicSignature(resolvedSignature);
            }
        } else {
            logSync(MetadataSyncManager.STAGE_PREPARING, "Subsonic sync returned empty; keeping previous cache", true);
        }

        return result;
    }

    private static boolean hasAnyData(Result result) {
        if (result == null) return false;
        if (result.playlists != null && !result.playlists.isEmpty()) return true;
        if (result.artists != null && !result.artists.isEmpty()) return true;
        if (result.albums != null && !result.albums.isEmpty()) return true;
        return !result.allSongs.isEmpty();
    }

    private static List<Playlist> syncPlaylists(CacheRepository cacheRepository) {
        try {
            Preferences.setMetadataSyncProgress(MetadataSyncManager.STAGE_PLAYLISTS, 0, -1);
            logSync(MetadataSyncManager.STAGE_PLAYLISTS, "Fetching playlists", false);
            Call<ApiResponse> call = App.getSubsonicClientInstance(false).getPlaylistClient().getPlaylists();
            applyTimeout(call, NETWORK_TIMEOUT_SECONDS);
            Response<ApiResponse> response = call.execute();
            if (!response.isSuccessful() || response.body() == null || response.body().getSubsonicResponse() == null) {
                logSync(MetadataSyncManager.STAGE_PLAYLISTS, "Playlists fetch failed", true);
                return null;
            }
            if (response.body().getSubsonicResponse().getError() != null) {
                logSync(MetadataSyncManager.STAGE_PLAYLISTS, "Playlists error: " + response.body().getSubsonicResponse().getError().getMessage(), true);
                return null;
            }
            if (response.body().getSubsonicResponse().getPlaylists() == null) {
                logSync(MetadataSyncManager.STAGE_PLAYLISTS, "Playlists empty response", true);
                return null;
            }

            List<Playlist> playlists = response.body().getSubsonicResponse().getPlaylists().getPlaylists();
            cacheRepository.save("playlists", playlists);
            int total = playlists == null ? 0 : playlists.size();
            Preferences.setMetadataSyncProgress(MetadataSyncManager.STAGE_PLAYLISTS, total, total);
            if (playlists != null && !playlists.isEmpty()) {
                int index = 0;
                for (Playlist playlist : playlists) {
                    if (playlist == null) continue;
                    index++;
                    String name = playlist.getName() != null ? playlist.getName() : playlist.getId();
                    logProgress(MetadataSyncManager.STAGE_PLAYLISTS, index, total, "Playlist: " + name, LOG_INTERVAL_SMALL);
                }
            }
            logSync(MetadataSyncManager.STAGE_PLAYLISTS, "Playlists cached (" + total + ")", true);
            return playlists;
        } catch (Exception ex) {
            logSync(MetadataSyncManager.STAGE_PLAYLISTS, "Playlists fetch failed (" + ex.getClass().getSimpleName() + ")", true);
            return null;
        }
    }

    private static void syncPlaylistSongs(CacheRepository cacheRepository, String playlistId) {
        if (playlistId == null || playlistId.isEmpty()) return;
        try {
            Call<ApiResponse> call = App.getSubsonicClientInstance(false).getPlaylistClient().getPlaylist(playlistId);
            applyTimeout(call, NETWORK_TIMEOUT_SECONDS);
            Response<ApiResponse> response = call.execute();
            if (!response.isSuccessful() || response.body() == null || response.body().getSubsonicResponse().getPlaylist() == null) {
                logSync(MetadataSyncManager.STAGE_PLAYLISTS, "Playlist songs fetch failed (" + playlistId + ")", true);
                return;
            }
            List<Child> songs = response.body().getSubsonicResponse().getPlaylist().getEntries();
            cacheRepository.save("playlist_songs_" + playlistId, songs);
        } catch (Exception ignored) {
            logSync(MetadataSyncManager.STAGE_PLAYLISTS, "Playlist songs fetch failed (" + playlistId + ")", true);
        }
    }

    private static List<ArtistID3> syncArtists(CacheRepository cacheRepository) {
        try {
            Preferences.setMetadataSyncProgress(MetadataSyncManager.STAGE_ARTISTS, 0, -1);
            logSync(MetadataSyncManager.STAGE_ARTISTS, "Fetching artists", false);
            Call<ApiResponse> call = App.getSubsonicClientInstance(false).getBrowsingClient().getArtists();
            applyTimeout(call, NETWORK_TIMEOUT_SECONDS);
            Response<ApiResponse> response = call.execute();
            if (!response.isSuccessful() || response.body() == null || response.body().getSubsonicResponse() == null) {
                logSync(MetadataSyncManager.STAGE_ARTISTS, "Artists fetch failed", true);
                return null;
            }
            if (response.body().getSubsonicResponse().getError() != null) {
                logSync(MetadataSyncManager.STAGE_ARTISTS, "Artists error: " + response.body().getSubsonicResponse().getError().getMessage(), true);
                return null;
            }
            if (response.body().getSubsonicResponse().getArtists() == null) {
                logSync(MetadataSyncManager.STAGE_ARTISTS, "Artists empty response", true);
                return null;
            }

            List<ArtistID3> artists = new ArrayList<>();
            if (response.body().getSubsonicResponse().getArtists().getIndices() != null) {
                for (IndexID3 index : response.body().getSubsonicResponse().getArtists().getIndices()) {
                    if (index != null && index.getArtists() != null) {
                        artists.addAll(index.getArtists());
                    }
                }
            }

            cacheRepository.save("artists_all", artists);
            Preferences.setMetadataSyncProgress(MetadataSyncManager.STAGE_ARTISTS, artists.size(), artists.size());
            logSync(MetadataSyncManager.STAGE_ARTISTS, "Artists cached (" + artists.size() + ")", true);
            return artists;
        } catch (Exception ex) {
            logSync(MetadataSyncManager.STAGE_ARTISTS, "Artists fetch failed (" + ex.getClass().getSimpleName() + ")", true);
            return null;
        }
    }

    private static void syncGenres(CacheRepository cacheRepository) {
        try {
            Preferences.setMetadataSyncProgress(MetadataSyncManager.STAGE_GENRES, 0, -1);
            logSync(MetadataSyncManager.STAGE_GENRES, "Fetching genres", false);
            Call<ApiResponse> call = App.getSubsonicClientInstance(false).getBrowsingClient().getGenres();
            applyTimeout(call, NETWORK_TIMEOUT_SECONDS);
            Response<ApiResponse> response = call.execute();
            if (!response.isSuccessful() || response.body() == null || response.body().getSubsonicResponse() == null) {
                logSync(MetadataSyncManager.STAGE_GENRES, "Genres fetch failed", true);
                return;
            }
            if (response.body().getSubsonicResponse().getError() != null) {
                logSync(MetadataSyncManager.STAGE_GENRES, "Genres error: " + response.body().getSubsonicResponse().getError().getMessage(), true);
                return;
            }
            if (response.body().getSubsonicResponse().getGenres() == null) {
                logSync(MetadataSyncManager.STAGE_GENRES, "Genres empty response", true);
                return;
            }
            List<Genre> genres = response.body().getSubsonicResponse().getGenres().getGenres();
            cacheRepository.save("genres_all", genres);
            int total = genres == null ? 0 : genres.size();
            Preferences.setMetadataSyncProgress(MetadataSyncManager.STAGE_GENRES, total, total);
            logSync(MetadataSyncManager.STAGE_GENRES, "Genres cached (" + total + ")", true);
        } catch (Exception ex) {
            logSync(MetadataSyncManager.STAGE_GENRES, "Genres fetch failed (" + ex.getClass().getSimpleName() + ")", true);
        }
    }

    private static List<AlbumID3> syncAlbums(CacheRepository cacheRepository) {
        List<AlbumID3> allAlbums = new ArrayList<>();
        int offset = 0;

        Preferences.setMetadataSyncProgress(MetadataSyncManager.STAGE_ALBUMS, 0, -1);
        logSync(MetadataSyncManager.STAGE_ALBUMS, "Fetching albums", false);
        while (true) {
            if (OfflinePolicy.isOffline()) break;
            try {
                Call<ApiResponse> call = App.getSubsonicClientInstance(false)
                        .getAlbumSongListClient()
                        .getAlbumList2("alphabeticalByName", ALBUM_PAGE_SIZE, offset, null, null);
                applyTimeout(call, NETWORK_TIMEOUT_SECONDS);
                Response<ApiResponse> response = call.execute();
                if (!response.isSuccessful() || response.body() == null || response.body().getSubsonicResponse() == null) {
                    logSync(MetadataSyncManager.STAGE_ALBUMS, "Albums fetch failed", true);
                    break;
                }
                if (response.body().getSubsonicResponse().getError() != null) {
                    logSync(MetadataSyncManager.STAGE_ALBUMS, "Albums error: " + response.body().getSubsonicResponse().getError().getMessage(), true);
                    break;
                }
                if (response.body().getSubsonicResponse().getAlbumList2() == null) {
                    logSync(MetadataSyncManager.STAGE_ALBUMS, "Albums empty response", true);
                    break;
                }

                List<AlbumID3> page = response.body().getSubsonicResponse().getAlbumList2().getAlbums();
                if (page == null || page.isEmpty()) break;

                allAlbums.addAll(page);
                cacheRepository.save("albums_all", allAlbums);
                Preferences.setMetadataSyncProgress(MetadataSyncManager.STAGE_ALBUMS, allAlbums.size(), -1);
                logSync(MetadataSyncManager.STAGE_ALBUMS, "Albums cached (" + allAlbums.size() + ")", false);

                offset += page.size();
                if (page.size() < ALBUM_PAGE_SIZE) break;
            } catch (Exception ex) {
                logSync(MetadataSyncManager.STAGE_ALBUMS, "Albums fetch failed (" + ex.getClass().getSimpleName() + ")", true);
                break;
            }
        }

        Preferences.setMetadataSyncProgress(MetadataSyncManager.STAGE_ALBUMS, allAlbums.size(), allAlbums.size());
        logSync(MetadataSyncManager.STAGE_ALBUMS, "Albums cached (" + allAlbums.size() + ")", true);

        return allAlbums;
    }

    private static List<Child> syncAlbumTracks(CacheRepository cacheRepository, AlbumID3 album) {
        if (album == null || album.getId() == null || album.getId().isEmpty()) return null;
        String albumId = album.getId();
        try {
            Call<ApiResponse> call = App.getSubsonicClientInstance(false).getBrowsingClient().getAlbum(albumId);
            applyTimeout(call, ALBUM_TRACK_TIMEOUT_SECONDS);
            Response<ApiResponse> response = call.execute();
            if (!response.isSuccessful() || response.body() == null || response.body().getSubsonicResponse().getAlbum() == null) {
                return null;
            }

            AlbumWithSongsID3 albumDetails = response.body().getSubsonicResponse().getAlbum();
            List<Child> tracks = albumDetails.getSongs();
            cacheRepository.save("album_tracks_" + albumId, tracks);
            cacheRepository.save("album_" + albumId, albumDetails);
            return tracks;
        } catch (Exception ex) {
            String name = album.getName() != null ? album.getName() : albumId;
            logSync(MetadataSyncManager.STAGE_SONGS, "Album tracks failed: " + name + " (" + ex.getClass().getSimpleName() + ")", false);
            return null;
        }
    }

    private static ArtistInfo2 syncArtistDetails(Context context, CacheRepository cacheRepository, ArtistID3 artist) {
        if (artist == null || artist.getId() == null || artist.getId().isEmpty()) return null;
        try {
            Call<ApiResponse> call = App.getSubsonicClientInstance(false).getBrowsingClient().getArtistInfo2(artist.getId());
            applyTimeout(call, NETWORK_TIMEOUT_SECONDS);
            Response<ApiResponse> response = call.execute();
            if (!response.isSuccessful() || response.body() == null || response.body().getSubsonicResponse().getArtistInfo2() == null) {
                return null;
            }

            ArtistInfo2 info = response.body().getSubsonicResponse().getArtistInfo2();
            cacheRepository.save("artist_info_" + artist.getId(), info);
            return info;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static AlbumInfo syncAlbumDetails(Context context, CacheRepository cacheRepository, AlbumID3 album) {
        if (album == null || album.getId() == null || album.getId().isEmpty()) return null;
        try {
            Call<ApiResponse> call = App.getSubsonicClientInstance(false).getBrowsingClient().getAlbumInfo2(album.getId());
            applyTimeout(call, NETWORK_TIMEOUT_SECONDS);
            Response<ApiResponse> response = call.execute();
            if (!response.isSuccessful() || response.body() == null || response.body().getSubsonicResponse().getAlbumInfo() == null) {
                return null;
            }

            AlbumInfo info = response.body().getSubsonicResponse().getAlbumInfo();
            cacheRepository.save("album_info_" + album.getId(), info);
            return info;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static final class ArtistDetailsResult {
        final ArtistID3 artist;
        final ArtistInfo2 info;

        ArtistDetailsResult(ArtistID3 artist, ArtistInfo2 info) {
            this.artist = artist;
            this.info = info;
        }
    }

    private static final class AlbumTracksResult {
        final AlbumID3 album;
        final List<Child> tracks;

        AlbumTracksResult(AlbumID3 album, List<Child> tracks) {
            this.album = album;
            this.tracks = tracks;
        }
    }

    private static final class AlbumDetailsResult {
        final AlbumID3 album;
        final AlbumInfo info;

        AlbumDetailsResult(AlbumID3 album, AlbumInfo info) {
            this.album = album;
            this.info = info;
        }
    }

    private static void collectInfoUrls(ArtistInfo2 info, Set<String> urls) {
        if (info == null || urls == null) return;
        if (info.getSmallImageUrl() != null) urls.add(info.getSmallImageUrl());
        if (info.getMediumImageUrl() != null) urls.add(info.getMediumImageUrl());
        if (info.getLargeImageUrl() != null) urls.add(info.getLargeImageUrl());
    }

    private static void collectInfoUrls(AlbumInfo info, Set<String> urls) {
        if (info == null || urls == null) return;
        if (info.getSmallImageUrl() != null) urls.add(info.getSmallImageUrl());
        if (info.getMediumImageUrl() != null) urls.add(info.getMediumImageUrl());
        if (info.getLargeImageUrl() != null) urls.add(info.getLargeImageUrl());
    }

    private static void logSync(String stage, String message, boolean completed) {
        Preferences.appendMetadataSyncLog(message, stage, completed);
    }

    private static void logProgress(String stage, int index, int total, String message, int interval) {
        if (index <= 0 || total <= 0) return;
        if (index == 1 || index % interval == 0 || index == total) {
            logSync(stage, message + " (" + index + "/" + total + ")", false);
        }
    }

    public static final class Result {
        public final Map<String, Child> allSongs = new LinkedHashMap<>();
        public final Set<String> coverArtIds = new HashSet<>();
        public final Set<String> coverArtUrls = new HashSet<>();
        public List<Playlist> playlists = new ArrayList<>();
        public List<ArtistID3> artists = new ArrayList<>();
        public List<AlbumID3> albums = new ArrayList<>();
        public boolean didWork;
    }

    private static String fetchLibrarySignature() {
        if (OfflinePolicy.isOffline()) return null;
        Call<ApiResponse> call = App.getSubsonicClientInstance(false).getMediaLibraryScanningClient().getScanStatus();
        applyTimeout(call, SIGNATURE_TIMEOUT_SECONDS);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Response<ApiResponse>> future = executor.submit(call::execute);
            Response<ApiResponse> response = future.get(SIGNATURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!response.isSuccessful() || response.body() == null) return null;
            if (response.body().getSubsonicResponse() == null) return null;
            ScanStatus status = response.body().getSubsonicResponse().getScanStatus();
            if (status == null || status.getCount() == null) return null;
            return String.valueOf(status.getCount());
        } catch (TimeoutException ex) {
            logSync(MetadataSyncManager.STAGE_PREPARING, "Subsonic scan status timed out", false);
            call.cancel();
            return null;
        } catch (Exception ignored) {
            return null;
        } finally {
            executor.shutdownNow();
        }
    }

    private static void applyTimeout(Call<?> call, int seconds) {
        if (call == null) return;
        try {
            call.timeout().timeout(seconds, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
    }

    private static String fallbackSignature(Result result) {
        if (result == null) return null;
        int songs = result.allSongs.size();
        int albums = result.albums != null ? result.albums.size() : 0;
        int artists = result.artists != null ? result.artists.size() : 0;
        int playlists = result.playlists != null ? result.playlists.size() : 0;
        return songs + ":" + albums + ":" + artists + ":" + playlists;
    }
}
