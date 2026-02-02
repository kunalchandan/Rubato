package one.chandan.rubato.util;

import one.chandan.rubato.model.LibrarySearchEntry;
import one.chandan.rubato.subsonic.models.AlbumID3;
import one.chandan.rubato.subsonic.models.ArtistID3;
import one.chandan.rubato.subsonic.models.Child;
import one.chandan.rubato.subsonic.models.Playlist;

import java.util.ArrayList;
import java.util.List;

public final class SearchIndexBuilder {
    private SearchIndexBuilder() {
    }

    public static List<LibrarySearchEntry> buildFromSubsonic(List<ArtistID3> artists,
                                                             List<AlbumID3> albums,
                                                             List<Child> songs) {
        return buildFromSubsonic(artists, albums, songs, null);
    }

    public static List<LibrarySearchEntry> buildFromSubsonic(List<ArtistID3> artists,
                                                             List<AlbumID3> albums,
                                                             List<Child> songs,
                                                             List<Playlist> playlists) {
        return buildFromSource(SearchIndexUtil.SOURCE_SUBSONIC, artists, albums, songs, playlists);
    }

    public static List<LibrarySearchEntry> buildFromSource(String source,
                                                           List<ArtistID3> artists,
                                                           List<AlbumID3> albums,
                                                           List<Child> songs,
                                                           List<Playlist> playlists) {
        List<LibrarySearchEntry> entries = new ArrayList<>();
        long now = System.currentTimeMillis();

        if (artists != null) {
            for (ArtistID3 artist : artists) {
                LibrarySearchEntry entry = fromArtist(artist, now, source);
                if (entry != null) entries.add(entry);
            }
        }

        if (albums != null) {
            for (AlbumID3 album : albums) {
                LibrarySearchEntry entry = fromAlbum(album, now, source);
                if (entry != null) entries.add(entry);
            }
        }

        if (songs != null) {
            for (Child song : songs) {
                LibrarySearchEntry entry = fromSong(song, now, source);
                if (entry != null) entries.add(entry);
            }
        }

        if (playlists != null) {
            for (Playlist playlist : playlists) {
                LibrarySearchEntry entry = fromPlaylist(playlist, now, source);
                if (entry != null) entries.add(entry);
            }
        }

        return entries;
    }

    private static LibrarySearchEntry fromArtist(ArtistID3 artist, long now, String source) {
        if (artist == null) return null;
        String title = artist.getName();
        if (title == null || title.trim().isEmpty()) return null;
        String itemId = artist.getId();
        String searchText = SearchIndexUtil.buildSearchText(title, null, null);
        String uid = SearchIndexUtil.buildUid(source, SearchIndexUtil.TYPE_ARTIST, itemId, title, null, null);
        return new LibrarySearchEntry(
                uid,
                itemId,
                source,
                SearchIndexUtil.TYPE_ARTIST,
                title,
                null,
                null,
                null,
                itemId,
                artist.getCoverArtId(),
                searchText,
                now
        );
    }

    private static LibrarySearchEntry fromAlbum(AlbumID3 album, long now, String source) {
        if (album == null) return null;
        String title = album.getName();
        if (title == null || title.trim().isEmpty()) return null;
        String itemId = album.getId();
        String artist = album.getArtist();
        String searchText = SearchIndexUtil.buildSearchText(title, artist, null);
        String uid = SearchIndexUtil.buildUid(source, SearchIndexUtil.TYPE_ALBUM, itemId, title, artist, null);
        return new LibrarySearchEntry(
                uid,
                itemId,
                source,
                SearchIndexUtil.TYPE_ALBUM,
                title,
                artist,
                title,
                itemId,
                album.getArtistId(),
                album.getCoverArtId(),
                searchText,
                now
        );
    }

    private static LibrarySearchEntry fromSong(Child song, long now, String source) {
        if (song == null) return null;
        String title = song.getTitle();
        if (title == null || title.trim().isEmpty()) return null;
        String itemId = song.getId();
        String artist = song.getArtist();
        String album = song.getAlbum();
        String searchText = SearchIndexUtil.buildSearchText(title, artist, album);
        String uid = SearchIndexUtil.buildUid(source, SearchIndexUtil.TYPE_SONG, itemId, title, artist, album);
        return new LibrarySearchEntry(
                uid,
                itemId,
                source,
                SearchIndexUtil.TYPE_SONG,
                title,
                artist,
                album,
                song.getAlbumId(),
                song.getArtistId(),
                song.getCoverArtId(),
                searchText,
                now
        );
    }

    private static LibrarySearchEntry fromPlaylist(Playlist playlist, long now, String source) {
        if (playlist == null) return null;
        String title = playlist.getName();
        if (title == null || title.trim().isEmpty()) return null;
        String itemId = playlist.getId();
        String searchText = SearchIndexUtil.buildSearchText(title, null, null);
        String uid = SearchIndexUtil.buildUid(source, SearchIndexUtil.TYPE_PLAYLIST, itemId, title, null, null);
        return new LibrarySearchEntry(
                uid,
                itemId,
                source,
                SearchIndexUtil.TYPE_PLAYLIST,
                title,
                null,
                null,
                null,
                null,
                playlist.getCoverArtId(),
                searchText,
                now
        );
    }
}
