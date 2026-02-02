package one.chandan.rubato.repository;

import androidx.annotation.Nullable;

import one.chandan.rubato.jellyfin.JellyfinMediaUtil;
import one.chandan.rubato.model.JellyfinServer;
import one.chandan.rubato.subsonic.models.AlbumID3;
import one.chandan.rubato.subsonic.models.ArtistID3;
import one.chandan.rubato.subsonic.models.Child;
import one.chandan.rubato.subsonic.models.Playlist;
import one.chandan.rubato.util.JellyfinTagUtil;
import one.chandan.rubato.util.LibraryDedupeUtil;
import one.chandan.rubato.util.SearchIndexUtil;

import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class JellyfinCacheRepository {
    private final CacheRepository cacheRepository = new CacheRepository();
    private final JellyfinServerRepository serverRepository = new JellyfinServerRepository();

    public void loadAllArtists(CacheRepository.CacheResult<List<ArtistID3>> callback) {
        Type type = new TypeToken<List<ArtistID3>>() {}.getType();
        loadAcrossServers("artists_all", type, JellyfinTagUtil::tagArtists, callback);
    }

    public void loadAllAlbums(CacheRepository.CacheResult<List<AlbumID3>> callback) {
        Type type = new TypeToken<List<AlbumID3>>() {}.getType();
        loadAcrossServers("albums_all", type, JellyfinTagUtil::tagAlbums, callback);
    }

    public void loadAllSongs(CacheRepository.CacheResult<List<Child>> callback) {
        Type type = new TypeToken<List<Child>>() {}.getType();
        loadAcrossServers("songs_all", type, JellyfinTagUtil::tagSongs, callback);
    }

    public void loadAllPlaylists(CacheRepository.CacheResult<List<Playlist>> callback) {
        Type type = new TypeToken<List<Playlist>>() {}.getType();
        loadAcrossServers("playlists", type, JellyfinTagUtil::tagPlaylists, callback);
    }

    public void loadArtist(String taggedId, CacheRepository.CacheResult<ArtistID3> callback) {
        JellyfinMediaUtil.JellyfinTaggedId parsed = JellyfinMediaUtil.parseTaggedId(taggedId);
        if (parsed == null) {
            callback.onLoaded(null);
            return;
        }
        String rawId = parsed.serverId + ":" + parsed.itemId;
        String key = buildKey(parsed.serverId, "artists_all");
        Type type = new TypeToken<List<ArtistID3>>() {}.getType();
        cacheRepository.loadOrNull(key, type, new CacheRepository.CacheResult<List<ArtistID3>>() {
            @Override
            public void onLoaded(List<ArtistID3> artists) {
                if (artists != null) {
                    for (ArtistID3 artist : artists) {
                        if (artist != null && rawId.equals(artist.getId())) {
                            callback.onLoaded(JellyfinTagUtil.tagArtist(artist));
                            return;
                        }
                    }
                }
                callback.onLoaded(null);
            }
        });
    }

    public void loadAlbum(String taggedId, CacheRepository.CacheResult<AlbumID3> callback) {
        JellyfinMediaUtil.JellyfinTaggedId parsed = JellyfinMediaUtil.parseTaggedId(taggedId);
        if (parsed == null) {
            callback.onLoaded(null);
            return;
        }
        String rawId = parsed.serverId + ":" + parsed.itemId;
        String key = buildKey(parsed.serverId, "albums_all");
        Type type = new TypeToken<List<AlbumID3>>() {}.getType();
        cacheRepository.loadOrNull(key, type, new CacheRepository.CacheResult<List<AlbumID3>>() {
            @Override
            public void onLoaded(List<AlbumID3> albums) {
                if (albums != null) {
                    for (AlbumID3 album : albums) {
                        if (album != null && rawId.equals(album.getId())) {
                            callback.onLoaded(JellyfinTagUtil.tagAlbum(album));
                            return;
                        }
                    }
                }
                callback.onLoaded(null);
            }
        });
    }

    public void loadPlaylist(String taggedId, CacheRepository.CacheResult<Playlist> callback) {
        JellyfinMediaUtil.JellyfinTaggedId parsed = JellyfinMediaUtil.parseTaggedId(taggedId);
        if (parsed == null) {
            callback.onLoaded(null);
            return;
        }
        String rawId = parsed.serverId + ":" + parsed.itemId;
        String key = buildKey(parsed.serverId, "playlists");
        Type type = new TypeToken<List<Playlist>>() {}.getType();
        cacheRepository.loadOrNull(key, type, new CacheRepository.CacheResult<List<Playlist>>() {
            @Override
            public void onLoaded(List<Playlist> playlists) {
                if (playlists != null) {
                    for (Playlist playlist : playlists) {
                        if (playlist != null && rawId.equals(playlist.getId())) {
                            callback.onLoaded(JellyfinTagUtil.tagPlaylist(playlist));
                            return;
                        }
                    }
                }
                callback.onLoaded(null);
            }
        });
    }

    public void loadAlbumsForArtist(String taggedArtistId, CacheRepository.CacheResult<List<AlbumID3>> callback) {
        JellyfinMediaUtil.JellyfinTaggedId parsed = JellyfinMediaUtil.parseTaggedId(taggedArtistId);
        if (parsed == null) {
            callback.onLoaded(Collections.emptyList());
            return;
        }
        String rawArtistId = parsed.serverId + ":" + parsed.itemId;
        String key = buildKey(parsed.serverId, "albums_all");
        Type type = new TypeToken<List<AlbumID3>>() {}.getType();
        cacheRepository.loadOrNull(key, type, new CacheRepository.CacheResult<List<AlbumID3>>() {
            @Override
            public void onLoaded(List<AlbumID3> albums) {
                List<AlbumID3> filtered = new ArrayList<>();
                if (albums != null) {
                    for (AlbumID3 album : albums) {
                        if (album == null) continue;
                        if (rawArtistId.equals(album.getArtistId())) {
                            filtered.add(JellyfinTagUtil.tagAlbum(album));
                        }
                    }
                }
                callback.onLoaded(filtered);
            }
        });
    }

    public void loadAlbumsForArtistName(String artistName, CacheRepository.CacheResult<List<AlbumID3>> callback) {
        if (artistName == null || artistName.trim().isEmpty()) {
            callback.onLoaded(Collections.emptyList());
            return;
        }
        String needle = SearchIndexUtil.normalize(artistName);
        loadAllAlbums(albums -> {
            List<AlbumID3> filtered = new ArrayList<>();
            if (albums != null) {
                for (AlbumID3 album : albums) {
                    if (album == null) continue;
                    String candidate = SearchIndexUtil.normalize(album.getArtist());
                    if (!candidate.isEmpty() && candidate.equals(needle)) {
                        filtered.add(album);
                    }
                }
            }
            callback.onLoaded(filtered);
        });
    }

    public void loadSongsForAlbum(String taggedAlbumId, CacheRepository.CacheResult<List<Child>> callback) {
        JellyfinMediaUtil.JellyfinTaggedId parsed = JellyfinMediaUtil.parseTaggedId(taggedAlbumId);
        if (parsed == null) {
            callback.onLoaded(Collections.emptyList());
            return;
        }
        String rawAlbumId = parsed.serverId + ":" + parsed.itemId;
        String key = buildKey(parsed.serverId, "songs_all");
        Type type = new TypeToken<List<Child>>() {}.getType();
        cacheRepository.loadOrNull(key, type, new CacheRepository.CacheResult<List<Child>>() {
            @Override
            public void onLoaded(List<Child> songs) {
                List<Child> filtered = new ArrayList<>();
                if (songs != null) {
                    for (Child song : songs) {
                        if (song == null) continue;
                        if (rawAlbumId.equals(song.getAlbumId())) {
                            filtered.add(JellyfinTagUtil.tagSong(song));
                        }
                    }
                }
                callback.onLoaded(filtered);
            }
        });
    }

    public void loadSongsForArtist(String taggedArtistId, CacheRepository.CacheResult<List<Child>> callback) {
        JellyfinMediaUtil.JellyfinTaggedId parsed = JellyfinMediaUtil.parseTaggedId(taggedArtistId);
        if (parsed == null) {
            callback.onLoaded(Collections.emptyList());
            return;
        }
        String rawArtistId = parsed.serverId + ":" + parsed.itemId;
        String key = buildKey(parsed.serverId, "songs_all");
        Type type = new TypeToken<List<Child>>() {}.getType();
        cacheRepository.loadOrNull(key, type, new CacheRepository.CacheResult<List<Child>>() {
            @Override
            public void onLoaded(List<Child> songs) {
                List<Child> filtered = new ArrayList<>();
                if (songs != null) {
                    for (Child song : songs) {
                        if (song == null) continue;
                        if (rawArtistId.equals(song.getArtistId())) {
                            filtered.add(JellyfinTagUtil.tagSong(song));
                        }
                    }
                }
                callback.onLoaded(filtered);
            }
        });
    }

    public void loadSongsForArtistName(String artistName, CacheRepository.CacheResult<List<Child>> callback) {
        if (artistName == null || artistName.trim().isEmpty()) {
            callback.onLoaded(Collections.emptyList());
            return;
        }
        String needle = SearchIndexUtil.normalize(artistName);
        loadAllSongs(songs -> {
            List<Child> filtered = new ArrayList<>();
            if (songs != null) {
                for (Child song : songs) {
                    if (song == null) continue;
                    String candidate = SearchIndexUtil.normalize(song.getArtist());
                    if (!candidate.isEmpty() && candidate.equals(needle)) {
                        filtered.add(song);
                    }
                }
            }
            callback.onLoaded(LibraryDedupeUtil.dedupeSongsBySignature(filtered));
        });
    }

    public void loadPlaylistSongs(String taggedPlaylistId, CacheRepository.CacheResult<List<Child>> callback) {
        JellyfinMediaUtil.JellyfinTaggedId parsed = JellyfinMediaUtil.parseTaggedId(taggedPlaylistId);
        if (parsed == null) {
            callback.onLoaded(Collections.emptyList());
            return;
        }
        String rawId = parsed.serverId + ":" + parsed.itemId;
        String key = "playlist_songs_" + rawId;
        Type type = new TypeToken<List<Child>>() {}.getType();
        cacheRepository.loadOrNull(key, type, new CacheRepository.CacheResult<List<Child>>() {
            @Override
            public void onLoaded(List<Child> songs) {
                List<Child> tagged = JellyfinTagUtil.tagSongs(songs);
                callback.onLoaded(tagged);
            }
        });
    }

    private <T> void loadAcrossServers(String suffix,
                                       Type type,
                                       ListTagger<T> tagger,
                                       CacheRepository.CacheResult<List<T>> callback) {
        List<JellyfinServer> servers = serverRepository.getServersSnapshot();
        if (servers == null || servers.isEmpty()) {
            callback.onLoaded(Collections.emptyList());
            return;
        }
        AtomicInteger remaining = new AtomicInteger(servers.size());
        List<T> merged = Collections.synchronizedList(new ArrayList<>());
        for (JellyfinServer server : servers) {
            if (server == null || server.getId() == null) {
                if (remaining.decrementAndGet() == 0) {
                    callback.onLoaded(new ArrayList<>(merged));
                }
                continue;
            }
            String key = buildKey(server.getId(), suffix);
            cacheRepository.loadOrNull(key, type, new CacheRepository.CacheResult<List<T>>() {
                @Override
                public void onLoaded(List<T> value) {
                    if (value != null) {
                        List<T> tagged = tagger != null ? tagger.tag(value) : value;
                        merged.addAll(tagged);
                    }
                    if (remaining.decrementAndGet() == 0) {
                        callback.onLoaded(new ArrayList<>(merged));
                    }
                }
            });
        }
    }

    private String buildKey(String serverId, String suffix) {
        return "jf_" + serverId + "_" + suffix;
    }

    private interface ListTagger<T> {
        List<T> tag(@Nullable List<T> items);
    }
}
