package one.chandan.rubato.util;

import one.chandan.rubato.model.LibrarySearchEntry;
import one.chandan.rubato.subsonic.models.AlbumID3;
import one.chandan.rubato.subsonic.models.ArtistID3;
import one.chandan.rubato.subsonic.models.Child;
import one.chandan.rubato.subsonic.models.Playlist;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SearchIndexBuilderTest {

    @Test
    public void buildFromSubsonic_setsSourceAndTypes() {
        ArtistID3 artist = new ArtistID3();
        artist.setId("artist-1");
        artist.setName("Artist One");

        AlbumID3 album = new AlbumID3();
        album.setId("album-1");
        album.setName("Album One");
        album.setArtist("Artist One");
        album.setArtistId("artist-1");

        Child song = new Child();
        song.setId("song-1");
        song.setTitle("Song One");
        song.setArtist("Artist One");
        song.setAlbum("Album One");
        song.setAlbumId("album-1");
        song.setArtistId("artist-1");

        Playlist playlist = new Playlist();
        playlist.setId("playlist-1");
        playlist.setName("Mix One");

        List<LibrarySearchEntry> entries = SearchIndexBuilder.buildFromSubsonic(
                Arrays.asList(artist),
                Arrays.asList(album),
                Arrays.asList(song),
                Arrays.asList(playlist)
        );

        assertEquals(4, entries.size());

        LibrarySearchEntry artistEntry = entries.stream()
                .filter(entry -> SearchIndexUtil.TYPE_ARTIST.equals(entry.getItemType()))
                .findFirst()
                .orElse(null);
        assertNotNull(artistEntry);
        assertEquals(SearchIndexUtil.SOURCE_SUBSONIC, artistEntry.getSource());
        assertEquals("Artist One", artistEntry.getTitle());
        assertEquals(SearchIndexUtil.buildUid(SearchIndexUtil.SOURCE_SUBSONIC, SearchIndexUtil.TYPE_ARTIST, "artist-1", "Artist One", null, null), artistEntry.getUid());

        LibrarySearchEntry albumEntry = entries.stream()
                .filter(entry -> SearchIndexUtil.TYPE_ALBUM.equals(entry.getItemType()))
                .findFirst()
                .orElse(null);
        assertNotNull(albumEntry);
        assertEquals("Album One", albumEntry.getTitle());

        LibrarySearchEntry songEntry = entries.stream()
                .filter(entry -> SearchIndexUtil.TYPE_SONG.equals(entry.getItemType()))
                .findFirst()
                .orElse(null);
        assertNotNull(songEntry);
        assertEquals("Song One", songEntry.getTitle());

        LibrarySearchEntry playlistEntry = entries.stream()
                .filter(entry -> SearchIndexUtil.TYPE_PLAYLIST.equals(entry.getItemType()))
                .findFirst()
                .orElse(null);
        assertNotNull(playlistEntry);
        assertEquals("Mix One", playlistEntry.getTitle());
    }

    @Test
    public void buildFromSource_skipsBlankTitles() {
        AlbumID3 album = new AlbumID3();
        album.setId("album-blank");
        album.setName("  ");

        List<LibrarySearchEntry> entries = SearchIndexBuilder.buildFromSource(
                SearchIndexUtil.SOURCE_SUBSONIC,
                null,
                Arrays.asList(album),
                null,
                null
        );

        assertTrue(entries.isEmpty());
    }
}
