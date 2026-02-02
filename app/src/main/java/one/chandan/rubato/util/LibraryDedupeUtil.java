package one.chandan.rubato.util;

import one.chandan.rubato.repository.LocalMusicRepository;
import one.chandan.rubato.subsonic.models.AlbumID3;
import one.chandan.rubato.subsonic.models.ArtistID3;
import one.chandan.rubato.subsonic.models.Child;
import one.chandan.rubato.subsonic.models.Playlist;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LibraryDedupeUtil {
    private LibraryDedupeUtil() {
    }

    public static List<ArtistID3> mergeArtists(List<ArtistID3> base, List<ArtistID3> extra) {
        List<ArtistID3> merged = new ArrayList<>();
        if (base != null) merged.addAll(base);
        if (extra != null) merged.addAll(extra);
        return dedupeArtistsBySignature(merged);
    }

    public static List<AlbumID3> mergeAlbums(List<AlbumID3> base, List<AlbumID3> extra) {
        List<AlbumID3> merged = new ArrayList<>();
        if (base != null) merged.addAll(base);
        if (extra != null) merged.addAll(extra);
        return dedupeAlbumsBySignature(merged);
    }

    public static List<Child> mergeSongs(List<Child> base, List<Child> extra) {
        List<Child> merged = new ArrayList<>();
        if (base != null) merged.addAll(base);
        if (extra != null) merged.addAll(extra);
        return dedupeSongsBySignature(merged);
    }

    public static List<Playlist> mergePlaylists(List<Playlist> base, List<Playlist> extra) {
        List<Playlist> merged = new ArrayList<>();
        if (base != null) merged.addAll(base);
        if (extra != null) merged.addAll(extra);
        return dedupePlaylistsBySignature(merged);
    }

    public static List<ArtistID3> dedupeArtistsBySignature(List<ArtistID3> artists) {
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

    public static List<AlbumID3> dedupeAlbumsBySignature(List<AlbumID3> albums) {
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

    public static List<Child> dedupeSongsBySignature(List<Child> songs) {
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

    public static List<Playlist> dedupePlaylistsBySignature(List<Playlist> playlists) {
        if (playlists == null || playlists.isEmpty()) return Collections.emptyList();
        Map<String, Playlist> deduped = new LinkedHashMap<>();
        for (Playlist playlist : playlists) {
            if (playlist == null) continue;
            String key = SearchIndexUtil.normalize(playlist.getName());
            if (key.isEmpty()) {
                key = playlist.getId() != null ? playlist.getId() : String.valueOf(playlist.hashCode());
            }
            Playlist existing = deduped.get(key);
            if (existing == null || shouldPreferPlaylist(playlist, existing)) {
                deduped.put(key, playlist);
            }
        }
        return new ArrayList<>(deduped.values());
    }

    private static boolean shouldPreferArtist(ArtistID3 candidate, ArtistID3 existing) {
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

    private static boolean shouldPreferAlbum(AlbumID3 candidate, AlbumID3 existing) {
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

    private static boolean shouldPreferSong(Child candidate, Child existing) {
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

    private static boolean shouldPreferPlaylist(Playlist candidate, Playlist existing) {
        int candidateRank = SearchIndexUtil.sourcePriority(resolvePlaylistSource(candidate));
        int existingRank = SearchIndexUtil.sourcePriority(resolvePlaylistSource(existing));
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

    private static String resolveArtistSource(ArtistID3 artist) {
        if (artist == null) return SearchIndexUtil.SOURCE_SUBSONIC;
        String id = artist.getId();
        if (SearchIndexUtil.isJellyfinTagged(id)) return SearchIndexUtil.SOURCE_JELLYFIN;
        if (LocalMusicRepository.isLocalArtistId(id)) return SearchIndexUtil.SOURCE_LOCAL;
        return SearchIndexUtil.SOURCE_SUBSONIC;
    }

    private static String resolveAlbumSource(AlbumID3 album) {
        if (album == null) return SearchIndexUtil.SOURCE_SUBSONIC;
        String id = album.getId();
        if (SearchIndexUtil.isJellyfinTagged(id)) return SearchIndexUtil.SOURCE_JELLYFIN;
        if (LocalMusicRepository.isLocalAlbumId(id)) return SearchIndexUtil.SOURCE_LOCAL;
        return SearchIndexUtil.SOURCE_SUBSONIC;
    }

    private static String resolveSongSource(Child song) {
        if (song == null) return SearchIndexUtil.SOURCE_SUBSONIC;
        String id = song.getId();
        if (SearchIndexUtil.isJellyfinTagged(id)) return SearchIndexUtil.SOURCE_JELLYFIN;
        if (LocalMusicRepository.isLocalSongId(id)) return SearchIndexUtil.SOURCE_LOCAL;
        return SearchIndexUtil.SOURCE_SUBSONIC;
    }

    private static String resolvePlaylistSource(Playlist playlist) {
        if (playlist == null) return SearchIndexUtil.SOURCE_SUBSONIC;
        String id = playlist.getId();
        if (SearchIndexUtil.isJellyfinTagged(id)) return SearchIndexUtil.SOURCE_JELLYFIN;
        return SearchIndexUtil.SOURCE_SUBSONIC;
    }
}
