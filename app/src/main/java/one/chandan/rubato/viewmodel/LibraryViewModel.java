package one.chandan.rubato.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import one.chandan.rubato.repository.DirectoryRepository;
import one.chandan.rubato.repository.LibraryRepository;
import one.chandan.rubato.repository.LocalSourceRepository;
import one.chandan.rubato.repository.ServerRepository;
import one.chandan.rubato.model.LibrarySourceItem;
import one.chandan.rubato.model.LocalSource;
import one.chandan.rubato.model.Server;
import one.chandan.rubato.subsonic.models.AlbumID3;
import one.chandan.rubato.subsonic.models.ArtistID3;
import one.chandan.rubato.subsonic.models.Genre;
import one.chandan.rubato.subsonic.models.Indexes;
import one.chandan.rubato.subsonic.models.MusicFolder;
import one.chandan.rubato.subsonic.models.Playlist;
import one.chandan.rubato.util.Preferences;
import one.chandan.rubato.util.NetworkUtil;
import one.chandan.rubato.ui.state.LibraryUiState;

import java.util.ArrayList;
import java.util.List;

public class LibraryViewModel extends AndroidViewModel {
    private static final String TAG = "LibraryViewModel";

    private final DirectoryRepository directoryRepository;
    private final LibraryRepository libraryRepository;
    private final LocalSourceRepository localSourceRepository;
    private final ServerRepository serverRepository;

    private final MutableLiveData<List<MusicFolder>> musicFolders = new MutableLiveData<>(null);
    private final MutableLiveData<List<LibrarySourceItem>> librarySources = new MutableLiveData<>(null);
    private final MutableLiveData<Indexes> indexes = new MutableLiveData<>(null);
    private final MutableLiveData<List<Playlist>> playlistSample = new MutableLiveData<>(null);
    private final MutableLiveData<List<AlbumID3>> sampleAlbum = new MutableLiveData<>(null);
    private final MutableLiveData<List<ArtistID3>> sampleArtist = new MutableLiveData<>(null);
    private final MutableLiveData<List<Genre>> sampleGenres = new MutableLiveData<>(null);
    private final MediatorLiveData<LibraryUiState> uiState = new MediatorLiveData<>();
    private boolean uiStateInitialized = false;
    private int libraryScrollY = 0;
    private long lastSyncCompletedAt = 0L;
    private List<MusicFolder> cachedMusicFolders = new ArrayList<>();
    private List<LocalSource> cachedLocalSources = new ArrayList<>();
    private List<Server> cachedServers = new ArrayList<>();

    public LibraryViewModel(@NonNull Application application) {
        super(application);

        directoryRepository = new DirectoryRepository();
        libraryRepository = new LibraryRepository();
        localSourceRepository = new LocalSourceRepository();
        serverRepository = new ServerRepository();
    }

    public LiveData<List<MusicFolder>> getMusicFolders(LifecycleOwner owner) {
        if (musicFolders.getValue() == null) {
            directoryRepository.getMusicFolders().observe(owner, musicFolders::postValue);
        }

        return musicFolders;
    }

    public LiveData<List<LibrarySourceItem>> getLibrarySources(LifecycleOwner owner) {
        if (librarySources.getValue() == null) {
            directoryRepository.getMusicFolders().observe(owner, folders -> {
                cachedMusicFolders = folders != null ? folders : new ArrayList<>();
                rebuildLibrarySources();
            });
            localSourceRepository.getLiveSources().observe(owner, sources -> {
                cachedLocalSources = sources != null ? sources : new ArrayList<>();
                rebuildLibrarySources();
            });
            serverRepository.getLiveServer().observe(owner, servers -> {
                cachedServers = servers != null ? servers : new ArrayList<>();
                rebuildLibrarySources();
            });
        }

        return librarySources;
    }

    public LiveData<Indexes> getIndexes(LifecycleOwner owner) {
        if (indexes.getValue() == null) {
            directoryRepository.getIndexes("0", null).observe(owner, indexes::postValue);
        }

        return indexes;
    }

    public LiveData<List<AlbumID3>> getAlbumSample(LifecycleOwner owner) {
        if (sampleAlbum.getValue() == null) {
            libraryRepository.loadAlbumsLegacy(items ->
                    sampleAlbum.postValue(selectSample(items, 10, true)));
        }

        return sampleAlbum;
    }

    public LiveData<List<ArtistID3>> getArtistSample(LifecycleOwner owner) {
        if (sampleArtist.getValue() == null) {
            libraryRepository.loadArtistsLegacy(items ->
                    sampleArtist.postValue(selectSample(items, 10, true)));
        }

        return sampleArtist;
    }

    public LiveData<List<Genre>> getGenreSample(LifecycleOwner owner) {
        if (sampleGenres.getValue() == null) {
            libraryRepository.loadGenresLegacy(items ->
                    sampleGenres.postValue(selectSample(items, 15, true)));
        }

        return sampleGenres;
    }

    public LiveData<List<Playlist>> getPlaylistSample(LifecycleOwner owner) {
        if (playlistSample.getValue() == null) {
            libraryRepository.loadPlaylistsLegacy(items ->
                    playlistSample.postValue(selectSample(items, 10, true)));
        }

        return playlistSample;
    }

    public LiveData<LibraryUiState> getUiState(LifecycleOwner owner) {
        if (!uiStateInitialized) {
            uiStateInitialized = true;
            getMusicFolders(owner);
            getLibrarySources(owner);
            getAlbumSample(owner);
            getArtistSample(owner);
            getGenreSample(owner);
            getPlaylistSample(owner);

            uiState.addSource(musicFolders, value -> rebuildUiState());
            uiState.addSource(librarySources, value -> rebuildUiState());
            uiState.addSource(sampleAlbum, value -> rebuildUiState());
            uiState.addSource(sampleArtist, value -> rebuildUiState());
            uiState.addSource(sampleGenres, value -> rebuildUiState());
            uiState.addSource(playlistSample, value -> rebuildUiState());
            rebuildUiState();
        }

        return uiState;
    }

    public int getLibraryScrollY() {
        return libraryScrollY;
    }

    public void setLibraryScrollY(int scrollY) {
        libraryScrollY = Math.max(0, scrollY);
    }

    public long getLastSyncCompletedAt() {
        return lastSyncCompletedAt;
    }

    public void setLastSyncCompletedAt(long completedAt) {
        lastSyncCompletedAt = completedAt;
    }

    private void rebuildLibrarySources() {
        List<LibrarySourceItem> items = new ArrayList<>();
        String serverName = resolveCurrentServerName();

        if (cachedMusicFolders != null) {
            for (MusicFolder folder : cachedMusicFolders) {
                if (folder == null) continue;
                items.add(LibrarySourceItem.fromSubsonic(folder, serverName));
            }
        }

        if (cachedLocalSources != null) {
            for (LocalSource source : cachedLocalSources) {
                if (source == null) continue;
                items.add(LibrarySourceItem.fromLocal(source));
            }
        }

        librarySources.postValue(items);
    }

    private String resolveCurrentServerName() {
        String currentServerId = Preferences.getServerId();
        if (currentServerId != null && cachedServers != null) {
            for (Server server : cachedServers) {
                if (server != null && currentServerId.equals(server.getServerId())) {
                    return server.getServerName();
                }
            }
        }
        return "Subsonic";
    }

    public void refreshAlbumSample(LifecycleOwner owner) {
        libraryRepository.loadAlbumsLegacy(items ->
                sampleAlbum.postValue(selectSample(items, 10, true)));
    }

    public void refreshArtistSample(LifecycleOwner owner) {
        libraryRepository.loadArtistsLegacy(items ->
                sampleArtist.postValue(selectSample(items, 10, true)));
    }

    public void refreshGenreSample(LifecycleOwner owner) {
        libraryRepository.loadGenresLegacy(items ->
                sampleGenres.postValue(selectSample(items, 15, true)));
    }

    public void refreshPlaylistSample(LifecycleOwner owner) {
        libraryRepository.loadPlaylistsLegacy(items ->
                playlistSample.postValue(selectSample(items, 10, true)));
    }

    private void rebuildUiState() {
        boolean loading = musicFolders.getValue() == null
                || librarySources.getValue() == null
                || sampleAlbum.getValue() == null
                || sampleArtist.getValue() == null
                || sampleGenres.getValue() == null
                || playlistSample.getValue() == null;

        LibraryUiState state = new LibraryUiState(
                loading,
                NetworkUtil.isOffline(),
                null,
                musicFolders.getValue() != null ? musicFolders.getValue() : new ArrayList<>(),
                librarySources.getValue() != null ? librarySources.getValue() : new ArrayList<>(),
                sampleAlbum.getValue() != null ? sampleAlbum.getValue() : new ArrayList<>(),
                sampleArtist.getValue() != null ? sampleArtist.getValue() : new ArrayList<>(),
                sampleGenres.getValue() != null ? sampleGenres.getValue() : new ArrayList<>(),
                playlistSample.getValue() != null ? playlistSample.getValue() : new ArrayList<>()
        );

        uiState.postValue(state);
    }

    private <T> List<T> selectSample(List<? extends T> items, int limit, boolean shuffle) {
        if (items == null || items.isEmpty()) return new ArrayList<>();
        List<T> copy = new ArrayList<>(items);
        if (shuffle) {
            java.util.Collections.shuffle(copy);
        }
        if (limit > 0 && copy.size() > limit) {
            return new ArrayList<>(copy.subList(0, limit));
        }
        return copy;
    }
}
