package one.chandan.rubato.util;

import one.chandan.rubato.subsonic.models.AlbumID3;
import one.chandan.rubato.subsonic.models.Child;
import one.chandan.rubato.subsonic.models.Playlist;
import one.chandan.rubato.subsonic.models.ArtistID3;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LibraryDedupeUtilTest {

    @Test
    public void dedupeAlbums_prefersMoreDetailedEntry() {
        AlbumID3 sparse = new AlbumID3();
        sparse.setId("a1");
        sparse.setName("Album");
        sparse.setArtist("Artist");

        AlbumID3 detailed = new AlbumID3();
        detailed.setId("a2");
        detailed.setName("Album");
        detailed.setArtist("Artist");
        detailed.setYear(2020);
        detailed.setSongCount(10);

        List<AlbumID3> deduped = LibraryDedupeUtil.dedupeAlbumsBySignature(Arrays.asList(sparse, detailed));
        assertEquals(1, deduped.size());
        assertEquals("a2", deduped.get(0).getId());
    }

    @Test
    public void dedupeSongs_prefersMoreDetailedEntry() {
        Child sparse = new Child();
        sparse.setId("s1");
        sparse.setTitle("Song");
        sparse.setArtist("Artist");
        sparse.setAlbum("Album");

        Child detailed = new Child();
        detailed.setId("s2");
        detailed.setTitle("Song");
        detailed.setArtist("Artist");
        detailed.setAlbum("Album");
        detailed.setTrack(3);
        detailed.setYear(2021);

        List<Child> deduped = LibraryDedupeUtil.dedupeSongsBySignature(Arrays.asList(sparse, detailed));
        assertEquals(1, deduped.size());
        assertEquals("s2", deduped.get(0).getId());
    }

    @Test
    public void dedupePlaylists_prefersMoreDetailedEntry() {
        Playlist sparse = new Playlist();
        sparse.setId("p1");
        sparse.setName("Playlist");

        Playlist detailed = new Playlist();
        detailed.setId("p2");
        detailed.setName("Playlist");
        detailed.setOwner("owner");
        detailed.setSongCount(5);

        List<Playlist> deduped = LibraryDedupeUtil.dedupePlaylistsBySignature(Arrays.asList(sparse, detailed));
        assertEquals(1, deduped.size());
        assertEquals("p2", deduped.get(0).getId());
    }

    @Test
    public void dedupeArtists_handlesNullAndEmpty() {
        List<ArtistID3> deduped = LibraryDedupeUtil.dedupeArtistsBySignature(null);
        assertTrue(deduped.isEmpty());
        deduped = LibraryDedupeUtil.dedupeArtistsBySignature(Collections.emptyList());
        assertTrue(deduped.isEmpty());
    }
}
