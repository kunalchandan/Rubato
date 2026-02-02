package one.chandan.rubato.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import one.chandan.rubato.model.LibrarySearchEntry

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

    @Query(
        "SELECT * FROM library_search_entry " +
            "WHERE search_text LIKE '%' || :query || '%' " +
            "AND media_type = :mediaType " +
            "ORDER BY title COLLATE NOCASE " +
            "LIMIT :limit"
    )
    fun searchByType(query: String, mediaType: String, limit: Int): List<LibrarySearchEntry>
}
