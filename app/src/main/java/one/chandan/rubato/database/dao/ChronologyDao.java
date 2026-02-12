package one.chandan.rubato.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import one.chandan.rubato.model.Chronology;
import one.chandan.rubato.model.ArtistPlayStat;

import java.util.List;

@Dao
public interface ChronologyDao {
    @Query("SELECT * FROM chronology WHERE server == :server GROUP BY id ORDER BY timestamp DESC LIMIT :count")
    LiveData<List<Chronology>> getLastPlayed(String server, int count);

    @Query("SELECT * FROM chronology WHERE server == :server GROUP BY id ORDER BY timestamp DESC LIMIT :count")
    List<Chronology> getLastPlayedSimple(String server, int count);

    @Query("SELECT * FROM chronology WHERE timestamp >= :endDate AND timestamp < :startDate AND server == :server GROUP BY id ORDER BY COUNT(id) DESC LIMIT 20")
    LiveData<List<Chronology>> getAllFrom(long startDate, long endDate, String server);

    @Query("SELECT * FROM chronology WHERE timestamp >= :endDate AND timestamp < :startDate GROUP BY id ORDER BY COUNT(id) DESC LIMIT 20")
    LiveData<List<Chronology>> getAllFromAny(long startDate, long endDate);

    @Query("SELECT artist_id AS artistId, artist AS artistName, MAX(timestamp) AS lastPlayed, COUNT(*) AS playCount " +
            "FROM chronology WHERE server == :server AND artist_id IS NOT NULL GROUP BY artist_id")
    List<ArtistPlayStat> getArtistStats(String server);

    @Query("SELECT artist_id AS artistId, artist AS artistName, MAX(timestamp) AS lastPlayed, COUNT(*) AS playCount " +
            "FROM chronology WHERE artist_id IS NOT NULL GROUP BY artist_id")
    List<ArtistPlayStat> getArtistStatsAny();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Chronology chronologyObject);
}
