package one.chandan.rubato.repository;

import androidx.lifecycle.LiveData;

import one.chandan.rubato.database.AppDatabase;
import one.chandan.rubato.database.dao.ChronologyDao;
import one.chandan.rubato.model.ArtistPlayStat;
import one.chandan.rubato.model.Chronology;
import one.chandan.rubato.util.AppExecutors;

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class ChronologyRepository {
    private final ChronologyDao chronologyDao = AppDatabase.getInstance().chronologyDao();

    public LiveData<List<Chronology>> getChronology(String server, long start, long end) {
        if (server == null || server.trim().isEmpty()) {
            return chronologyDao.getAllFromAny(start, end);
        }
        return chronologyDao.getAllFrom(start, end, server);
    }

    public List<Chronology> getLastPlayedSimple(String server, int count) {
        Future<List<Chronology>> future = AppExecutors.io().submit(() -> chronologyDao.getLastPlayedSimple(server, count));
        try {
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
        }
        return null;
    }

    public List<ArtistPlayStat> getArtistStats(String server) {
        Future<List<ArtistPlayStat>> future = AppExecutors.io().submit(() -> {
            if (server == null || server.trim().isEmpty()) {
                return chronologyDao.getArtistStatsAny();
            }
            return chronologyDao.getArtistStats(server);
        });
        try {
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
        }
        return null;
    }

    public void insert(Chronology item) {
        AppExecutors.io().execute(() -> chronologyDao.insert(item));
    }
}
