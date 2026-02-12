package one.chandan.rubato.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import one.chandan.rubato.model.LibrarySearchEntry
import one.chandan.rubato.model.SearchSongLite

@Dao
interface LibrarySearchEntryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAll(entries: List<LibrarySearchEntry>)

    @Query("DELETE FROM library_search_entry WHERE source = :source")
    fun deleteBySource(source: String)

    @Query("DELETE FROM library_search_entry")
    fun clearAll()

    @Query("SELECT COUNT(*) FROM library_search_entry")
    fun count(): Int

    @Query("SELECT COUNT(*) FROM library_search_entry WHERE media_type = :mediaType")
    fun countByType(mediaType: String): Int

    @Query(
        "SELECT * FROM library_search_entry " +
            "WHERE search_text LIKE '%' || :query || '%' " +
            "AND media_type = :mediaType " +
            "ORDER BY title COLLATE NOCASE " +
            "LIMIT :limit"
    )
    fun searchByType(query: String, mediaType: String, limit: Int): List<LibrarySearchEntry>

    @Query(
        "SELECT uid, item_id, source, title, artist, album, album_id, artist_id, cover_art " +
            "FROM library_search_entry " +
            "WHERE media_type = :mediaType " +
            "ORDER BY uid"
    )
    fun getAllLiteByType(mediaType: String): List<SearchSongLite>

    @Query(
        "SELECT uid, item_id, source, title, artist, album, album_id, artist_id, cover_art " +
            "FROM library_search_entry " +
            "WHERE media_type = :mediaType " +
            "AND album_id = :albumId " +
            "ORDER BY uid"
    )
    fun getAllLiteByAlbumId(mediaType: String, albumId: String): List<SearchSongLite>

    @Query(
        "SELECT uid, item_id, source, title, artist, album, album_id, artist_id, cover_art " +
            "FROM library_search_entry " +
            "WHERE media_type = :mediaType " +
            "AND LOWER(album) = LOWER(:album) " +
            "AND (:artist IS NULL OR :artist = '' OR LOWER(artist) = LOWER(:artist)) " +
            "ORDER BY uid"
    )
    fun getAllLiteByAlbumMetadata(mediaType: String, album: String, artist: String?): List<SearchSongLite>
}
