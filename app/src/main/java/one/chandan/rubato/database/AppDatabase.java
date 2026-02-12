package one.chandan.rubato.database;

import androidx.media3.common.util.UnstableApi;
import androidx.room.AutoMigration;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import one.chandan.rubato.App;
import one.chandan.rubato.database.converter.DateConverters;
import one.chandan.rubato.database.dao.CachedResponseDao;
import one.chandan.rubato.database.dao.ChronologyDao;
import one.chandan.rubato.database.dao.DownloadDao;
import one.chandan.rubato.database.dao.FavoriteDao;
import one.chandan.rubato.database.dao.LibrarySearchEntryDao;
import one.chandan.rubato.database.dao.LocalSourceDao;
import one.chandan.rubato.database.dao.PlaylistDao;
import one.chandan.rubato.database.dao.QueueDao;
import one.chandan.rubato.database.dao.RecentSearchDao;
import one.chandan.rubato.database.dao.ServerDao;
import one.chandan.rubato.database.dao.SessionMediaItemDao;
import one.chandan.rubato.database.dao.TelemetryEventDao;
import one.chandan.rubato.model.CachedResponse;
import one.chandan.rubato.model.Chronology;
import one.chandan.rubato.model.Download;
import one.chandan.rubato.model.Favorite;
import one.chandan.rubato.model.LibrarySearchEntry;
import one.chandan.rubato.model.LocalSource;
import one.chandan.rubato.model.Queue;
import one.chandan.rubato.model.RecentSearch;
import one.chandan.rubato.model.Server;
import one.chandan.rubato.model.SessionMediaItem;
import one.chandan.rubato.model.TelemetryEvent;
import one.chandan.rubato.subsonic.models.Playlist;
import one.chandan.rubato.BuildConfig;

@UnstableApi
@Database(
        version = 15,
        entities = {Queue.class, Server.class, RecentSearch.class, Download.class, Chronology.class, Favorite.class, SessionMediaItem.class, Playlist.class, CachedResponse.class, TelemetryEvent.class, LocalSource.class, LibrarySearchEntry.class},
        autoMigrations = {@AutoMigration(from = 9, to = 10), @AutoMigration(from = 10, to = 11), @AutoMigration(from = 11, to = 12), @AutoMigration(from = 12, to = 13), @AutoMigration(from = 13, to = 14)}
)
@TypeConverters({DateConverters.class})
public abstract class AppDatabase extends RoomDatabase {
    private final static String DB_NAME = "tempo_db";
    private static AppDatabase instance;
    private static final Migration MIGRATION_14_15_TELEMETRY = new Migration(14, 15) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("DROP TABLE IF EXISTS telemetry_event");
            database.execSQL("CREATE TABLE IF NOT EXISTS `telemetry_event` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `screen` TEXT, `action` TEXT NOT NULL, `detail` TEXT, `duration_ms` INTEGER NOT NULL, `source` TEXT, `error` TEXT)");
        }
    };

    public static synchronized AppDatabase getInstance() {
        if (instance == null) {
            RoomDatabase.Builder<AppDatabase> builder = Room.databaseBuilder(App.getContext(), AppDatabase.class, DB_NAME)
                    .addMigrations(MIGRATION_14_15_TELEMETRY);
            if (BuildConfig.DEBUG) {
                builder.fallbackToDestructiveMigration();
            }
            instance = builder.build();
        }

        return instance;
    }

    public abstract QueueDao queueDao();

    public abstract ServerDao serverDao();

    public abstract RecentSearchDao recentSearchDao();

    public abstract DownloadDao downloadDao();

    public abstract ChronologyDao chronologyDao();

    public abstract FavoriteDao favoriteDao();

    public abstract SessionMediaItemDao sessionMediaItemDao();

    public abstract PlaylistDao playlistDao();

    public abstract CachedResponseDao cachedResponseDao();

    public abstract TelemetryEventDao telemetryEventDao();

    public abstract LocalSourceDao localSourceDao();

    public abstract LibrarySearchEntryDao librarySearchEntryDao();
}
