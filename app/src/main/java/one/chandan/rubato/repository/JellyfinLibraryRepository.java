package one.chandan.rubato.repository;

import androidx.annotation.Nullable;

import one.chandan.rubato.jellyfin.JellyfinApi;
import one.chandan.rubato.jellyfin.model.JellyfinItem;
import one.chandan.rubato.jellyfin.model.JellyfinItemRef;
import one.chandan.rubato.jellyfin.model.JellyfinItemsResponse;
import one.chandan.rubato.model.JellyfinServer;
import one.chandan.rubato.model.LibrarySearchEntry;
import one.chandan.rubato.subsonic.models.AlbumID3;
import one.chandan.rubato.subsonic.models.ArtistID3;
import one.chandan.rubato.subsonic.models.Child;
import one.chandan.rubato.subsonic.models.Playlist;
import one.chandan.rubato.util.SearchIndexBuilder;
import one.chandan.rubato.util.SearchIndexUtil;
import one.chandan.rubato.util.Preferences;

import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class JellyfinLibraryRepository {
    private static final int PAGE_SIZE = 200;

    public List<LibrarySearchEntry> syncServer(JellyfinServer server, CacheRepository cacheRepository) {
        if (server == null || cacheRepository == null) return Collections.emptyList();
        JellyfinApi api = createApi(server.getAddress());

        List<JellyfinItem> artistItems = fetchAllItems(api, server, "MusicArtist");
        List<JellyfinItem> albumItems = fetchAllItems(api, server, "MusicAlbum");
        List<JellyfinItem> songItems = fetchAllItems(api, server, "Audio");
        List<JellyfinItem> playlistItems = fetchAllItems(api, server, "Playlist,MusicPlaylist");

        List<ArtistID3> artists = mapArtists(server, artistItems);
        List<AlbumID3> albums = mapAlbums(server, albumItems);
        List<Child> songs = mapSongs(server, songItems);
        List<Playlist> playlists = mapPlaylists(server, playlistItems);

        cacheRepository.save(buildKey(server, "artists_all"), artists);
        cacheRepository.save(buildKey(server, "albums_all"), albums);
        cacheRepository.save(buildKey(server, "songs_all"), songs);
        cacheRepository.save(buildKey(server, "playlists"), playlists);

        for (JellyfinItem playlistItem : playlistItems) {
            if (playlistItem == null || playlistItem.getId() == null) continue;
            String rawId = buildRawId(server, playlistItem.getId());
            List<JellyfinItem> playlistSongs = fetchPlaylistItems(api, server, playlistItem.getId());
            if (playlistSongs.isEmpty()) continue;
            List<Child> playlistEntries = mapSongs(server, playlistSongs);
            cacheRepository.save("playlist_songs_" + rawId, playlistEntries);
        }

        return SearchIndexBuilder.buildFromSource(
                SearchIndexUtil.SOURCE_JELLYFIN,
                artists,
                albums,
                songs,
                playlists
        );
    }

    @Nullable
    public LibrarySignature fetchSignature(JellyfinServer server) {
        if (server == null) return null;
        JellyfinApi api = createApi(server.getAddress());
        Integer artists = fetchCount(api, server, "MusicArtist");
        Integer albums = fetchCount(api, server, "MusicAlbum");
        Integer songs = fetchCount(api, server, "Audio");
        Integer playlists = fetchCount(api, server, "Playlist,MusicPlaylist");
        if (artists == null && albums == null && songs == null && playlists == null) {
            return null;
        }
        return new LibrarySignature(
                artists != null ? artists : 0,
                albums != null ? albums : 0,
                songs != null ? songs : 0,
                playlists != null ? playlists : 0
        );
    }

    private List<JellyfinItem> fetchAllItems(JellyfinApi api, JellyfinServer server, String includeItemTypes) {
        if (api == null || server == null) return Collections.emptyList();
        List<JellyfinItem> items = new ArrayList<>();
        int startIndex = 0;
        while (true) {
            try {
                Response<JellyfinItemsResponse> response = api.getItems(
                        server.getUserId(),
                        server.getLibraryId(),
                        includeItemTypes,
                        true,
                        startIndex,
                        PAGE_SIZE,
                        "SortName",
                        "Ascending",
                        null,
                        server.getAccessToken()
                ).execute();
                if (!response.isSuccessful() || response.body() == null) {
                    break;
                }
                List<JellyfinItem> batch = response.body().getItems();
                if (batch == null || batch.isEmpty()) {
                    break;
                }
                items.addAll(batch);
                Integer total = response.body().getTotalRecordCount();
                if (total != null && items.size() >= total) {
                    break;
                }
                startIndex += batch.size();
            } catch (Exception ignored) {
                break;
            }
        }
        return items;
    }

    private Integer fetchCount(JellyfinApi api, JellyfinServer server, String includeItemTypes) {
        if (api == null || server == null) return null;
        try {
            Response<JellyfinItemsResponse> response = api.getItems(
                    server.getUserId(),
                    server.getLibraryId(),
                    includeItemTypes,
                    true,
                    0,
                    1,
                    "SortName",
                    "Ascending",
                    null,
                    server.getAccessToken()
            ).execute();
            if (!response.isSuccessful() || response.body() == null) {
                return null;
            }
            return response.body().getTotalRecordCount();
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<JellyfinItem> fetchPlaylistItems(JellyfinApi api, JellyfinServer server, String playlistId) {
        if (api == null || server == null || playlistId == null) return Collections.emptyList();
        List<JellyfinItem> items = new ArrayList<>();
        int startIndex = 0;
        while (true) {
            try {
                Response<JellyfinItemsResponse> response = api.getPlaylistItems(
                        playlistId,
                        server.getUserId(),
                        startIndex,
                        PAGE_SIZE,
                        server.getAccessToken()
                ).execute();
                if (!response.isSuccessful() || response.body() == null) {
                    break;
                }
                List<JellyfinItem> batch = response.body().getItems();
                if (batch == null || batch.isEmpty()) {
                    break;
                }
                items.addAll(batch);
                Integer total = response.body().getTotalRecordCount();
                if (total != null && items.size() >= total) {
                    break;
                }
                startIndex += batch.size();
            } catch (Exception ignored) {
                break;
            }
        }
        return items;
    }

    private List<ArtistID3> mapArtists(JellyfinServer server, List<JellyfinItem> items) {
        if (items == null || items.isEmpty()) return Collections.emptyList();
        List<ArtistID3> mapped = new ArrayList<>();
        for (JellyfinItem item : items) {
            if (item == null || item.getId() == null) continue;
            ArtistID3 artist = new ArtistID3();
            String rawId = buildRawId(server, item.getId());
            artist.setId(rawId);
            artist.setName(item.getName());
            artist.setCoverArtId(rawId);
            mapped.add(artist);
        }
        return mapped;
    }

    private List<AlbumID3> mapAlbums(JellyfinServer server, List<JellyfinItem> items) {
        if (items == null || items.isEmpty()) return Collections.emptyList();
        List<AlbumID3> mapped = new ArrayList<>();
        for (JellyfinItem item : items) {
            if (item == null || item.getId() == null) continue;
            AlbumID3 album = new AlbumID3();
            String rawId = buildRawId(server, item.getId());
            album.setId(rawId);
            album.setName(item.getName());
            album.setArtist(resolveArtistName(item));
            String artistId = resolveArtistId(server, item);
            album.setArtistId(artistId);
            album.setCoverArtId(rawId);
            mapped.add(album);
        }
        return mapped;
    }

    private List<Child> mapSongs(JellyfinServer server, List<JellyfinItem> items) {
        if (items == null || items.isEmpty()) return Collections.emptyList();
        List<Child> mapped = new ArrayList<>();
        for (JellyfinItem item : items) {
            if (item == null || item.getId() == null) continue;
            String rawId = buildRawId(server, item.getId());
            Child song = new Child(rawId);
            song.setTitle(item.getName());
            song.setAlbum(item.getAlbum());
            String albumId = item.getAlbumId() != null ? buildRawId(server, item.getAlbumId()) : null;
            song.setAlbumId(albumId);
            song.setArtist(resolveArtistName(item));
            song.setArtistId(resolveArtistId(server, item));
            String coverArt = albumId != null ? albumId : rawId;
            song.setCoverArtId(coverArt);
            if (item.getRunTimeTicks() != null) {
                song.setDuration((int) (item.getRunTimeTicks() / 10000000L));
            }
            mapped.add(song);
        }
        return mapped;
    }

    private List<Playlist> mapPlaylists(JellyfinServer server, List<JellyfinItem> items) {
        if (items == null || items.isEmpty()) return Collections.emptyList();
        List<Playlist> mapped = new ArrayList<>();
        for (JellyfinItem item : items) {
            if (item == null || item.getId() == null) continue;
            String rawId = buildRawId(server, item.getId());
            Playlist playlist = new Playlist(rawId);
            playlist.setName(item.getName());
            playlist.setCoverArtId(rawId);
            mapped.add(playlist);
        }
        return mapped;
    }

    private String resolveArtistName(JellyfinItem item) {
        if (item == null) return null;
        if (item.getAlbumArtist() != null && !item.getAlbumArtist().isEmpty()) {
            return item.getAlbumArtist();
        }
        if (item.getArtists() != null && !item.getArtists().isEmpty()) {
            return item.getArtists().get(0);
        }
        return null;
    }

    @Nullable
    private String resolveArtistId(JellyfinServer server, JellyfinItem item) {
        if (item == null || item.getArtistItems() == null || item.getArtistItems().isEmpty()) return null;
        JellyfinItemRef ref = item.getArtistItems().get(0);
        if (ref == null || ref.getId() == null) return null;
        return buildRawId(server, ref.getId());
    }

    private String buildRawId(JellyfinServer server, String itemId) {
        return server.getId() + ":" + itemId;
    }

    private String buildKey(JellyfinServer server, String suffix) {
        return "jf_" + server.getId() + "_" + suffix;
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
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BASIC);

        return new OkHttpClient.Builder()
                .callTimeout(2, TimeUnit.MINUTES)
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(40, TimeUnit.SECONDS)
                .writeTimeout(40, TimeUnit.SECONDS)
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

    public static final class LibrarySignature {
        private final int artists;
        private final int albums;
        private final int songs;
        private final int playlists;

        public LibrarySignature(int artists, int albums, int songs, int playlists) {
            this.artists = artists;
            this.albums = albums;
            this.songs = songs;
            this.playlists = playlists;
        }

        public String asKey() {
            return artists + ":" + albums + ":" + songs + ":" + playlists;
        }
    }
}
