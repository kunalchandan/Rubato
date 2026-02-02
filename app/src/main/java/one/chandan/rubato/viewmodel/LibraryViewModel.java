package one.chandan.rubato.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import one.chandan.rubato.repository.AlbumRepository;
import one.chandan.rubato.repository.ArtistRepository;
import one.chandan.rubato.repository.DirectoryRepository;
import one.chandan.rubato.repository.GenreRepository;
import one.chandan.rubato.repository.LocalSourceRepository;
import one.chandan.rubato.repository.PlaylistRepository;
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

import java.util.ArrayList;
import java.util.List;

public class LibraryViewModel extends AndroidViewModel {
    private static final String TAG = "LibraryViewModel";

    private final DirectoryRepository directoryRepository;
    private final AlbumRepository albumRepository;
    private final ArtistRepository artistRepository;
    private final GenreRepository genreRepository;
    private final PlaylistRepository playlistRepository;
    private final LocalSourceRepository localSourceRepository;
    private final ServerRepository serverRepository;

    private final MutableLiveData<List<MusicFolder>> musicFolders = new MutableLiveData<>(null);
    private final MutableLiveData<List<LibrarySourceItem>> librarySources = new MutableLiveData<>(null);
    private final MutableLiveData<Indexes> indexes = new MutableLiveData<>(null);
    private final MutableLiveData<List<Playlist>> playlistSample = new MutableLiveData<>(null);
    private final MutableLiveData<List<AlbumID3>> sampleAlbum = new MutableLiveData<>(null);
    private final MutableLiveData<List<ArtistID3>> sampleArtist = new MutableLiveData<>(null);
    private final MutableLiveData<List<Genre>> sampleGenres = new MutableLiveData<>(null);
    private List<MusicFolder> cachedMusicFolders = new ArrayList<>();
    private List<LocalSource> cachedLocalSources = new ArrayList<>();
    private List<Server> cachedServers = new ArrayList<>();

    public LibraryViewModel(@NonNull Application application) {
        super(application);

        directoryRepository = new DirectoryRepository();
        albumRepository = new AlbumRepository();
        artistRepository = new ArtistRepository();
        genreRepository = new GenreRepository();
        playlistRepository = new PlaylistRepository();
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
            albumRepository.getAlbums("random", 10, null, null).observe(owner, sampleAlbum::postValue);
        }

        return sampleAlbum;
    }

    public LiveData<List<ArtistID3>> getArtistSample(LifecycleOwner owner) {
        if (sampleArtist.getValue() == null) {
            artistRepository.getArtists(true, 10).observe(owner, sampleArtist::postValue);
        }

        return sampleArtist;
    }

    public LiveData<List<Genre>> getGenreSample(LifecycleOwner owner) {
        if (sampleGenres.getValue() == null) {
            genreRepository.getGenres(true, 15).observe(owner, sampleGenres::postValue);
        }

        return sampleGenres;
    }

    public LiveData<List<Playlist>> getPlaylistSample(LifecycleOwner owner) {
        if (playlistSample.getValue() == null) {
            playlistRepository.getPlaylists(true, 10).observe(owner, playlistSample::postValue);
        }

        return playlistSample;
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
        albumRepository.getAlbums("random", 10, null, null).observe(owner, sampleAlbum::postValue);
    }

    public void refreshArtistSample(LifecycleOwner owner) {
        artistRepository.getArtists(true, 10).observe(owner, sampleArtist::postValue);
    }

    public void refreshGenreSample(LifecycleOwner owner) {
        genreRepository.getGenres(true, 15).observe(owner, sampleGenres::postValue);
    }

    public void refreshPlaylistSample(LifecycleOwner owner) {
        playlistRepository.getPlaylists(true, 10).observe(owner, playlistSample::postValue);
    }
}
