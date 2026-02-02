package one.chandan.rubato.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import one.chandan.rubato.model.LocalSource;

import java.util.List;

@Dao
public interface LocalSourceDao {
    @Query("SELECT * FROM local_source ORDER BY display_name")
    LiveData<List<LocalSource>> getAll();

    @Query("SELECT * FROM local_source")
    List<LocalSource> getAllSync();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(LocalSource source);

    @Delete
    void delete(LocalSource source);

    @Query("DELETE FROM local_source WHERE id = :id")
    void deleteById(String id);
}
