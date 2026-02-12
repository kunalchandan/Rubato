package one.chandan.rubato.model

import androidx.annotation.Keep
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Keep
@Entity(tableName = "telemetry_event")
data class TelemetryEvent(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "timestamp")
    val timestamp: Long,
    @ColumnInfo(name = "screen")
    val screen: String?,
    @ColumnInfo(name = "action")
    val action: String,
    @ColumnInfo(name = "detail")
    val detail: String?,
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,
    @ColumnInfo(name = "source")
    val source: String? = null,
    @ColumnInfo(name = "error")
    val error: String? = null
)
