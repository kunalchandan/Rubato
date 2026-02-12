package one.chandan.rubato.model

import androidx.room.ColumnInfo

data class SearchSongLite(
    val uid: String,
    @ColumnInfo(name = "item_id")
    val itemId: String?,
    val source: String,
    val title: String?,
    val artist: String?,
    val album: String?,
    @ColumnInfo(name = "album_id")
    val albumId: String?,
    @ColumnInfo(name = "artist_id")
    val artistId: String?,
    @ColumnInfo(name = "cover_art")
    val coverArt: String?
)
