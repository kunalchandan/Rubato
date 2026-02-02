package one.chandan.rubato.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_response")
data class CachedResponse(
    @PrimaryKey
    @ColumnInfo(name = "cache_key")
    val cacheKey: String,
    @ColumnInfo(name = "payload")
    val payload: String,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
