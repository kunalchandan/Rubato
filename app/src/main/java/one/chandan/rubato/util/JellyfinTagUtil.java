package one.chandan.rubato.util;

import androidx.annotation.Nullable;

import one.chandan.rubato.jellyfin.JellyfinMediaUtil;
import one.chandan.rubato.subsonic.models.AlbumID3;
import one.chandan.rubato.subsonic.models.ArtistID3;
import one.chandan.rubato.subsonic.models.Child;
import one.chandan.rubato.subsonic.models.Playlist;

import java.util.ArrayList;
import java.util.List;

public final class JellyfinTagUtil {
    private JellyfinTagUtil() {
    }

    @Nullable
    public static String toTagged(@Nullable String rawId) {
        if (rawId == null || rawId.trim().isEmpty()) return rawId;
        return SearchIndexUtil.tagSourceId(SearchIndexUtil.SOURCE_JELLYFIN, rawId);
    }

    @Nullable
    public static String toRaw(@Nullable String maybeTagged) {
        if (maybeTagged == null) return null;
        if (!SearchIndexUtil.isJellyfinTagged(maybeTagged)) return maybeTagged;
        JellyfinMediaUtil.JellyfinTaggedId parsed = JellyfinMediaUtil.parseTaggedId(maybeTagged);
        if (parsed == null) return maybeTagged;
        return parsed.serverId + ":" + parsed.itemId;
    }

    @Nullable
    public static String extractServerId(@Nullable String rawOrTagged) {
        if (rawOrTagged == null) return null;
        if (SearchIndexUtil.isJellyfinTagged(rawOrTagged)) {
            JellyfinMediaUtil.JellyfinTaggedId parsed = JellyfinMediaUtil.parseTaggedId(rawOrTagged);
            return parsed != null ? parsed.serverId : null;
        }
        int split = rawOrTagged.indexOf(':');
        if (split <= 0) return null;
        return rawOrTagged.substring(0, split);
    }

    public static ArtistID3 tagArtist(@Nullable ArtistID3 artist) {
        if (artist == null) return null;
        artist.setId(toTagged(artist.getId()));
        artist.setCoverArtId(toTagged(artist.getCoverArtId()));
        return artist;
    }

    public static List<ArtistID3> tagArtists(@Nullable List<ArtistID3> artists) {
        List<ArtistID3> tagged = new ArrayList<>();
        if (artists == null) return tagged;
        for (ArtistID3 artist : artists) {
            if (artist == null) continue;
            tagged.add(tagArtist(artist));
        }
        return tagged;
    }

    public static AlbumID3 tagAlbum(@Nullable AlbumID3 album) {
        if (album == null) return null;
        album.setId(toTagged(album.getId()));
        album.setArtistId(toTagged(album.getArtistId()));
        album.setCoverArtId(toTagged(album.getCoverArtId()));
        return album;
    }

    public static List<AlbumID3> tagAlbums(@Nullable List<AlbumID3> albums) {
        List<AlbumID3> tagged = new ArrayList<>();
        if (albums == null) return tagged;
        for (AlbumID3 album : albums) {
            if (album == null) continue;
            tagged.add(tagAlbum(album));
        }
        return tagged;
    }

    public static Child tagSong(@Nullable Child song) {
        if (song == null) return null;
        String taggedId = toTagged(song.getId());
        Child tagged = new Child(taggedId != null ? taggedId : song.getId());
        tagged.setParentId(song.getParentId());
        tagged.setDir(song.isDir());
        tagged.setTitle(song.getTitle());
        tagged.setAlbum(song.getAlbum());
        tagged.setArtist(song.getArtist());
        tagged.setTrack(song.getTrack());
        tagged.setYear(song.getYear());
        tagged.setGenre(song.getGenre());
        tagged.setCoverArtId(toTagged(song.getCoverArtId()));
        tagged.setSize(song.getSize());
        tagged.setContentType(song.getContentType());
        tagged.setSuffix(song.getSuffix());
        tagged.setTranscodedContentType(song.getTranscodedContentType());
        tagged.setTranscodedSuffix(song.getTranscodedSuffix());
        tagged.setDuration(song.getDuration());
        tagged.setBitrate(song.getBitrate());
        tagged.setPath(song.getPath());
        tagged.setVideo(song.isVideo());
        tagged.setUserRating(song.getUserRating());
        tagged.setAverageRating(song.getAverageRating());
        tagged.setPlayCount(song.getPlayCount());
        tagged.setDiscNumber(song.getDiscNumber());
        tagged.setCreated(song.getCreated());
        tagged.setStarred(song.getStarred());
        tagged.setAlbumId(toTagged(song.getAlbumId()));
        tagged.setArtistId(toTagged(song.getArtistId()));
        tagged.setType(song.getType());
        tagged.setBookmarkPosition(song.getBookmarkPosition());
        tagged.setOriginalWidth(song.getOriginalWidth());
        tagged.setOriginalHeight(song.getOriginalHeight());
        return tagged;
    }

    public static List<Child> tagSongs(@Nullable List<Child> songs) {
        List<Child> tagged = new ArrayList<>();
        if (songs == null) return tagged;
        for (Child song : songs) {
            if (song == null) continue;
            tagged.add(tagSong(song));
        }
        return tagged;
    }

    public static Playlist tagPlaylist(@Nullable Playlist playlist) {
        if (playlist == null) return null;
        playlist.setId(toTagged(playlist.getId()));
        playlist.setCoverArtId(toTagged(playlist.getCoverArtId()));
        return playlist;
    }

    public static List<Playlist> tagPlaylists(@Nullable List<Playlist> playlists) {
        List<Playlist> tagged = new ArrayList<>();
        if (playlists == null) return tagged;
        for (Playlist playlist : playlists) {
            if (playlist == null) continue;
            tagged.add(tagPlaylist(playlist));
        }
        return tagged;
    }
}
