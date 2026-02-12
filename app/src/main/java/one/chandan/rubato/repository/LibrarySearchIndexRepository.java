package one.chandan.rubato.repository;

import one.chandan.rubato.database.AppDatabase;
import one.chandan.rubato.database.dao.LibrarySearchEntryDao;
import one.chandan.rubato.model.LibrarySearchEntry;
import one.chandan.rubato.model.SearchSongLite;
import one.chandan.rubato.util.AppExecutors;
import one.chandan.rubato.util.SearchIndexUtil;

import java.util.Collections;
import java.util.List;

public class LibrarySearchIndexRepository {
    private final LibrarySearchEntryDao librarySearchEntryDao = AppDatabase.getInstance().librarySearchEntryDao();

    public void replaceSource(String source, List<LibrarySearchEntry> entries) {
        AppExecutors.io().execute(() -> {
            librarySearchEntryDao.deleteBySource(source);
            if (entries != null && !entries.isEmpty()) {
                librarySearchEntryDao.upsertAll(entries);
            }
        });
    }

    public void upsertAll(List<LibrarySearchEntry> entries) {
        if (entries == null || entries.isEmpty()) return;
        AppExecutors.io().execute(() -> librarySearchEntryDao.upsertAll(entries));
    }

    public void count(ResultCallback<Integer> callback) {
        AppExecutors.io().execute(() -> callback.onLoaded(librarySearchEntryDao.count()));
    }

    public void search(String query, int artistLimit, int albumLimit, int songLimit, SearchCallback callback) {
        AppExecutors.io().execute(() -> {
            String normalized = SearchIndexUtil.normalize(query);
            if (normalized.isEmpty()) {
                callback.onLoaded(Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
                return;
            }

            List<LibrarySearchEntry> artists = librarySearchEntryDao.searchByType(normalized, SearchIndexUtil.TYPE_ARTIST, artistLimit);
            List<LibrarySearchEntry> albums = librarySearchEntryDao.searchByType(normalized, SearchIndexUtil.TYPE_ALBUM, albumLimit);
            List<LibrarySearchEntry> songs = librarySearchEntryDao.searchByType(normalized, SearchIndexUtil.TYPE_SONG, songLimit);
            callback.onLoaded(artists, albums, songs);
        });
    }

    public void searchPlaylists(String query, int limit, ResultCallback<List<LibrarySearchEntry>> callback) {
        AppExecutors.io().execute(() -> {
            String normalized = SearchIndexUtil.normalize(query);
            if (normalized.isEmpty()) {
                callback.onLoaded(Collections.emptyList());
                return;
            }
            List<LibrarySearchEntry> playlists = librarySearchEntryDao.searchByType(normalized, SearchIndexUtil.TYPE_PLAYLIST, limit);
            callback.onLoaded(playlists);
        });
    }

    public void getAllSongs(ResultCallback<List<SearchSongLite>> callback) {
        AppExecutors.io().execute(() -> {
            List<SearchSongLite> songs = librarySearchEntryDao.getAllLiteByType(SearchIndexUtil.TYPE_SONG);
            callback.onLoaded(songs);
        });
    }

    public void getSongsByAlbum(String albumId, String albumName, String artistName, ResultCallback<List<SearchSongLite>> callback) {
        AppExecutors.io().execute(() -> {
            List<SearchSongLite> matches = Collections.emptyList();
            if (albumId != null && !albumId.trim().isEmpty()) {
                matches = librarySearchEntryDao.getAllLiteByAlbumId(SearchIndexUtil.TYPE_SONG, albumId);
            }
            if ((matches == null || matches.isEmpty()) && albumName != null && !albumName.trim().isEmpty()) {
                matches = librarySearchEntryDao.getAllLiteByAlbumMetadata(
                        SearchIndexUtil.TYPE_SONG,
                        albumName,
                        artistName
                );
            }
            callback.onLoaded(matches != null ? matches : Collections.emptyList());
        });
    }

    public interface ResultCallback<T> {
        void onLoaded(T value);
    }

    public interface SearchCallback {
        void onLoaded(List<LibrarySearchEntry> artists, List<LibrarySearchEntry> albums, List<LibrarySearchEntry> songs);
    }
}
