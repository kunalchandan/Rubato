package one.chandan.rubato.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import one.chandan.rubato.model.CachedResponse;
import java.util.List;

@Dao
public interface CachedResponseDao {
    @Query("SELECT * FROM cached_response WHERE cache_key = :key LIMIT 1")
    CachedResponse get(String key);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(CachedResponse cachedResponse);

    @Query("DELETE FROM cached_response WHERE cache_key = :key")
    void delete(String key);

    @Query("SELECT SUM(LENGTH(payload)) FROM cached_response WHERE cache_key IN (:keys)")
    Long getPayloadSize(List<String> keys);

    @Query("SELECT SUM(LENGTH(payload)) FROM cached_response WHERE cache_key LIKE :prefix")
    Long getPayloadSizeLike(String prefix);
}
