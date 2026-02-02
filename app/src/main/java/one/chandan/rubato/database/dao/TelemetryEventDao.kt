package one.chandan.rubato.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import one.chandan.rubato.model.TelemetryEvent

@Dao
interface TelemetryEventDao {
    @Insert
    fun insert(event: TelemetryEvent)

    @Query("SELECT * FROM telemetry_event ORDER BY timestamp DESC LIMIT :limit")
    fun getLatest(limit: Int): List<TelemetryEvent>

    @Query("DELETE FROM telemetry_event")
    fun clearAll()
}
