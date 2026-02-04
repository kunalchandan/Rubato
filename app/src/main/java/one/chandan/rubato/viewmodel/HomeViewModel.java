package one.chandan.rubato.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import one.chandan.rubato.R;
import one.chandan.rubato.interfaces.StarCallback;
import one.chandan.rubato.model.Chronology;
import one.chandan.rubato.model.Favorite;
import one.chandan.rubato.model.HomeSector;
import one.chandan.rubato.repository.AlbumRepository;
import one.chandan.rubato.repository.ArtistRepository;
import one.chandan.rubato.repository.ChronologyRepository;
import one.chandan.rubato.repository.FavoriteRepository;
import one.chandan.rubato.repository.PlaylistRepository;
import one.chandan.rubato.repository.SharingRepository;
import one.chandan.rubato.repository.SongRepository;
import one.chandan.rubato.repository.LocalMusicRepository;
import one.chandan.rubato.subsonic.models.AlbumID3;
import one.chandan.rubato.subsonic.models.ArtistID3;
import one.chandan.rubato.subsonic.models.Child;
import one.chandan.rubato.subsonic.models.Playlist;
import one.chandan.rubato.subsonic.models.Share;
import one.chandan.rubato.util.Constants;
import one.chandan.rubato.util.OfflinePolicy;
import one.chandan.rubato.util.Preferences;
import one.chandan.rubato.util.ServerConfigUtil;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class HomeViewModel extends AndroidViewModel {
    private static final String TAG = "HomeViewModel";

    private final SongRepository songRepository;
    private final AlbumRepository albumRepository;
    private final ArtistRepository artistRepository;
    private final ChronologyRepository chronologyRepository;
    private final FavoriteRepository favoriteRepository;
    private final PlaylistRepository playlistRepository;
    private final SharingRepository sharingRepository;

    private final MutableLiveData<List<Child>> dicoverSongSample = new MutableLiveData<>(null);
    private final MutableLiveData<List<AlbumID3>> newReleasedAlbum = new MutableLiveData<>(null);
    private final MutableLiveData<List<Child>> starredTracksSample = new MutableLiveData<>(null);
    private final MutableLiveData<List<ArtistID3>> starredArtistsSample = new MutableLiveData<>(null);
    private final MutableLiveData<List<ArtistID3>> bestOfArtists = new MutableLiveData<>(null);
    private final MutableLiveData<List<Child>> starredTracks = new MutableLiveData<>(null);
    private final MutableLiveData<List<AlbumID3>> starredAlbums = new MutableLiveData<>(null);
    private final MutableLiveData<List<ArtistID3>> starredArtists = new MutableLiveData<>(null);
    private final MutableLiveData<List<AlbumID3>> mostPlayedAlbumSample = new MutableLiveData<>(null);
    private final MutableLiveData<List<AlbumID3>> recentlyPlayedAlbumSample = new MutableLiveData<>(null);
    private final MutableLiveData<List<Integer>> years = new MutableLiveData<>(null);
    private final MutableLiveData<List<AlbumID3>> recentlyAddedAlbumSample = new MutableLiveData<>(null);

    private final MutableLiveData<List<Chronology>> thisGridTopSong = new MutableLiveData<>(null);
    private final MutableLiveData<List<Child>> mediaInstantMix = new MutableLiveData<>(null);
    private final MutableLiveData<List<Child>> artistInstantMix = new MutableLiveData<>(null);
    private final MutableLiveData<List<Child>> artistBestOf = new MutableLiveData<>(null);
    private final MutableLiveData<List<Playlist>> pinnedPlaylists = new MutableLiveData<>(null);
    private final MutableLiveData<List<Share>> shares = new MutableLiveData<>(null);

    private List<HomeSector> sectors;

    private LiveData<List<Child>> starredTracksSource;
    private LiveData<List<AlbumID3>> starredAlbumsSource;
    private LiveData<List<ArtistID3>> starredArtistsSource;
    private LiveData<List<Chronology>> chronologySource;
    private final Observer<List<Child>> starredTracksObserver = songs -> updateListPreservingNonEmpty(starredTracks, songs);
    private final Observer<List<AlbumID3>> starredAlbumsObserver = albums -> updateListPreservingNonEmpty(starredAlbums, albums);
    private final Observer<List<ArtistID3>> starredArtistsObserver = artists -> updateListPreservingNonEmpty(starredArtists, artists);
    private final Observer<List<Chronology>> chronologyObserver = thisGridTopSong::postValue;

    public HomeViewModel(@NonNull Application application) {
        super(application);

        setHomeSectorList();

        songRepository = new SongRepository();
        albumRepository = new AlbumRepository();
        artistRepository = new ArtistRepository();
        chronologyRepository = new ChronologyRepository();
        favoriteRepository = new FavoriteRepository();
        playlistRepository = new PlaylistRepository();
        sharingRepository = new SharingRepository();

        setOfflineFavorite();
    }

    private boolean hasRemoteServer() {
        return ServerConfigUtil.hasAnyRemoteServer();
    }

    private void loadLocalSongsSample(int limit, MutableLiveData<List<Child>> target) {
        LocalMusicRepository.loadLibrary(getApplication(), library -> {
            List<Child> songs = new ArrayList<>(library.songs);
            Collections.shuffle(songs);
            if (limit > 0 && songs.size() > limit) {
                songs = new ArrayList<>(songs.subList(0, limit));
            }
            target.postValue(songs);
        });
    }

    private void loadLocalAlbumsSample(int limit, MutableLiveData<List<AlbumID3>> target) {
        LocalMusicRepository.loadLibrary(getApplication(), library -> {
            List<AlbumID3> albums = new ArrayList<>(library.albums);
            if (limit > 0 && albums.size() > limit) {
                albums = new ArrayList<>(albums.subList(0, limit));
            }
            target.postValue(albums);
        });
    }

    private void loadLocalArtistsSample(int limit, MutableLiveData<List<ArtistID3>> target) {
        LocalMusicRepository.loadLibrary(getApplication(), library -> {
            List<ArtistID3> artists = new ArrayList<>(library.artists);
            if (limit > 0 && artists.size() > limit) {
                artists = new ArrayList<>(artists.subList(0, limit));
            }
            target.postValue(artists);
        });
    }

    private <T> MutableLiveData<List<T>> emptyList(MutableLiveData<List<T>> target) {
        if (target != null) {
            List<T> current = target.getValue();
            if (current != null && !current.isEmpty()) {
                return target;
            }
            target.setValue(Collections.emptyList());
            return target;
        }
        return new MutableLiveData<>(Collections.emptyList());
    }

    public LiveData<List<Child>> getDiscoverSongSample(LifecycleOwner owner) {
        if (!hasRemoteServer()) {
            loadLocalSongsSample(10, dicoverSongSample);
            return dicoverSongSample;
        }

        if (dicoverSongSample.getValue() == null) {
            songRepository.getRandomSample(10, null, null).observe(owner, dicoverSongSample::postValue);
        }

        return dicoverSongSample;
    }

    public LiveData<List<Child>> getRandomShuffleSample() {
        if (!hasRemoteServer()) {
            MutableLiveData<List<Child>> local = new MutableLiveData<>(Collections.emptyList());
            loadLocalSongsSample(100, local);
            return local;
        }
        return songRepository.getRandomSample(100, null, null);
    }

    public LiveData<List<Chronology>> getChronologySample(LifecycleOwner owner) {
        if (chronologySource == null) {
            chronologySource = createChronologySource();
            chronologySource.observeForever(chronologyObserver);
        }
        return thisGridTopSong;
    }

    public void refreshChronologySample(LifecycleOwner owner) {
        if (chronologySource != null) {
            chronologySource.removeObserver(chronologyObserver);
        }
        chronologySource = createChronologySource();
        chronologySource.observeForever(chronologyObserver);
    }

    private LiveData<List<Chronology>> createChronologySource() {
        Calendar cal = Calendar.getInstance();
        String server = Preferences.getServerId();

        int currentWeek = cal.get(Calendar.WEEK_OF_YEAR);
        long start = cal.getTimeInMillis();

        cal.set(Calendar.WEEK_OF_YEAR, currentWeek - 1);
        long end = cal.getTimeInMillis();

        return chronologyRepository.getChronology(server, start, end);
    }

    public LiveData<List<AlbumID3>> getRecentlyReleasedAlbums(LifecycleOwner owner) {
        if (!hasRemoteServer()) {
            loadLocalAlbumsSample(20, newReleasedAlbum);
            return newReleasedAlbum;
        }

        if (newReleasedAlbum.getValue() == null) {
            loadRecentlyReleasedWithFallback(owner);
        }

        return newReleasedAlbum;
    }

    public LiveData<List<Child>> getStarredTracksSample(LifecycleOwner owner) {
        if (!hasRemoteServer()) {
            loadLocalSongsSample(10, starredTracksSample);
            return starredTracksSample;
        }

        if (starredTracksSample.getValue() == null) {
            songRepository.getStarredSongs(true, 10).observe(owner, starredTracksSample::postValue);
        }

        return starredTracksSample;
    }

    public LiveData<List<ArtistID3>> getStarredArtistsSample(LifecycleOwner owner) {
        if (!hasRemoteServer()) {
            loadLocalArtistsSample(10, starredArtistsSample);
            return starredArtistsSample;
        }

        if (starredArtistsSample.getValue() == null) {
            artistRepository.getStarredArtists(true, 10).observe(owner, starredArtistsSample::postValue);
        }

        return starredArtistsSample;
    }

    public LiveData<List<ArtistID3>> getBestOfArtists(LifecycleOwner owner) {
        if (!hasRemoteServer()) {
            loadLocalArtistsSample(20, bestOfArtists);
            return bestOfArtists;
        }

        if (bestOfArtists.getValue() == null) {
            artistRepository.getStarredArtists(true, 20).observe(owner, bestOfArtists::postValue);
        }

        return bestOfArtists;
    }

    public LiveData<List<Child>> getStarredTracks(LifecycleOwner owner) {
        if (!hasRemoteServer()) {
            return emptyList(starredTracks);
        }

        if (starredTracksSource == null) {
            starredTracksSource = songRepository.getStarredSongs(true, 20);
            starredTracksSource.observeForever(starredTracksObserver);
        }

        return starredTracks;
    }

    public LiveData<List<AlbumID3>> getStarredAlbums(LifecycleOwner owner) {
        if (!hasRemoteServer()) {
            return emptyList(starredAlbums);
        }

        if (starredAlbumsSource == null) {
            starredAlbumsSource = albumRepository.getStarredAlbums(true, 20);
            starredAlbumsSource.observeForever(starredAlbumsObserver);
        }

        return starredAlbums;
    }

    public LiveData<List<ArtistID3>> getStarredArtists(LifecycleOwner owner) {
        if (!hasRemoteServer()) {
            return emptyList(starredArtists);
        }

        if (starredArtistsSource == null) {
            starredArtistsSource = artistRepository.getStarredArtists(true, 20);
            starredArtistsSource.observeForever(starredArtistsObserver);
        }

        return starredArtists;
    }

    public LiveData<List<Integer>> getYearList(LifecycleOwner owner) {
        if (years.getValue() == null) {
            albumRepository.getDecades().observe(owner, years::postValue);
        }

        return years;
    }

    public LiveData<List<AlbumID3>> getMostPlayedAlbums(LifecycleOwner owner) {
        if (!hasRemoteServer()) {
            loadLocalAlbumsSample(20, mostPlayedAlbumSample);
            return mostPlayedAlbumSample;
        }

        if (mostPlayedAlbumSample.getValue() == null) {
            albumRepository.getAlbums("frequent", 20, null, null).observe(owner, mostPlayedAlbumSample::postValue);
        }

        return mostPlayedAlbumSample;
    }

    public LiveData<List<AlbumID3>> getMostRecentlyAddedAlbums(LifecycleOwner owner) {
        if (!hasRemoteServer()) {
            loadLocalAlbumsSample(20, recentlyAddedAlbumSample);
            return recentlyAddedAlbumSample;
        }

        if (recentlyAddedAlbumSample.getValue() == null) {
            albumRepository.getAlbums("newest", 20, null, null).observe(owner, recentlyAddedAlbumSample::postValue);
        }

        return recentlyAddedAlbumSample;
    }

    public LiveData<List<AlbumID3>> getRecentlyPlayedAlbumList(LifecycleOwner owner) {
        if (!hasRemoteServer()) {
            loadLocalAlbumsSample(20, recentlyPlayedAlbumSample);
            return recentlyPlayedAlbumSample;
        }

        if (recentlyPlayedAlbumSample.getValue() == null) {
            albumRepository.getAlbums("recent", 20, null, null).observe(owner, recentlyPlayedAlbumSample::postValue);
        }

        return recentlyPlayedAlbumSample;
    }

    public LiveData<List<Child>> getMediaInstantMix(LifecycleOwner owner, Child media) {
        mediaInstantMix.setValue(Collections.emptyList());

        if (!hasRemoteServer()) {
            return mediaInstantMix;
        }

        songRepository.getInstantMix(media.getId(), 20).observe(owner, mediaInstantMix::postValue);

        return mediaInstantMix;
    }

    public LiveData<List<Child>> getArtistInstantMix(LifecycleOwner owner, ArtistID3 artist) {
        artistInstantMix.setValue(Collections.emptyList());

        if (!hasRemoteServer()) {
            if (artist != null) {
                LocalMusicRepository.getLocalArtistSongsByName(getApplication(), artist.getName(), songs -> {
                    artistInstantMix.postValue(songs != null ? songs : Collections.emptyList());
                });
            }
            return artistInstantMix;
        }

        artistRepository.getTopSongs(artist.getName(), 10).observe(owner, artistInstantMix::postValue);

        return artistInstantMix;
    }

    public LiveData<List<Child>> getArtistBestOf(LifecycleOwner owner, ArtistID3 artist) {
        artistBestOf.setValue(Collections.emptyList());

        if (!hasRemoteServer()) {
            if (artist != null) {
                LocalMusicRepository.getLocalArtistSongsByName(getApplication(), artist.getName(), songs -> {
                    artistBestOf.postValue(songs != null ? songs : Collections.emptyList());
                });
            }
            return artistBestOf;
        }

        artistRepository.getTopSongs(artist.getName(), 10).observe(owner, artistBestOf::postValue);

        return artistBestOf;
    }

    public LiveData<List<Playlist>> getPinnedPlaylists(LifecycleOwner owner) {
        pinnedPlaylists.setValue(Collections.emptyList());

        if (!hasRemoteServer()) {
            playlistRepository.getPinnedPlaylists().observe(owner, locals -> {
                pinnedPlaylists.setValue(locals != null ? locals : Collections.emptyList());
            });
            return pinnedPlaylists;
        }

        playlistRepository.getPlaylists(false, -1).observe(owner, remotes -> {
            playlistRepository.getPinnedPlaylists().observe(owner, locals -> {
                if (locals == null || locals.isEmpty()) {
                    pinnedPlaylists.setValue(remotes != null ? remotes : Collections.emptyList());
                    return;
                }

                if (remotes == null || remotes.isEmpty()) {
                    pinnedPlaylists.setValue(locals);
                    return;
                }

                List<Playlist> toReturn = remotes.stream()
                        .filter(remote -> locals.stream().anyMatch(local -> local.getId().equals(remote.getId())))
                        .collect(Collectors.toList());

                pinnedPlaylists.setValue(!toReturn.isEmpty() ? toReturn : locals);
            });
        });

        return pinnedPlaylists;
    }

    public LiveData<List<Share>> getShares(LifecycleOwner owner) {
        if (!hasRemoteServer()) {
            return emptyList(shares);
        }

        if (shares.getValue() == null) {
            sharingRepository.getShares().observe(owner, shares::postValue);
        }

        return shares;
    }

    public LiveData<List<Child>> getAllStarredTracks() {
        if (!hasRemoteServer()) {
            return new MutableLiveData<>(Collections.emptyList());
        }
        return songRepository.getStarredSongs(false, -1);
    }

    public void changeChronologyPeriod(LifecycleOwner owner, int period) {
        Calendar cal = Calendar.getInstance();
        String server = Preferences.getServerId();
        int currentWeek = cal.get(Calendar.WEEK_OF_YEAR);

        long start = 0;
        long end = 0;

        if (period == 0) {
            start = cal.getTimeInMillis();
            cal.set(Calendar.WEEK_OF_YEAR, currentWeek - 1);
            end = cal.getTimeInMillis();
        } else if (period == 1) {
            start = cal.getTimeInMillis();
            cal.set(Calendar.WEEK_OF_YEAR, currentWeek - 4);
            end = cal.getTimeInMillis();
        } else if (period == 2) {
            start = cal.getTimeInMillis();
            cal.set(Calendar.WEEK_OF_YEAR, currentWeek - 52);
            end = cal.getTimeInMillis();
        }

        chronologyRepository.getChronology(server, start, end).observe(owner, thisGridTopSong::postValue);
    }

    public void refreshDiscoverySongSample(LifecycleOwner owner) {
        if (!hasRemoteServer()) {
            loadLocalSongsSample(10, dicoverSongSample);
            return;
        }
        songRepository.getRandomSample(10, null, null).observe(owner, dicoverSongSample::postValue);
    }

    public void refreshSimilarSongSample(LifecycleOwner owner) {
        if (!hasRemoteServer()) {
            loadLocalSongsSample(10, starredTracksSample);
            return;
        }
        songRepository.getStarredSongs(true, 10).observe(owner, starredTracksSample::postValue);
    }

    public void refreshRadioArtistSample(LifecycleOwner owner) {
        if (!hasRemoteServer()) {
            loadLocalArtistsSample(10, starredArtistsSample);
            return;
        }
        artistRepository.getStarredArtists(true, 10).observe(owner, starredArtistsSample::postValue);
    }

    public void refreshBestOfArtist(LifecycleOwner owner) {
        if (!hasRemoteServer()) {
            loadLocalArtistsSample(20, bestOfArtists);
            return;
        }
        artistRepository.getStarredArtists(true, 20).observe(owner, bestOfArtists::postValue);
    }

    public void refreshStarredTracks(LifecycleOwner owner) {
        if (!hasRemoteServer()) {
            updateListPreservingNonEmpty(starredTracks, Collections.emptyList());
            return;
        }
        if (starredTracksSource != null) {
            starredTracksSource.removeObserver(starredTracksObserver);
        }
        starredTracksSource = songRepository.getStarredSongs(true, 20);
        starredTracksSource.observeForever(starredTracksObserver);
    }

    public void refreshStarredAlbums(LifecycleOwner owner) {
        if (!hasRemoteServer()) {
            updateListPreservingNonEmpty(starredAlbums, Collections.emptyList());
            return;
        }
        if (starredAlbumsSource != null) {
            starredAlbumsSource.removeObserver(starredAlbumsObserver);
        }
        starredAlbumsSource = albumRepository.getStarredAlbums(true, 20);
        starredAlbumsSource.observeForever(starredAlbumsObserver);
    }

    public void refreshStarredArtists(LifecycleOwner owner) {
        if (!hasRemoteServer()) {
            updateListPreservingNonEmpty(starredArtists, Collections.emptyList());
            return;
        }
        if (starredArtistsSource != null) {
            starredArtistsSource.removeObserver(starredArtistsObserver);
        }
        starredArtistsSource = artistRepository.getStarredArtists(true, 20);
        starredArtistsSource.observeForever(starredArtistsObserver);
    }

    private <T> void updateListPreservingNonEmpty(MutableLiveData<List<T>> target, List<T> incoming) {
        if (target == null) return;
        if (incoming == null) {
            if (target.getValue() == null) {
                target.postValue(null);
            }
            return;
        }
        if (incoming.isEmpty()) {
            List<T> current = target.getValue();
            if (current != null && !current.isEmpty()) {
                return;
            }
        }
        target.postValue(new ArrayList<>(incoming));
    }

    public void refreshMostPlayedAlbums(LifecycleOwner owner) {
        if (!hasRemoteServer()) {
            loadLocalAlbumsSample(20, mostPlayedAlbumSample);
            return;
        }
        albumRepository.getAlbums("frequent", 20, null, null).observe(owner, mostPlayedAlbumSample::postValue);
    }

    public void refreshMostRecentlyAddedAlbums(LifecycleOwner owner) {
        if (!hasRemoteServer()) {
            loadLocalAlbumsSample(20, recentlyAddedAlbumSample);
            return;
        }
        albumRepository.getAlbums("newest", 20, null, null).observe(owner, recentlyAddedAlbumSample::postValue);
    }

    public void refreshRecentlyReleasedAlbums(LifecycleOwner owner) {
        if (!hasRemoteServer()) {
            loadLocalAlbumsSample(20, newReleasedAlbum);
            return;
        }
        loadRecentlyReleasedWithFallback(owner);
    }

    private void loadRecentlyReleasedWithFallback(LifecycleOwner owner) {
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        albumRepository.getAlbums("byYear", 500, currentYear, currentYear).observe(owner, albums -> {
            if (albums == null) {
                return;
            }
            if (!hasNonLocalAlbums(albums)) {
                albumRepository.getAlbums("newest", 200, null, null).observe(owner, fallback -> {
                    if (fallback == null) return;
                    updateNewReleasesFromList(fallback);
                });
                return;
            }
            updateNewReleasesFromList(albums);
        });
    }

    private void updateNewReleasesFromList(List<AlbumID3> albums) {
        if (albums == null) return;
        List<AlbumID3> sorted = new ArrayList<>(albums);
        sortByRecentCreated(sorted);
        List<AlbumID3> selection = new ArrayList<>(sorted.subList(0, Math.min(20, sorted.size())));
        updateListPreservingNonEmpty(newReleasedAlbum, selection);
    }

    private boolean hasNonLocalAlbums(List<AlbumID3> albums) {
        if (albums == null || albums.isEmpty()) return false;
        for (AlbumID3 album : albums) {
            if (album == null) continue;
            String id = album.getId();
            if (id != null && !LocalMusicRepository.isLocalAlbumId(id)) {
                return true;
            }
        }
        return false;
    }

    private void sortByRecentCreated(List<AlbumID3> albums) {
        if (albums == null || albums.isEmpty()) return;
        albums.removeIf(item -> item == null);
        albums.sort((left, right) -> {
            if (left == null && right == null) return 0;
            if (left == null) return 1;
            if (right == null) return -1;
            java.util.Date leftDate = left.getCreated();
            java.util.Date rightDate = right.getCreated();
            if (leftDate == null && rightDate == null) {
                return Integer.compare(right.getYear(), left.getYear());
            }
            if (leftDate == null) return 1;
            if (rightDate == null) return -1;
            return rightDate.compareTo(leftDate);
        });
    }

    public void refreshRecentlyPlayedAlbumList(LifecycleOwner owner) {
        if (!hasRemoteServer()) {
            loadLocalAlbumsSample(20, recentlyPlayedAlbumSample);
            return;
        }
        albumRepository.getAlbums("recent", 20, null, null).observe(owner, recentlyPlayedAlbumSample::postValue);
    }

    public void refreshShares(LifecycleOwner owner) {
        if (!hasRemoteServer()) {
            updateListPreservingNonEmpty(shares, Collections.emptyList());
            return;
        }
        sharingRepository.getShares().observe(owner, this.shares::postValue);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (starredTracksSource != null) {
            starredTracksSource.removeObserver(starredTracksObserver);
        }
        if (starredAlbumsSource != null) {
            starredAlbumsSource.removeObserver(starredAlbumsObserver);
        }
        if (starredArtistsSource != null) {
            starredArtistsSource.removeObserver(starredArtistsObserver);
        }
        if (chronologySource != null) {
            chronologySource.removeObserver(chronologyObserver);
        }
    }

    private void setHomeSectorList() {
        if (Preferences.getHomeSectorList() != null && !Preferences.getHomeSectorList().equals("null")) {
            List<HomeSector> saved = new Gson().fromJson(
                    Preferences.getHomeSectorList(),
                    new TypeToken<List<HomeSector>>() {
                    }.getType()
            );
            sectors = mergeWithDefaults(saved);
        } else {
            sectors = fillStandardHomeSectorList();
        }
    }

    public List<HomeSector> getHomeSectorList() {
        return sectors;
    }

    public boolean checkHomeSectorVisibility(String sectorId) {
        return sectors != null && sectors.stream().filter(sector -> sector.getId().equals(sectorId))
                .findAny()
                .orElse(null) == null;
    }

    public void setOfflineFavorite() {
        if (!hasRemoteServer() || OfflinePolicy.isOffline()) {
            return;
        }

        ArrayList<Favorite> favorites = getFavorites();
        ArrayList<Favorite> favoritesToSave = getFavoritesToSave(favorites);
        ArrayList<Favorite> favoritesToDelete = getFavoritesToDelete(favorites, favoritesToSave);

        manageFavoriteToSave(favoritesToSave);
        manageFavoriteToDelete(favoritesToDelete);
    }

    private ArrayList<Favorite> getFavorites() {
        return new ArrayList<>(favoriteRepository.getFavorites());
    }

    private ArrayList<Favorite> getFavoritesToSave(ArrayList<Favorite> favorites) {
        HashMap<String, Favorite> filteredMap = new HashMap<>();

        for (Favorite favorite : favorites) {
            String key = favorite.toString();

            if (!filteredMap.containsKey(key) || favorite.getTimestamp() > filteredMap.get(key).getTimestamp()) {
                filteredMap.put(key, favorite);
            }
        }

        return new ArrayList<>(filteredMap.values());
    }

    private ArrayList<Favorite> getFavoritesToDelete(ArrayList<Favorite> favorites, ArrayList<Favorite> favoritesToSave) {
        ArrayList<Favorite> favoritesToDelete = new ArrayList<>();

        for (Favorite favorite : favorites) {
            if (!favoritesToSave.contains(favorite)) {
                favoritesToDelete.add(favorite);
            }
        }

        return favoritesToDelete;
    }

    private void manageFavoriteToSave(ArrayList<Favorite> favoritesToSave) {
        for (Favorite favorite : favoritesToSave) {
            if (favorite.getToStar()) {
                favoriteToStar(favorite);
            } else {
                favoriteToUnstar(favorite);
            }
        }
    }

    private void manageFavoriteToDelete(ArrayList<Favorite> favoritesToDelete) {
        for (Favorite favorite : favoritesToDelete) {
            favoriteRepository.delete(favorite);
        }
    }

    private void favoriteToStar(Favorite favorite) {
        if (favorite.getSongId() != null) {
            favoriteRepository.star(favorite.getSongId(), null, null, new StarCallback() {
                @Override
                public void onSuccess() {
                    favoriteRepository.delete(favorite);
                }
            });
        } else if (favorite.getAlbumId() != null) {
            favoriteRepository.star(null, favorite.getAlbumId(), null, new StarCallback() {
                @Override
                public void onSuccess() {
                    favoriteRepository.delete(favorite);
                }
            });
        } else if (favorite.getArtistId() != null) {
            favoriteRepository.star(null, null, favorite.getArtistId(), new StarCallback() {
                @Override
                public void onSuccess() {
                    favoriteRepository.delete(favorite);
                }
            });
        }
    }

    private void favoriteToUnstar(Favorite favorite) {
        if (favorite.getSongId() != null) {
            favoriteRepository.unstar(favorite.getSongId(), null, null, new StarCallback() {
                @Override
                public void onSuccess() {
                    favoriteRepository.delete(favorite);
                }
            });
        } else if (favorite.getAlbumId() != null) {
            favoriteRepository.unstar(null, favorite.getAlbumId(), null, new StarCallback() {
                @Override
                public void onSuccess() {
                    favoriteRepository.delete(favorite);
                }
            });
        } else if (favorite.getArtistId() != null) {
            favoriteRepository.unstar(null, null, favorite.getArtistId(), new StarCallback() {
                @Override
                public void onSuccess() {
                    favoriteRepository.delete(favorite);
                }
            });
        }
    }

    private List<HomeSector> fillStandardHomeSectorList() {
        List<HomeSector> sectors = new ArrayList<>();

        sectors.add(new HomeSector(Constants.HOME_SECTOR_DISCOVERY, getApplication().getString(R.string.home_title_discovery), true, 1));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_MADE_FOR_YOU, getApplication().getString(R.string.home_title_made_for_you), true, 2));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_BEST_OF, getApplication().getString(R.string.home_title_best_of), true, 3));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_RADIO_STATION, getApplication().getString(R.string.home_title_radio_station), true, 4));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_TOP_SONGS, getApplication().getString(R.string.home_title_top_songs), true, 5));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_STARRED_TRACKS, getApplication().getString(R.string.home_title_starred_tracks), true, 6));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_STARRED_ALBUMS, getApplication().getString(R.string.home_title_starred_albums), true, 7));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_STARRED_ARTISTS, getApplication().getString(R.string.home_title_starred_artists), true, 8));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_NEW_RELEASES, getApplication().getString(R.string.home_title_new_releases), true, 9));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_FLASHBACK, getApplication().getString(R.string.home_title_flashback), true, 10));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_MOST_PLAYED, getApplication().getString(R.string.home_title_most_played), true, 11));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_LAST_PLAYED, getApplication().getString(R.string.home_title_last_played), true, 12));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_RECENTLY_ADDED, getApplication().getString(R.string.home_title_recently_added), true, 13));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_PINNED_PLAYLISTS, getApplication().getString(R.string.home_title_pinned_playlists), true, 14));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_SHARED, getApplication().getString(R.string.home_title_shares), true, 15));

        return sectors;
    }

    private List<HomeSector> mergeWithDefaults(List<HomeSector> saved) {
        if (saved == null || saved.isEmpty()) {
            return fillStandardHomeSectorList();
        }

        List<HomeSector> merged = new ArrayList<>(saved);
        List<HomeSector> defaults = fillStandardHomeSectorList();

        for (HomeSector sector : defaults) {
            boolean exists = false;
            for (HomeSector savedSector : saved) {
                if (savedSector.getId().equals(sector.getId())) {
                    exists = true;
                    break;
                }
            }

            if (!exists) {
                merged.add(sector);
            }
        }

        return merged;
    }
}
