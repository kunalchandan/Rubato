package one.chandan.rubato.repository;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import one.chandan.rubato.model.LocalSource;
import one.chandan.rubato.repository.LocalSourceRepository;
import one.chandan.rubato.subsonic.models.AlbumID3;
import one.chandan.rubato.subsonic.models.ArtistID3;
import one.chandan.rubato.subsonic.models.Child;
import one.chandan.rubato.subsonic.models.Genre;
import one.chandan.rubato.util.AppExecutors;
import one.chandan.rubato.util.Constants;
import one.chandan.rubato.util.LocalMusicPermissions;
import one.chandan.rubato.util.Preferences;
import one.chandan.rubato.util.SearchIndexUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

public final class LocalMusicRepository {
    public static final String LOCAL_SONG_PREFIX = "local:";
    public static final String LOCAL_ALBUM_PREFIX = "local-album:";
    public static final String LOCAL_ARTIST_PREFIX = "local-artist:";

    private static final ExecutorService EXECUTOR = AppExecutors.localMusic();
    private static final Object LOCK = new Object();

    private static LocalLibrary cachedLibrary;

    private LocalMusicRepository() {
    }

    public interface ResultCallback<T> {
        void onResult(T value);
    }

    public static boolean isEnabled(Context context) {
        return Preferences.isLocalMusicEnabled() && LocalMusicPermissions.hasReadPermission(context);
    }

    public static boolean isLocalId(@Nullable String id) {
        return id != null && (id.startsWith(LOCAL_SONG_PREFIX) || id.startsWith(LOCAL_ALBUM_PREFIX) || id.startsWith(LOCAL_ARTIST_PREFIX));
    }

    public static boolean isLocalSongId(@Nullable String id) {
        return id != null && id.startsWith(LOCAL_SONG_PREFIX);
    }

    public static boolean isLocalAlbumId(@Nullable String id) {
        return id != null && id.startsWith(LOCAL_ALBUM_PREFIX);
    }

    public static boolean isLocalArtistId(@Nullable String id) {
        return id != null && id.startsWith(LOCAL_ARTIST_PREFIX);
    }

    public static boolean isLocalSong(@Nullable Child song) {
        return song != null && (Constants.MEDIA_TYPE_LOCAL.equals(song.getType()) || isLocalId(song.getId()));
    }

    public static void invalidateCache() {
        synchronized (LOCK) {
            cachedLibrary = null;
        }
    }

    public static void loadLibrary(Context context, ResultCallback<LocalLibrary> callback) {
        if (!isEnabled(context)) {
            callback.onResult(new LocalLibrary(Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList()));
            return;
        }

        LocalLibrary snapshot;
        synchronized (LOCK) {
            snapshot = cachedLibrary;
        }

        if (snapshot != null) {
            callback.onResult(snapshot);
            return;
        }

        EXECUTOR.execute(() -> {
            LocalLibrary library = buildLibrary(context);
            synchronized (LOCK) {
                cachedLibrary = library;
            }
            callback.onResult(library);
        });
    }

    public static void appendLocalSongs(Context context, List<Child> base, ResultCallback<List<Child>> callback) {
        loadLibrary(context, library -> {
            if (library.songs.isEmpty()) {
                callback.onResult(base);
                return;
            }
            List<Child> merged = new ArrayList<>(base != null ? base : Collections.emptyList());
            merged.addAll(library.songs);
            callback.onResult(merged);
        });
    }

    public static void appendLocalAlbums(Context context, List<AlbumID3> base, ResultCallback<List<AlbumID3>> callback) {
        loadLibrary(context, library -> {
            if (library.albums.isEmpty()) {
                callback.onResult(base);
                return;
            }
            List<AlbumID3> merged = new ArrayList<>(base != null ? base : Collections.emptyList());
            merged.addAll(library.albums);
            callback.onResult(merged);
        });
    }

    public static void appendLocalArtists(Context context, List<ArtistID3> base, ResultCallback<List<ArtistID3>> callback) {
        loadLibrary(context, library -> {
            if (library.artists.isEmpty()) {
                callback.onResult(base);
                return;
            }
            List<ArtistID3> merged = new ArrayList<>(base != null ? base : Collections.emptyList());
            merged.addAll(library.artists);
            callback.onResult(merged);
        });
    }

    public static void appendLocalGenres(Context context, List<Genre> base, ResultCallback<List<Genre>> callback) {
        loadLibrary(context, library -> {
            if (library.genres.isEmpty()) {
                callback.onResult(base);
                return;
            }
            List<Genre> merged = new ArrayList<>(base != null ? base : Collections.emptyList());
            merged.addAll(library.genres);
            callback.onResult(merged);
        });
    }

    public static void getLocalAlbum(Context context, String albumId, ResultCallback<AlbumID3> callback) {
        loadLibrary(context, library -> {
            for (AlbumID3 album : library.albums) {
                if (album != null && albumId != null && albumId.equals(album.getId())) {
                    callback.onResult(album);
                    return;
                }
            }
            callback.onResult(null);
        });
    }

    public static void getLocalSong(Context context, String songId, ResultCallback<Child> callback) {
        loadLibrary(context, library -> {
            for (Child song : library.songs) {
                if (song != null && songId != null && songId.equals(song.getId())) {
                    callback.onResult(song);
                    return;
                }
            }
            callback.onResult(null);
        });
    }

    public static void getLocalArtist(Context context, String artistId, ResultCallback<ArtistID3> callback) {
        loadLibrary(context, library -> {
            for (ArtistID3 artist : library.artists) {
                if (artist != null && artistId != null && artistId.equals(artist.getId())) {
                    callback.onResult(artist);
                    return;
                }
            }
            callback.onResult(null);
        });
    }

    public static void getLocalArtistAlbums(Context context, String artistId, ResultCallback<List<AlbumID3>> callback) {
        loadLibrary(context, library -> {
            if (artistId == null) {
                callback.onResult(Collections.emptyList());
                return;
            }
            List<AlbumID3> filtered = new ArrayList<>();
            for (AlbumID3 album : library.albums) {
                if (album != null && artistId.equals(album.getArtistId())) {
                    filtered.add(album);
                }
            }
            callback.onResult(filtered);
        });
    }

    public static void getLocalAlbumSongs(Context context, String albumId, ResultCallback<List<Child>> callback) {
        loadLibrary(context, library -> {
            if (albumId == null) {
                callback.onResult(Collections.emptyList());
                return;
            }
            List<Child> filtered = new ArrayList<>();
            for (Child song : library.songs) {
                if (song != null && albumId.equals(song.getAlbumId())) {
                    filtered.add(song);
                }
            }
            callback.onResult(filtered);
        });
    }

    public static void getLocalAlbumSongsByMetadata(Context context, String albumName, String artistName, ResultCallback<List<Child>> callback) {
        loadLibrary(context, library -> {
            if (TextUtils.isEmpty(albumName)) {
                callback.onResult(Collections.emptyList());
                return;
            }
            String targetAlbum = SearchIndexUtil.normalize(albumName);
            String targetArtist = SearchIndexUtil.normalize(artistName);
            boolean matchArtist = !TextUtils.isEmpty(targetArtist);
            List<Child> filtered = new ArrayList<>();
            for (Child song : library.songs) {
                if (song == null) continue;
                String album = SearchIndexUtil.normalize(song.getAlbum());
                if (!targetAlbum.equals(album)) continue;
                if (matchArtist) {
                    String artist = SearchIndexUtil.normalize(song.getArtist());
                    if (!targetArtist.equals(artist)) continue;
                }
                filtered.add(song);
            }
            callback.onResult(filtered);
        });
    }

    public static void getLocalArtistSongs(Context context, String artistId, ResultCallback<List<Child>> callback) {
        loadLibrary(context, library -> {
            if (artistId == null) {
                callback.onResult(Collections.emptyList());
                return;
            }
            List<Child> filtered = new ArrayList<>();
            for (Child song : library.songs) {
                if (song != null && artistId.equals(song.getArtistId())) {
                    filtered.add(song);
                }
            }
            callback.onResult(filtered);
        });
    }

    public static void getLocalArtistSongsByName(Context context, String artistName, ResultCallback<List<Child>> callback) {
        loadLibrary(context, library -> {
            if (TextUtils.isEmpty(artistName)) {
                callback.onResult(Collections.emptyList());
                return;
            }
            List<Child> filtered = new ArrayList<>();
            for (Child song : library.songs) {
                if (song != null && artistName.equalsIgnoreCase(song.getArtist())) {
                    filtered.add(song);
                }
            }
            callback.onResult(filtered);
        });
    }

    public static void getLocalSongsByGenre(Context context, String genreId, ResultCallback<List<Child>> callback) {
        loadLibrary(context, library -> {
            if (TextUtils.isEmpty(genreId)) {
                callback.onResult(Collections.emptyList());
                return;
            }
            List<Child> filtered = new ArrayList<>();
            for (Child song : library.songs) {
                if (song != null && song.getGenre() != null && song.getGenre().equalsIgnoreCase(genreId)) {
                    filtered.add(song);
                }
            }
            callback.onResult(filtered);
        });
    }

    public static void getLocalSongsByGenres(Context context, List<String> genres, ResultCallback<List<Child>> callback) {
        loadLibrary(context, library -> {
            if (genres == null || genres.isEmpty()) {
                callback.onResult(Collections.emptyList());
                return;
            }
            List<Child> filtered = new ArrayList<>();
            for (Child song : library.songs) {
                if (song == null || song.getGenre() == null) continue;
                for (String genre : genres) {
                    if (genre != null && song.getGenre().equalsIgnoreCase(genre)) {
                        filtered.add(song);
                        break;
                    }
                }
            }
            callback.onResult(filtered);
        });
    }

    public static void getLocalSongsByYearRange(Context context, Integer fromYear, Integer toYear, ResultCallback<List<Child>> callback) {
        loadLibrary(context, library -> {
            if (fromYear == null && toYear == null) {
                callback.onResult(new ArrayList<>(library.songs));
                return;
            }
            int minYear = fromYear != null ? fromYear : Integer.MIN_VALUE;
            int maxYear = toYear != null ? toYear : Integer.MAX_VALUE;
            List<Child> filtered = new ArrayList<>();
            for (Child song : library.songs) {
                if (song == null) continue;
                Integer year = song.getYear();
                if (year == null) continue;
                if (year >= minYear && year <= maxYear) {
                    filtered.add(song);
                }
            }
            callback.onResult(filtered);
        });
    }

    public static void search(Context context, String query, ResultCallback<LocalSearchResult> callback) {
        if (TextUtils.isEmpty(query)) {
            callback.onResult(new LocalSearchResult(Collections.emptyList(), Collections.emptyList(), Collections.emptyList()));
            return;
        }

        String needle = query.toLowerCase();
        loadLibrary(context, library -> {
            List<Child> songs = new ArrayList<>();
            List<AlbumID3> albums = new ArrayList<>();
            List<ArtistID3> artists = new ArrayList<>();

            for (Child song : library.songs) {
                if (song != null && contains(song.getTitle(), needle)) {
                    songs.add(song);
                }
            }

            for (AlbumID3 album : library.albums) {
                if (album != null && (contains(album.getName(), needle) || contains(album.getArtist(), needle))) {
                    albums.add(album);
                }
            }

            for (ArtistID3 artist : library.artists) {
                if (artist != null && contains(artist.getName(), needle)) {
                    artists.add(artist);
                }
            }

            callback.onResult(new LocalSearchResult(artists, albums, songs));
        });
    }

    private static boolean contains(@Nullable String value, String needle) {
        return value != null && value.toLowerCase().contains(needle);
    }

    private static LocalLibrary buildLibrary(Context context) {
        ContentResolver resolver = context.getContentResolver();
        List<Child> songs = new ArrayList<>();
        Map<Long, Child> songById = new HashMap<>();
        Map<Long, AlbumAccumulator> albumMap = new HashMap<>();
        Map<Long, ArtistAccumulator> artistMap = new HashMap<>();

        List<LocalSource> sources = new LocalSourceRepository().getSourcesSync();
        List<String> allowedPaths = normalizeAllowedPaths(sources);
        boolean filterEnabled = allowedPaths != null && !allowedPaths.isEmpty();

        List<String> projectionList = new ArrayList<>();
        projectionList.add(MediaStore.Audio.Media._ID);
        projectionList.add(MediaStore.Audio.Media.TITLE);
        projectionList.add(MediaStore.Audio.Media.ALBUM);
        projectionList.add(MediaStore.Audio.Media.ARTIST);
        projectionList.add(MediaStore.Audio.Media.ALBUM_ID);
        projectionList.add(MediaStore.Audio.Media.ARTIST_ID);
        projectionList.add(MediaStore.Audio.Media.DURATION);
        projectionList.add(MediaStore.Audio.Media.TRACK);
        projectionList.add(MediaStore.Audio.Media.YEAR);
        projectionList.add(MediaStore.Audio.Media.SIZE);
        projectionList.add(MediaStore.Audio.Media.MIME_TYPE);
        projectionList.add(MediaStore.Audio.Media.DISPLAY_NAME);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projectionList.add(MediaStore.Audio.Media.RELATIVE_PATH);
        } else {
            projectionList.add(MediaStore.Audio.Media.DATA);
        }

        String[] projection = projectionList.toArray(new String[0]);

        String selection = MediaStore.Audio.Media.IS_MUSIC + "!=0";
        Cursor cursor = resolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, null, MediaStore.Audio.Media.TITLE + " ASC");
        if (cursor != null) {
            try {
                int idCol = cursor.getColumnIndex(MediaStore.Audio.Media._ID);
                int titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
                int albumCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM);
                int artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST);
                int albumIdCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID);
                int artistIdCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST_ID);
                int durationCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION);
                int trackCol = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK);
                int yearCol = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR);
                int sizeCol = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE);
                int mimeCol = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE);
                int displayCol = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME);
                int relativeCol = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                        ? cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                        : -1;
                int dataCol = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                        ? -1
                        : cursor.getColumnIndex(MediaStore.Audio.Media.DATA);

                if (idCol == -1) {
                    return new LocalLibrary(Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
                }

                while (cursor.moveToNext()) {
                    long mediaId = cursor.getLong(idCol);
                    String title = titleCol != -1 ? cursor.getString(titleCol) : null;
                    String album = albumCol != -1 ? cursor.getString(albumCol) : null;
                    String artist = artistCol != -1 ? cursor.getString(artistCol) : null;
                    long albumId = albumIdCol != -1 ? cursor.getLong(albumIdCol) : 0L;
                    long artistId = artistIdCol != -1 ? cursor.getLong(artistIdCol) : 0L;
                    long durationMs = durationCol != -1 ? cursor.getLong(durationCol) : 0L;
                    int track = trackCol != -1 ? cursor.getInt(trackCol) : 0;
                    int year = yearCol != -1 ? cursor.getInt(yearCol) : 0;
                    long size = sizeCol != -1 ? cursor.getLong(sizeCol) : 0L;
                    String mime = mimeCol != -1 ? cursor.getString(mimeCol) : null;
                    String displayName = displayCol != -1 ? cursor.getString(displayCol) : null;
                    String relativePath = relativeCol != -1 ? cursor.getString(relativeCol) : null;
                    String dataPath = dataCol != -1 ? cursor.getString(dataCol) : null;

                    if (filterEnabled && !matchesLocalSource(relativePath, dataPath, allowedPaths)) {
                        continue;
                    }

                    Child song = createLocalChild(LOCAL_SONG_PREFIX + mediaId);
                    song.setTitle(title);
                    song.setAlbum(album);
                    song.setArtist(artist);
                    song.setAlbumId(LOCAL_ALBUM_PREFIX + albumId);
                    song.setArtistId(LOCAL_ARTIST_PREFIX + artistId);
                    song.setDuration(durationMs > 0 ? (int) (durationMs / 1000) : null);
                    song.setTrack(track);
                    song.setYear(year);
                    song.setSize(size);
                    song.setContentType(mime);
                    song.setSuffix(resolveSuffix(displayName, mime));
                    song.setType(Constants.MEDIA_TYPE_LOCAL);

                    Uri songUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaId);
                    song.setPath(songUri.toString());

                    if (albumId > 0) {
                        Uri artUri = Uri.parse("content://media/external/audio/albumart/" + albumId);
                        song.setCoverArtId(artUri.toString());
                    }

                    songs.add(song);
                    songById.put(mediaId, song);

                    AlbumAccumulator albumAcc = albumMap.get(albumId);
                    if (albumAcc == null) {
                        albumAcc = new AlbumAccumulator(albumId, album, artist, year, artistId);
                        albumMap.put(albumId, albumAcc);
                    }
                    albumAcc.addSong(song);

                    ArtistAccumulator artistAcc = artistMap.get(artistId);
                    if (artistAcc == null) {
                        artistAcc = new ArtistAccumulator(artistId, artist);
                        artistMap.put(artistId, artistAcc);
                    }
                    artistAcc.addSong(song, albumId);
                }
            } finally {
                cursor.close();
            }
        }

        List<AlbumID3> albums = new ArrayList<>();
        for (AlbumAccumulator acc : albumMap.values()) {
            albums.add(acc.toAlbum());
        }

        List<ArtistID3> artists = new ArrayList<>();
        for (ArtistAccumulator acc : artistMap.values()) {
            artists.add(acc.toArtist());
        }

        List<Genre> genres = loadGenres(resolver, songById);

        return new LocalLibrary(songs, albums, artists, genres);
    }

    private static Child createLocalChild(String id) {
        return new Child(
                id,
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private static List<Genre> loadGenres(ContentResolver resolver, Map<Long, Child> songById) {
        List<Genre> genres = new ArrayList<>();
        Cursor genreCursor = resolver.query(MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME},
                null,
                null,
                MediaStore.Audio.Genres.NAME + " ASC");
        if (genreCursor == null) return genres;

        try {
            int idCol = genreCursor.getColumnIndex(MediaStore.Audio.Genres._ID);
            int nameCol = genreCursor.getColumnIndex(MediaStore.Audio.Genres.NAME);
            if (idCol == -1 || nameCol == -1) {
                return genres;
            }
            while (genreCursor.moveToNext()) {
                long genreId = genreCursor.getLong(idCol);
                String name = genreCursor.getString(nameCol);
                if (TextUtils.isEmpty(name)) continue;

                int songCount = 0;
                Set<Long> albums = new HashSet<>();
                Uri membersUri = MediaStore.Audio.Genres.Members.getContentUri("external", genreId);
                Cursor membersCursor = resolver.query(membersUri,
                        new String[]{MediaStore.Audio.Genres.Members.AUDIO_ID, MediaStore.Audio.Genres.Members.ALBUM_ID},
                        null,
                        null,
                        null);
                if (membersCursor != null) {
                    try {
                        int audioIdCol = membersCursor.getColumnIndex(MediaStore.Audio.Genres.Members.AUDIO_ID);
                        int albumCol = membersCursor.getColumnIndex(MediaStore.Audio.Genres.Members.ALBUM_ID);
                        if (audioIdCol == -1 || albumCol == -1) {
                            continue;
                        }
                        while (membersCursor.moveToNext()) {
                            long audioId = membersCursor.getLong(audioIdCol);
                            if (songById != null && !songById.containsKey(audioId)) {
                                continue;
                            }
                            songCount++;
                            albums.add(membersCursor.getLong(albumCol));
                            if (songById != null) {
                                Child song = songById.get(audioId);
                                if (song != null && TextUtils.isEmpty(song.getGenre())) {
                                    song.setGenre(name);
                                }
                            }
                        }
                    } finally {
                        membersCursor.close();
                    }
                }

                Genre genre = new Genre();
                genre.setGenre(name);
                genre.setSongCount(songCount);
                genre.setAlbumCount(albums.size());
                genres.add(genre);
            }
        } finally {
            genreCursor.close();
        }

        return genres;
    }

    private static List<String> normalizeAllowedPaths(List<LocalSource> sources) {
        if (sources == null || sources.isEmpty()) return Collections.emptyList();
        List<String> allowed = new ArrayList<>();
        for (LocalSource source : sources) {
            if (source == null) continue;
            String relative = source.getRelativePath();
            if (TextUtils.isEmpty(relative)) continue;
            String normalized = relative.replace("\\\\", "/");
            if (!normalized.endsWith("/")) {
                normalized = normalized + "/";
            }
            allowed.add(normalized);
        }
        return allowed;
    }

    private static boolean matchesLocalSource(@Nullable String relativePath, @Nullable String dataPath, List<String> allowedPaths) {
        if (allowedPaths == null || allowedPaths.isEmpty()) return true;
        String rel = relativePath != null ? relativePath.replace("\\\\", "/") : null;
        String data = dataPath != null ? dataPath.replace("\\\\", "/") : null;

        for (String allowed : allowedPaths) {
            if (allowed == null || allowed.isEmpty()) continue;
            if (rel != null && rel.startsWith(allowed)) {
                return true;
            }
            if (data != null && data.contains("/" + allowed)) {
                return true;
            }
        }

        return false;
    }

    @Nullable
    private static String resolveSuffix(@Nullable String displayName, @Nullable String mimeType) {
        if (displayName != null && displayName.contains(".")) {
            String ext = displayName.substring(displayName.lastIndexOf('.') + 1);
            return ext.toLowerCase();
        }
        if (mimeType == null) return null;
        if (mimeType.contains("/")) {
            return mimeType.substring(mimeType.indexOf('/') + 1);
        }
        return null;
    }

    public static final class LocalLibrary {
        public final List<Child> songs;
        public final List<AlbumID3> albums;
        public final List<ArtistID3> artists;
        public final List<Genre> genres;

        public LocalLibrary(List<Child> songs, List<AlbumID3> albums, List<ArtistID3> artists, List<Genre> genres) {
            this.songs = songs;
            this.albums = albums;
            this.artists = artists;
            this.genres = genres;
        }
    }

    public static final class LocalSearchResult {
        public final List<ArtistID3> artists;
        public final List<AlbumID3> albums;
        public final List<Child> songs;

        public LocalSearchResult(List<ArtistID3> artists, List<AlbumID3> albums, List<Child> songs) {
            this.artists = artists;
            this.albums = albums;
            this.songs = songs;
        }
    }

    private static final class AlbumAccumulator {
        private final long albumId;
        private final String name;
        private final String artist;
        private final int year;
        private final long artistId;
        private int songCount = 0;
        private long duration = 0;
        private String coverArtId;

        private AlbumAccumulator(long albumId, String name, String artist, int year, long artistId) {
            this.albumId = albumId;
            this.name = name;
            this.artist = artist;
            this.year = year;
            this.artistId = artistId;
        }

        void addSong(Child song) {
            songCount++;
            if (song.getDuration() != null) {
                duration += song.getDuration();
            }
            if (coverArtId == null) {
                coverArtId = song.getCoverArtId();
            }
        }

        AlbumID3 toAlbum() {
            AlbumID3 album = new AlbumID3();
            album.setId(LOCAL_ALBUM_PREFIX + albumId);
            album.setName(name);
            album.setArtist(artist);
            album.setYear(year);
            album.setSongCount(songCount);
            album.setDuration((int) duration);
            album.setCoverArtId(coverArtId);
            album.setArtistId(LOCAL_ARTIST_PREFIX + artistId);
            return album;
        }
    }

    private static final class ArtistAccumulator {
        private final long artistId;
        private final String name;
        private final Set<Long> albums = new HashSet<>();
        private String coverArtId;

        private ArtistAccumulator(long artistId, String name) {
            this.artistId = artistId;
            this.name = name;
        }

        void addSong(Child song, long albumId) {
            albums.add(albumId);
            if (coverArtId == null) {
                coverArtId = song.getCoverArtId();
            }
        }

        ArtistID3 toArtist() {
            ArtistID3 artist = new ArtistID3();
            artist.setId(LOCAL_ARTIST_PREFIX + artistId);
            artist.setName(name);
            artist.setAlbumCount(albums.size());
            artist.setCoverArtId(coverArtId);
            return artist;
        }
    }
}
