package one.chandan.rubato.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "library_search_entry",
    indices = [
        Index(value = ["search_text"]),
        Index(value = ["media_type"]),
        Index(value = ["source"])
    ]
)
data class LibrarySearchEntry(
    @PrimaryKey
    @ColumnInfo(name = "uid")
    val uid: String,
    @ColumnInfo(name = "item_id")
    val itemId: String?,
    @ColumnInfo(name = "source")
    val source: String,
    @ColumnInfo(name = "media_type")
    val mediaType: String,
    @ColumnInfo(name = "title")
    val title: String?,
    @ColumnInfo(name = "artist")
    val artist: String?,
    @ColumnInfo(name = "album")
    val album: String?,
    @ColumnInfo(name = "album_id")
    val albumId: String?,
    @ColumnInfo(name = "artist_id")
    val artistId: String?,
    @ColumnInfo(name = "cover_art")
    val coverArt: String?,
    @ColumnInfo(name = "search_text")
    val searchText: String,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
