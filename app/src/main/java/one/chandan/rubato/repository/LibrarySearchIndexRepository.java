package one.chandan.rubato.repository;

import one.chandan.rubato.database.AppDatabase;
import one.chandan.rubato.database.dao.LibrarySearchEntryDao;
import one.chandan.rubato.model.LibrarySearchEntry;
import one.chandan.rubato.util.SearchIndexUtil;

import java.util.Collections;
import java.util.List;

public class LibrarySearchIndexRepository {
    private final LibrarySearchEntryDao librarySearchEntryDao = AppDatabase.getInstance().librarySearchEntryDao();

    public void replaceSource(String source, List<LibrarySearchEntry> entries) {
        new Thread(() -> {
            librarySearchEntryDao.deleteBySource(source);
            if (entries != null && !entries.isEmpty()) {
                librarySearchEntryDao.upsertAll(entries);
            }
        }).start();
    }

    public void upsertAll(List<LibrarySearchEntry> entries) {
        if (entries == null || entries.isEmpty()) return;
        new Thread(() -> librarySearchEntryDao.upsertAll(entries)).start();
    }

    public void count(ResultCallback<Integer> callback) {
        new Thread(() -> callback.onLoaded(librarySearchEntryDao.count())).start();
    }

    public void search(String query, int artistLimit, int albumLimit, int songLimit, SearchCallback callback) {
        new Thread(() -> {
            String normalized = SearchIndexUtil.normalize(query);
            if (normalized.isEmpty()) {
                callback.onLoaded(Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
                return;
            }

            List<LibrarySearchEntry> artists = librarySearchEntryDao.searchByType(normalized, SearchIndexUtil.TYPE_ARTIST, artistLimit);
            List<LibrarySearchEntry> albums = librarySearchEntryDao.searchByType(normalized, SearchIndexUtil.TYPE_ALBUM, albumLimit);
            List<LibrarySearchEntry> songs = librarySearchEntryDao.searchByType(normalized, SearchIndexUtil.TYPE_SONG, songLimit);
            callback.onLoaded(artists, albums, songs);
        }).start();
    }

    public void searchPlaylists(String query, int limit, ResultCallback<List<LibrarySearchEntry>> callback) {
        new Thread(() -> {
            String normalized = SearchIndexUtil.normalize(query);
            if (normalized.isEmpty()) {
                callback.onLoaded(Collections.emptyList());
                return;
            }
            List<LibrarySearchEntry> playlists = librarySearchEntryDao.searchByType(normalized, SearchIndexUtil.TYPE_PLAYLIST, limit);
            callback.onLoaded(playlists);
        }).start();
    }

    public interface ResultCallback<T> {
        void onLoaded(T value);
    }

    public interface SearchCallback {
        void onLoaded(List<LibrarySearchEntry> artists, List<LibrarySearchEntry> albums, List<LibrarySearchEntry> songs);
    }
}
