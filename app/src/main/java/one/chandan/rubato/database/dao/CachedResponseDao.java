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

    @Query("SELECT * FROM cached_response WHERE cache_key LIKE :prefix ORDER BY cache_key ASC")
    List<CachedResponse> getLike(String prefix);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(CachedResponse cachedResponse);

    @Query("DELETE FROM cached_response WHERE cache_key = :key")
    void delete(String key);

    @Query("DELETE FROM cached_response WHERE cache_key LIKE :prefix")
    void deleteLike(String prefix);

    @Query("SELECT SUM(LENGTH(payload)) FROM cached_response WHERE cache_key IN (:keys)")
    Long getPayloadSize(List<String> keys);

    @Query("SELECT LENGTH(payload) FROM cached_response WHERE cache_key = :key LIMIT 1")
    Long getPayloadSize(String key);

    @Query("SELECT SUM(LENGTH(payload)) FROM cached_response WHERE cache_key LIKE :prefix")
    Long getPayloadSizeLike(String prefix);
}
