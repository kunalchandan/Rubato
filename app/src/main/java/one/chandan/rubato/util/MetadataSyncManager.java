package one.chandan.rubato.util;

import android.content.Context;

import one.chandan.rubato.App;
import one.chandan.rubato.glide.CustomGlideRequest;
import one.chandan.rubato.repository.CacheRepository;
import one.chandan.rubato.repository.JellyfinLibraryRepository;
import one.chandan.rubato.repository.JellyfinServerRepository;
import one.chandan.rubato.repository.LibrarySearchIndexRepository;
import one.chandan.rubato.util.SearchIndexBuilder;
import one.chandan.rubato.util.SearchIndexUtil;
import one.chandan.rubato.model.JellyfinServer;
import one.chandan.rubato.model.LibrarySearchEntry;
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
import one.chandan.rubato.subsonic.models.SubsonicResponse;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import retrofit2.Call;
import retrofit2.Response;

public final class MetadataSyncManager {
    private static final long MIN_SYNC_INTERVAL_MS = 6 * 60 * 60 * 1000L;
    private static final int ALBUM_PAGE_SIZE = 500;
    private static final AtomicBoolean SYNCING = new AtomicBoolean(false);

    public static final String STAGE_PREPARING = "preparing";
    public static final String STAGE_PLAYLISTS = "playlists";
    public static final String STAGE_ARTISTS = "artists";
    public static final String STAGE_ARTIST_DETAILS = "artist_details";
    public static final String STAGE_GENRES = "genres";
    public static final String STAGE_ALBUMS = "albums";
    public static final String STAGE_ALBUM_DETAILS = "album_details";
    public static final String STAGE_SONGS = "songs";
    public static final String STAGE_COVER_ART = "cover_art";
    public static final String STAGE_LYRICS = "lyrics";
    public static final String STAGE_JELLYFIN = "jellyfin";
    private static final int LOG_INTERVAL_SMALL = 10;
    private static final int LOG_INTERVAL_MEDIUM = 25;
    private static final int LOG_INTERVAL_LARGE = 50;

    private MetadataSyncManager() {
    }

    public static void startIfNeeded(Context context) {
        if (context == null) return;
        if (NetworkUtil.isOffline()) return;
        if (Preferences.isDataSavingMode()) return;
        if (SYNCING.get()) return;

        long lastSync = Preferences.getMetadataSyncLast();
        if (lastSync > 0 && System.currentTimeMillis() - lastSync < MIN_SYNC_INTERVAL_MS) return;

        SYNCING.set(true);
        new Thread(() -> {
            boolean success = false;
            try {
                success = runSync(context.getApplicationContext());
                if (success) {
                    Preferences.setMetadataSyncLast(System.currentTimeMillis());
                }
            } finally {
                Preferences.setMetadataSyncActive(false);
                Preferences.setMetadataSyncProgress(null, 0, -1);
                Preferences.setMetadataSyncStarted(0);
                SYNCING.set(false);
            }
        }, "MetadataSync").start();
    }

    private static boolean runSync(Context context) {
        if (context == null) return false;
        if (!hasSubsonicCredentials()) {
            Preferences.clearMetadataSyncLogs();
            logSync(STAGE_PREPARING, "Sync skipped: server credentials missing", true);
            return false;
        }
        if (!verifySubsonicReachable()) {
            Preferences.clearMetadataSyncLogs();
            logSync(STAGE_PREPARING, "Sync skipped: server unreachable", true);
            return false;
        }

        CacheRepository cacheRepository = new CacheRepository();
        LibrarySearchIndexRepository searchIndexRepository = new LibrarySearchIndexRepository();
        Map<String, Child> allSongs = new LinkedHashMap<>();
        Set<String> coverArtIds = new HashSet<>();
        Set<String> coverArtUrls = new HashSet<>();
        boolean didWork = false;

        Preferences.setMetadataSyncActive(true);
        Preferences.setMetadataSyncStarted(System.currentTimeMillis());
        Preferences.setMetadataSyncProgress(STAGE_PREPARING, 0, -1);
        Preferences.setMetadataSyncCoverArtProgress(0, -1);
        Preferences.setMetadataSyncLyricsProgress(0, -1);
        Preferences.clearMetadataSyncLogs();
        logSync(STAGE_PREPARING, "Sync started", false);

        List<Playlist> playlists = syncPlaylists(cacheRepository);
        if (playlists != null) {
            didWork = true;
            for (Playlist playlist : playlists) {
                if (playlist != null) {
                    syncPlaylistSongs(cacheRepository, playlist.getId());
                    if (playlist.getCoverArtId() != null) {
                        coverArtIds.add(playlist.getCoverArtId());
                    }
                }
            }
        }

        List<ArtistID3> artists = syncArtists(cacheRepository);
        if (artists != null) {
            didWork = true;
            int artistTotal = artists.size();
            Preferences.setMetadataSyncProgress(STAGE_ARTIST_DETAILS, 0, artistTotal);
            int artistIndex = 0;
            for (ArtistID3 artist : artists) {
                if (artist == null) continue;
                if (artist.getCoverArtId() != null) {
                    coverArtIds.add(artist.getCoverArtId());
                }
                ArtistInfo2 info = syncArtistDetails(context, cacheRepository, artist);
                collectInfoUrls(info, coverArtUrls);
                artistIndex++;
                String name = artist.getName() != null ? artist.getName() : artist.getId();
                logProgress(STAGE_ARTIST_DETAILS, artistIndex, artistTotal, "Artist details: " + name, LOG_INTERVAL_MEDIUM);
                if (artistIndex % 10 == 0 || artistIndex == artistTotal) {
                    Preferences.setMetadataSyncProgress(STAGE_ARTIST_DETAILS, artistIndex, artistTotal);
                }
            }
        }

        syncGenres(cacheRepository);

        List<AlbumID3> albums = syncAlbums(cacheRepository);
        if (albums != null) {
            didWork = true;
            int estimatedSongTotal = 0;
            for (AlbumID3 album : albums) {
                if (album != null && album.getSongCount() != null) {
                    estimatedSongTotal += Math.max(album.getSongCount(), 0);
                }
            }
            Preferences.setMetadataSyncProgress(STAGE_SONGS, 0, estimatedSongTotal > 0 ? estimatedSongTotal : -1);
            int albumIndex = 0;
            int processedTracks = 0;
            for (AlbumID3 album : albums) {
                if (album == null) continue;
                albumIndex++;
                if (album.getCoverArtId() != null) coverArtIds.add(album.getCoverArtId());
                if (album.getId() != null) {
                    List<Child> tracks = syncAlbumTracks(cacheRepository, album.getId());
                    if (tracks != null) {
                        processedTracks += tracks.size();
                        for (Child track : tracks) {
                            if (track != null) {
                                if (track.getId() != null && !allSongs.containsKey(track.getId())) {
                                    allSongs.put(track.getId(), track);
                                }
                                if (track.getCoverArtId() != null) {
                                    coverArtIds.add(track.getCoverArtId());
                                }
                            }
                        }
                    }
                }
                String name = album.getName() != null ? album.getName() : album.getId();
                logProgress(STAGE_SONGS, albumIndex, albums.size(), "Album tracks: " + name, LOG_INTERVAL_MEDIUM);
                if (albumIndex % 10 == 0) {
                    int total = estimatedSongTotal > 0 ? estimatedSongTotal : -1;
                    Preferences.setMetadataSyncProgress(STAGE_SONGS, processedTracks, total);
                }
            }
            Preferences.setMetadataSyncProgress(STAGE_SONGS, processedTracks, estimatedSongTotal > 0 ? estimatedSongTotal : -1);

            int albumTotal = albums.size();
            Preferences.setMetadataSyncProgress(STAGE_ALBUM_DETAILS, 0, albumTotal);
            int albumDetailsIndex = 0;
            for (AlbumID3 album : albums) {
                if (album == null) continue;
                AlbumInfo info = syncAlbumDetails(context, cacheRepository, album);
                collectInfoUrls(info, coverArtUrls);
                albumDetailsIndex++;
                String name = album.getName() != null ? album.getName() : album.getId();
                logProgress(STAGE_ALBUM_DETAILS, albumDetailsIndex, albumTotal, "Album details: " + name, LOG_INTERVAL_MEDIUM);
                if (albumDetailsIndex % 10 == 0 || albumDetailsIndex == albumTotal) {
                    Preferences.setMetadataSyncProgress(STAGE_ALBUM_DETAILS, albumDetailsIndex, albumTotal);
                }
            }
            logSync(STAGE_ALBUM_DETAILS, "Album details cached (" + albumTotal + ")", true);
        }

        if (!allSongs.isEmpty()) {
            cacheRepository.save("songs_all", new ArrayList<>(allSongs.values()));
            logSync(STAGE_SONGS, "Songs cached (" + allSongs.size() + ")", true);
        }

        searchIndexRepository.replaceSource(
                SearchIndexUtil.SOURCE_SUBSONIC,
                SearchIndexBuilder.buildFromSubsonic(artists, albums, new ArrayList<>(allSongs.values()), playlists)
        );

        syncJellyfinLibraries(cacheRepository, searchIndexRepository, coverArtIds);

        prefetchCoverArt(context, coverArtIds, coverArtUrls);
        syncLyrics(cacheRepository, allSongs);
        logSync(STAGE_PREPARING, "Sync complete", true);
        return didWork;
    }

    private static boolean hasSubsonicCredentials() {
        String server = Preferences.getInUseServerAddress();
        String user = Preferences.getUser();
        String password = Preferences.getPassword();
        String token = Preferences.getToken();
        return server != null && !server.isEmpty()
                && user != null && !user.isEmpty()
                && ((password != null && !password.isEmpty()) || (token != null && !token.isEmpty()));
    }

    private static boolean verifySubsonicReachable() {
        if (NetworkUtil.isOffline()) {
            return false;
        }
        try {
            Call<ApiResponse> call = App.getSubsonicClientInstance(false).getSystemClient().ping();
            Response<ApiResponse> response = call.execute();
            if (!response.isSuccessful() || response.body() == null) {
                ServerStatus.markUnreachable();
                return false;
            }
            SubsonicResponse subsonicResponse = response.body().getSubsonicResponse();
            if (subsonicResponse == null) {
                ServerStatus.markUnreachable();
                return false;
            }
            if (subsonicResponse.getError() != null) {
                ServerStatus.markUnreachable();
                return false;
            }
            if (subsonicResponse.getStatus() != null
                    && "failed".equalsIgnoreCase(subsonicResponse.getStatus())) {
                ServerStatus.markUnreachable();
                return false;
            }
            ServerStatus.markReachable();
            return true;
        } catch (Exception ignored) {
            ServerStatus.markUnreachable();
            return false;
        }
    }

    private static void syncJellyfinLibraries(CacheRepository cacheRepository,
                                              LibrarySearchIndexRepository searchIndexRepository,
                                              Set<String> coverArtIds) {
        try {
            List<JellyfinServer> servers = new JellyfinServerRepository().getServersSnapshot();
            if (servers == null || servers.isEmpty()) return;

            Preferences.setMetadataSyncProgress(STAGE_JELLYFIN, 0, servers.size());
            logSync(STAGE_JELLYFIN, "Fetching Jellyfin libraries", false);

            JellyfinLibraryRepository repository = new JellyfinLibraryRepository();
            List<LibrarySearchEntry> entries = new ArrayList<>();
            int index = 0;
            for (JellyfinServer server : servers) {
                if (server == null) continue;
                index++;
                List<LibrarySearchEntry> serverEntries = repository.syncServer(server, cacheRepository);
                if (serverEntries != null && !serverEntries.isEmpty()) {
                    entries.addAll(serverEntries);
                }
                String name = server.getName() != null ? server.getName() : server.getId();
                logProgress(STAGE_JELLYFIN, index, servers.size(), "Jellyfin: " + name, LOG_INTERVAL_SMALL);
                Preferences.setMetadataSyncProgress(STAGE_JELLYFIN, index, servers.size());
            }
            if (!entries.isEmpty()) {
                if (coverArtIds != null) {
                    for (LibrarySearchEntry entry : entries) {
                        if (entry == null) continue;
                        String coverArt = SearchIndexUtil.tagSourceId(SearchIndexUtil.SOURCE_JELLYFIN, entry.getCoverArt());
                        if (coverArt != null && !coverArt.isEmpty()) {
                            coverArtIds.add(coverArt);
                        }
                    }
                }
                searchIndexRepository.replaceSource(SearchIndexUtil.SOURCE_JELLYFIN, entries);
                logSync(STAGE_JELLYFIN, "Jellyfin cached (" + entries.size() + ")", true);
            }
        } catch (Exception ignored) {
        }
    }

    private static List<Playlist> syncPlaylists(CacheRepository cacheRepository) {
        try {
            Preferences.setMetadataSyncProgress(STAGE_PLAYLISTS, 0, -1);
            logSync(STAGE_PLAYLISTS, "Fetching playlists", false);
            Call<ApiResponse> call = App.getSubsonicClientInstance(false).getPlaylistClient().getPlaylists();
            Response<ApiResponse> response = call.execute();
            if (!response.isSuccessful() || response.body() == null || response.body().getSubsonicResponse().getPlaylists() == null) {
                return null;
            }

            List<Playlist> playlists = response.body().getSubsonicResponse().getPlaylists().getPlaylists();
            cacheRepository.save("playlists", playlists);
            int total = playlists == null ? 0 : playlists.size();
            Preferences.setMetadataSyncProgress(STAGE_PLAYLISTS, total, total);
            if (playlists != null && !playlists.isEmpty()) {
                int index = 0;
                for (Playlist playlist : playlists) {
                    if (playlist == null) continue;
                    index++;
                    String name = playlist.getName() != null ? playlist.getName() : playlist.getId();
                    logProgress(STAGE_PLAYLISTS, index, total, "Playlist: " + name, LOG_INTERVAL_SMALL);
                }
            }
            logSync(STAGE_PLAYLISTS, "Playlists cached (" + total + ")", true);
            return playlists;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void syncPlaylistSongs(CacheRepository cacheRepository, String playlistId) {
        if (playlistId == null || playlistId.isEmpty()) return;
        try {
            Call<ApiResponse> call = App.getSubsonicClientInstance(false).getPlaylistClient().getPlaylist(playlistId);
            Response<ApiResponse> response = call.execute();
            if (!response.isSuccessful() || response.body() == null || response.body().getSubsonicResponse().getPlaylist() == null) {
                return;
            }
            List<Child> songs = response.body().getSubsonicResponse().getPlaylist().getEntries();
            cacheRepository.save("playlist_songs_" + playlistId, songs);
        } catch (Exception ignored) {
        }
    }

    private static List<ArtistID3> syncArtists(CacheRepository cacheRepository) {
        try {
            Preferences.setMetadataSyncProgress(STAGE_ARTISTS, 0, -1);
            logSync(STAGE_ARTISTS, "Fetching artists", false);
            Call<ApiResponse> call = App.getSubsonicClientInstance(false).getBrowsingClient().getArtists();
            Response<ApiResponse> response = call.execute();
            if (!response.isSuccessful() || response.body() == null || response.body().getSubsonicResponse().getArtists() == null) {
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
            Preferences.setMetadataSyncProgress(STAGE_ARTISTS, artists.size(), artists.size());
            logSync(STAGE_ARTISTS, "Artists cached (" + artists.size() + ")", true);
            return artists;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void syncGenres(CacheRepository cacheRepository) {
        try {
            Preferences.setMetadataSyncProgress(STAGE_GENRES, 0, -1);
            logSync(STAGE_GENRES, "Fetching genres", false);
            Call<ApiResponse> call = App.getSubsonicClientInstance(false).getBrowsingClient().getGenres();
            Response<ApiResponse> response = call.execute();
            if (!response.isSuccessful() || response.body() == null || response.body().getSubsonicResponse().getGenres() == null) {
                return;
            }
            List<Genre> genres = response.body().getSubsonicResponse().getGenres().getGenres();
            cacheRepository.save("genres_all", genres);
            int total = genres == null ? 0 : genres.size();
            Preferences.setMetadataSyncProgress(STAGE_GENRES, total, total);
            logSync(STAGE_GENRES, "Genres cached (" + total + ")", true);
        } catch (Exception ignored) {
        }
    }

    private static List<AlbumID3> syncAlbums(CacheRepository cacheRepository) {
        List<AlbumID3> allAlbums = new ArrayList<>();
        int offset = 0;

        Preferences.setMetadataSyncProgress(STAGE_ALBUMS, 0, -1);
        logSync(STAGE_ALBUMS, "Fetching albums", false);
        while (true) {
            if (NetworkUtil.isOffline()) break;
            try {
                Call<ApiResponse> call = App.getSubsonicClientInstance(false)
                        .getAlbumSongListClient()
                        .getAlbumList2("alphabeticalByName", ALBUM_PAGE_SIZE, offset, null, null);
                Response<ApiResponse> response = call.execute();
                if (!response.isSuccessful() || response.body() == null || response.body().getSubsonicResponse().getAlbumList2() == null) {
                    break;
                }

                List<AlbumID3> page = response.body().getSubsonicResponse().getAlbumList2().getAlbums();
                if (page == null || page.isEmpty()) break;

                allAlbums.addAll(page);
                cacheRepository.save("albums_all", allAlbums);
                Preferences.setMetadataSyncProgress(STAGE_ALBUMS, allAlbums.size(), -1);
                logSync(STAGE_ALBUMS, "Albums cached (" + allAlbums.size() + ")", false);

                offset += page.size();
                if (page.size() < ALBUM_PAGE_SIZE) break;
            } catch (Exception ignored) {
                break;
            }
        }

        Preferences.setMetadataSyncProgress(STAGE_ALBUMS, allAlbums.size(), allAlbums.size());
        logSync(STAGE_ALBUMS, "Albums cached (" + allAlbums.size() + ")", true);

        return allAlbums;
    }

    private static List<Child> syncAlbumTracks(CacheRepository cacheRepository, String albumId) {
        if (albumId == null || albumId.isEmpty()) return null;
        try {
            Call<ApiResponse> call = App.getSubsonicClientInstance(false).getBrowsingClient().getAlbum(albumId);
            Response<ApiResponse> response = call.execute();
            if (!response.isSuccessful() || response.body() == null || response.body().getSubsonicResponse().getAlbum() == null) {
                return null;
            }

            AlbumWithSongsID3 album = response.body().getSubsonicResponse().getAlbum();
            List<Child> tracks = album.getSongs();
            cacheRepository.save("album_tracks_" + albumId, tracks);
            cacheRepository.save("album_" + albumId, album);
            return tracks;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static ArtistInfo2 syncArtistDetails(Context context, CacheRepository cacheRepository, ArtistID3 artist) {
        if (artist == null || artist.getId() == null || artist.getId().isEmpty()) return null;
        try {
            Call<ApiResponse> call = App.getSubsonicClientInstance(false).getBrowsingClient().getArtistInfo2(artist.getId());
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

    private static void prefetchCoverArt(Context context, Set<String> coverArtIds, Set<String> coverArtUrls) {
        if (context == null) return;
        int total = 0;
        if (coverArtIds != null) total += coverArtIds.size();
        if (coverArtUrls != null) total += coverArtUrls.size();

        Preferences.setMetadataSyncProgress(STAGE_COVER_ART, 0, total);
        Preferences.setMetadataSyncCoverArtProgress(0, total);
        logSync(STAGE_COVER_ART, "Prefetching cover art", false);

        int done = 0;
        if (coverArtIds != null && !coverArtIds.isEmpty()) {
            for (String coverArtId : coverArtIds) {
                if (coverArtId == null || coverArtId.isEmpty()) continue;
                CoverArtPrefetcher.prefetch(context, coverArtId, CustomGlideRequest.ResourceType.Album);
                done++;
                if (done % 25 == 0 || done == total) {
                    Preferences.setMetadataSyncProgress(STAGE_COVER_ART, done, total);
                    Preferences.setMetadataSyncCoverArtProgress(done, total);
                    logProgress(STAGE_COVER_ART, done, total, "Cover art cached", LOG_INTERVAL_LARGE);
                }
            }
        }

        if (coverArtUrls != null && !coverArtUrls.isEmpty()) {
            for (String url : coverArtUrls) {
                if (url == null || url.isEmpty()) continue;
                CoverArtPrefetcher.prefetchUrl(context, url);
                done++;
                if (done % 25 == 0 || done == total) {
                    Preferences.setMetadataSyncProgress(STAGE_COVER_ART, done, total);
                    Preferences.setMetadataSyncCoverArtProgress(done, total);
                    logProgress(STAGE_COVER_ART, done, total, "Cover art cached", LOG_INTERVAL_LARGE);
                }
            }
        }

        Preferences.setMetadataSyncProgress(STAGE_COVER_ART, done, total);
        Preferences.setMetadataSyncCoverArtProgress(done, total);
        logSync(STAGE_COVER_ART, "Cover art cached (" + done + ")", true);
    }

    private static void syncLyrics(CacheRepository cacheRepository, Map<String, Child> allSongs) {
        if (cacheRepository == null || allSongs == null || allSongs.isEmpty()) {
            Preferences.setMetadataSyncLyricsProgress(0, 0);
            return;
        }
        if (NetworkUtil.isOffline()) {
            return;
        }
        if (!Preferences.isOpenSubsonic()) {
            return;
        }
        if (Preferences.isDataSavingMode()) {
            return;
        }

        int total = allSongs.size();
        Preferences.setMetadataSyncProgress(STAGE_LYRICS, 0, total);
        Preferences.setMetadataSyncLyricsProgress(0, total);
        logSync(STAGE_LYRICS, "Syncing lyrics", false);
        int done = 0;

        for (Child song : allSongs.values()) {
            if (song == null || song.getId() == null || song.getId().isEmpty()) continue;
            if (NetworkUtil.isOffline()) break;
            try {
                Call<ApiResponse> call = App.getSubsonicClientInstance(false)
                        .getOpenClient()
                        .getLyricsBySongId(song.getId());
                Response<ApiResponse> response = call.execute();
                if (response.isSuccessful()
                        && response.body() != null
                        && response.body().getSubsonicResponse().getLyricsList() != null) {
                    cacheRepository.save("lyrics_song_" + song.getId(), response.body().getSubsonicResponse().getLyricsList());
                }
            } catch (Exception ignored) {
            }
            done++;
            if (done % 25 == 0 || done == total) {
                Preferences.setMetadataSyncProgress(STAGE_LYRICS, done, total);
                Preferences.setMetadataSyncLyricsProgress(done, total);
                logProgress(STAGE_LYRICS, done, total, "Lyrics cached", LOG_INTERVAL_LARGE);
            }
        }

        Preferences.setMetadataSyncProgress(STAGE_LYRICS, done, total);
        Preferences.setMetadataSyncLyricsProgress(done, total);
        logSync(STAGE_LYRICS, "Lyrics cached (" + done + ")", true);
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
}
