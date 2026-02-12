package one.chandan.rubato.repository;


import android.net.Uri;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.LibraryResult;

import one.chandan.rubato.App;
import one.chandan.rubato.database.AppDatabase;
import one.chandan.rubato.database.dao.ChronologyDao;
import one.chandan.rubato.database.dao.SessionMediaItemDao;
import one.chandan.rubato.glide.CustomGlideRequest;
import one.chandan.rubato.model.Chronology;
import one.chandan.rubato.model.Download;
import one.chandan.rubato.model.SessionMediaItem;
import one.chandan.rubato.service.DownloaderManager;
import one.chandan.rubato.subsonic.base.ApiResponse;
import one.chandan.rubato.subsonic.models.AlbumID3;
import one.chandan.rubato.subsonic.models.Artist;
import one.chandan.rubato.subsonic.models.ArtistID3;
import one.chandan.rubato.subsonic.models.Child;
import one.chandan.rubato.subsonic.models.Directory;
import one.chandan.rubato.subsonic.models.Index;
import one.chandan.rubato.subsonic.models.InternetRadioStation;
import one.chandan.rubato.subsonic.models.MusicFolder;
import one.chandan.rubato.subsonic.models.Playlist;
import one.chandan.rubato.subsonic.models.PodcastEpisode;
import one.chandan.rubato.util.AppExecutors;
import one.chandan.rubato.util.DownloadUtil;
import one.chandan.rubato.util.MappingUtil;
import one.chandan.rubato.util.MusicUtil;
import one.chandan.rubato.util.Preferences;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AutomotiveRepository {
    private final SessionMediaItemDao sessionMediaItemDao = AppDatabase.getInstance().sessionMediaItemDao();
    private final ChronologyDao chronologyDao = AppDatabase.getInstance().chronologyDao();

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getAlbums(String prefix, String type, int size) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getAlbumSongListClient()
                .getAlbumList2(type, size, 0, null, null)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getAlbumList2() != null && response.body().getSubsonicResponse().getAlbumList2().getAlbums() != null) {
                            List<AlbumID3> albums = response.body().getSubsonicResponse().getAlbumList2().getAlbums();

                            List<MediaItem> mediaItems = new ArrayList<>();

                            for (AlbumID3 album : albums) {
                                Uri artworkUri = Uri.parse(CustomGlideRequest.createUrl(album.getCoverArtId(), Preferences.getImageSize()));

                                MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                                        .setTitle(album.getName())
                                        .setAlbumTitle(album.getName())
                                        .setArtist(album.getArtist())
                                        .setGenre(album.getGenre())
                                        .setIsBrowsable(true)
                                        .setIsPlayable(false)
                                        .setMediaType(MediaMetadata.MEDIA_TYPE_ALBUM)
                                        .setArtworkUri(artworkUri)
                                        .build();

                                MediaItem mediaItem = new MediaItem.Builder()
                                        .setMediaId(prefix + album.getId())
                                        .setMediaMetadata(mediaMetadata)
                                        .setUri("")
                                        .build();

                                mediaItems.add(mediaItem);
                            }

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            listenableFuture.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        listenableFuture.setException(t);
                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getStarredSongs() {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getAlbumSongListClient()
                .getStarred2()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getStarred2() != null && response.body().getSubsonicResponse().getStarred2().getSongs() != null) {
                            List<Child> songs = response.body().getSubsonicResponse().getStarred2().getSongs();

                            setChildrenMetadata(songs);

                            List<MediaItem> mediaItems = MappingUtil.mapMediaItems(songs);

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            listenableFuture.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        listenableFuture.setException(t);
                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getRandomSongs(int count) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getAlbumSongListClient()
                .getRandomSongs(100, null, null)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getRandomSongs() != null && response.body().getSubsonicResponse().getRandomSongs().getSongs() != null) {
                            List<Child> songs = response.body().getSubsonicResponse().getRandomSongs().getSongs();

                            setChildrenMetadata(songs);

                            List<MediaItem> mediaItems = MappingUtil.mapMediaItems(songs);

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            listenableFuture.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        listenableFuture.setException(t);
                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getRecentlyPlayedSongs(String server, int count) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        chronologyDao.getLastPlayed(server, count).observeForever(new Observer<List<Chronology>>() {
            @Override
            public void onChanged(List<Chronology> chronology) {
                if (chronology != null && !chronology.isEmpty()) {
                    List<Child> songs = new ArrayList<>(chronology);

                    setChildrenMetadata(songs);

                    List<MediaItem> mediaItems = MappingUtil.mapMediaItems(songs);

                    LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                    listenableFuture.set(libraryResult);
                } else {
                    listenableFuture.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE));
                }

                chronologyDao.getLastPlayed(server, count).removeObserver(this);
            }
        });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getStarredAlbums(String prefix) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getAlbumSongListClient()
                .getStarred2()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getStarred2() != null && response.body().getSubsonicResponse().getStarred2().getAlbums() != null) {
                            List<AlbumID3> albums = response.body().getSubsonicResponse().getStarred2().getAlbums();

                            List<MediaItem> mediaItems = new ArrayList<>();

                            for (AlbumID3 album : albums) {
                                Uri artworkUri = Uri.parse(CustomGlideRequest.createUrl(album.getCoverArtId(), Preferences.getImageSize()));

                                MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                                        .setTitle(album.getName())
                                        .setArtist(album.getArtist())
                                        .setGenre(album.getGenre())
                                        .setIsBrowsable(true)
                                        .setIsPlayable(false)
                                        .setMediaType(MediaMetadata.MEDIA_TYPE_ALBUM)
                                        .setArtworkUri(artworkUri)
                                        .build();

                                MediaItem mediaItem = new MediaItem.Builder()
                                        .setMediaId(prefix + album.getId())
                                        .setMediaMetadata(mediaMetadata)
                                        .setUri("")
                                        .build();

                                mediaItems.add(mediaItem);
                            }

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            listenableFuture.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {

                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getStarredArtists(String prefix) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getAlbumSongListClient()
                .getStarred2()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getStarred2() != null && response.body().getSubsonicResponse().getStarred2().getArtists() != null) {
                            List<ArtistID3> artists = response.body().getSubsonicResponse().getStarred2().getArtists();

                            Collections.shuffle(artists);

                            List<MediaItem> mediaItems = new ArrayList<>();

                            for (ArtistID3 artist : artists) {
                                Uri artworkUri = Uri.parse(CustomGlideRequest.createUrl(artist.getCoverArtId(), Preferences.getImageSize()));

                                MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                                        .setTitle(artist.getName())
                                        .setIsBrowsable(true)
                                        .setIsPlayable(false)
                                        .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                                        .setArtworkUri(artworkUri)
                                        .build();

                                MediaItem mediaItem = new MediaItem.Builder()
                                        .setMediaId(prefix + artist.getId())
                                        .setMediaMetadata(mediaMetadata)
                                        .setUri("")
                                        .build();

                                mediaItems.add(mediaItem);
                            }

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            listenableFuture.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        listenableFuture.setException(t);
                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getMusicFolders(String prefix) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getMusicFolders()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getMusicFolders() != null && response.body().getSubsonicResponse().getMusicFolders().getMusicFolders() != null) {
                            List<MusicFolder> musicFolders = response.body().getSubsonicResponse().getMusicFolders().getMusicFolders();

                            List<MediaItem> mediaItems = new ArrayList<>();

                            for (MusicFolder musicFolder : musicFolders) {
                                MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                                        .setTitle(musicFolder.getName())
                                        .setIsBrowsable(true)
                                        .setIsPlayable(false)
                                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                                        .build();

                                MediaItem mediaItem = new MediaItem.Builder()
                                        .setMediaId(prefix + musicFolder.getId())
                                        .setMediaMetadata(mediaMetadata)
                                        .setUri("")
                                        .build();

                                mediaItems.add(mediaItem);
                            }

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            listenableFuture.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        listenableFuture.setException(t);
                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getIndexes(String prefix, String id) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getIndexes(id, null)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getIndexes() != null) {
                            List<MediaItem> mediaItems = new ArrayList<>();

                            if (response.body().getSubsonicResponse().getIndexes().getIndices() != null) {
                                List<Index> indices = response.body().getSubsonicResponse().getIndexes().getIndices();

                                for (Index index : indices) {
                                    if (index.getArtists() != null) {
                                        for (Artist artist : index.getArtists()) {
                                            MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                                                    .setTitle(artist.getName())
                                                    .setIsBrowsable(true)
                                                    .setIsPlayable(false)
                                                    .setMediaType(MediaMetadata.MEDIA_TYPE_ARTIST)
                                                    .build();

                                            MediaItem mediaItem = new MediaItem.Builder()
                                                    .setMediaId(prefix + artist.getId())
                                                    .setMediaMetadata(mediaMetadata)
                                                    .setUri("")
                                                    .build();

                                            mediaItems.add(mediaItem);
                                        }
                                    }
                                }
                            }

                            if (response.body().getSubsonicResponse().getIndexes().getChildren() != null) {
                                List<Child> children = response.body().getSubsonicResponse().getIndexes().getChildren();

                                for (Child song : children) {
                                    Uri artworkUri = Uri.parse(CustomGlideRequest.createUrl(song.getCoverArtId(), Preferences.getImageSize()));

                                    MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                                            .setTitle(song.getTitle())
                                            .setAlbumTitle(song.getAlbum())
                                            .setArtist(song.getArtist())
                                            .setIsBrowsable(false)
                                            .setIsPlayable(true)
                                            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                                            .setArtworkUri(artworkUri)
                                            .build();

                                    MediaItem mediaItem = new MediaItem.Builder()
                                            .setMediaId(prefix + song.getId())
                                            .setMediaMetadata(mediaMetadata)
                                            .setUri(MusicUtil.getStreamUri(song.getId()))
                                            .build();

                                    mediaItems.add(mediaItem);
                                }

                                setChildrenMetadata(children);
                            }

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        listenableFuture.setException(t);
                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getDirectories(String prefix, String id) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getMusicDirectory(id)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getDirectory() != null && response.body().getSubsonicResponse().getDirectory().getChildren() != null) {
                            Directory directory = response.body().getSubsonicResponse().getDirectory();

                            List<MediaItem> mediaItems = new ArrayList<>();

                            for (Child child : directory.getChildren()) {
                                Uri artworkUri = Uri.parse(CustomGlideRequest.createUrl(child.getCoverArtId(), Preferences.getImageSize()));

                                MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                                        .setTitle(child.getTitle())
                                        .setIsBrowsable(child.isDir())
                                        .setIsPlayable(!child.isDir())
                                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                                        .setArtworkUri(artworkUri)
                                        .build();

                                MediaItem mediaItem = new MediaItem.Builder()
                                        .setMediaId(child.isDir() ? prefix + child.getId() : child.getId())
                                        .setMediaMetadata(mediaMetadata)
                                        .setUri(!child.isDir() ? MusicUtil.getStreamUri(child.getId()) : Uri.parse(""))
                                        .build();

                                mediaItems.add(mediaItem);
                            }

                            setChildrenMetadata(directory.getChildren().stream().filter(child -> !child.isDir()).collect(Collectors.toList()));

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        listenableFuture.setException(t);
                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getPlaylists(String prefix) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getPlaylistClient()
                .getPlaylists()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getPlaylists() != null && response.body().getSubsonicResponse().getPlaylists().getPlaylists() != null) {
                            List<Playlist> playlists = response.body().getSubsonicResponse().getPlaylists().getPlaylists();

                            List<MediaItem> mediaItems = new ArrayList<>();

                            for (Playlist playlist : playlists) {
                                MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                                        .setTitle(playlist.getName())
                                        .setIsBrowsable(true)
                                        .setIsPlayable(false)
                                        .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                                        .build();

                                MediaItem mediaItem = new MediaItem.Builder()
                                        .setMediaId(prefix + playlist.getId())
                                        .setMediaMetadata(mediaMetadata)
                                        .setUri("")
                                        .build();

                                mediaItems.add(mediaItem);
                            }

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            listenableFuture.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        listenableFuture.setException(t);
                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getNewestPodcastEpisodes(int count) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getPodcastClient()
                .getNewestPodcasts(count)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getNewestPodcasts() != null && response.body().getSubsonicResponse().getNewestPodcasts().getEpisodes() != null) {
                            List<PodcastEpisode> episodes = response.body().getSubsonicResponse().getNewestPodcasts().getEpisodes();

                            List<MediaItem> mediaItems = new ArrayList<>();

                            for (PodcastEpisode episode : episodes) {
                                Uri artworkUri = Uri.parse(CustomGlideRequest.createUrl(episode.getCoverArtId(), Preferences.getImageSize()));

                                MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                                        .setTitle(episode.getTitle())
                                        .setIsBrowsable(false)
                                        .setIsPlayable(true)
                                        .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
                                        .setArtworkUri(artworkUri)
                                        .build();

                                MediaItem mediaItem = new MediaItem.Builder()
                                        .setMediaId(episode.getId())
                                        .setMediaMetadata(mediaMetadata)
                                        .setUri(MusicUtil.getStreamUri(episode.getStreamId()))
                                        .build();

                                mediaItems.add(mediaItem);
                            }

                            setPodcastEpisodesMetadata(episodes);

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            listenableFuture.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        listenableFuture.setException(t);
                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getInternetRadioStations() {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getInternetRadioClient()
                .getInternetRadioStations()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getInternetRadioStations() != null && response.body().getSubsonicResponse().getInternetRadioStations().getInternetRadioStations() != null) {

                            List<InternetRadioStation> radioStations = response.body().getSubsonicResponse().getInternetRadioStations().getInternetRadioStations();

                            List<MediaItem> mediaItems = new ArrayList<>();

                            for (InternetRadioStation radioStation : radioStations) {
                                MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                                        .setTitle(radioStation.getName())
                                        .setIsBrowsable(false)
                                        .setIsPlayable(true)
                                        .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
                                        .build();

                                MediaItem mediaItem = new MediaItem.Builder()
                                        .setMediaId(radioStation.getId())
                                        .setMediaMetadata(mediaMetadata)
                                        .setUri(radioStation.getStreamUrl())
                                        .build();

                                mediaItems.add(mediaItem);
                            }

                            setInternetRadioStationsMetadata(radioStations);

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            listenableFuture.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        listenableFuture.setException(t);
                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getAlbumTracks(String id) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getAlbum(id)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getAlbum() != null && response.body().getSubsonicResponse().getAlbum().getSongs() != null) {
                            List<Child> tracks = response.body().getSubsonicResponse().getAlbum().getSongs();

                            setChildrenMetadata(tracks);

                            List<MediaItem> mediaItems = MappingUtil.mapMediaItems(tracks);

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            listenableFuture.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        listenableFuture.setException(t);
                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getArtistAlbum(String prefix, String id) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getArtist(id)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getArtist() != null && response.body().getSubsonicResponse().getArtist().getAlbums() != null) {
                            List<AlbumID3> albums = response.body().getSubsonicResponse().getArtist().getAlbums();

                            List<MediaItem> mediaItems = new ArrayList<>();

                            for (AlbumID3 album : albums) {
                                Uri artworkUri = Uri.parse(CustomGlideRequest.createUrl(album.getCoverArtId(), Preferences.getImageSize()));

                                MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                                        .setTitle(album.getName())
                                        .setAlbumTitle(album.getName())
                                        .setArtist(album.getArtist())
                                        .setGenre(album.getGenre())
                                        .setIsBrowsable(true)
                                        .setIsPlayable(false)
                                        .setMediaType(MediaMetadata.MEDIA_TYPE_ALBUM)
                                        .setArtworkUri(artworkUri)
                                        .build();

                                MediaItem mediaItem = new MediaItem.Builder()
                                        .setMediaId(prefix + album.getId())
                                        .setMediaMetadata(mediaMetadata)
                                        .setUri("")
                                        .build();

                                mediaItems.add(mediaItem);
                            }

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        listenableFuture.setException(t);
                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getPlaylistSongs(String id) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getPlaylistClient()
                .getPlaylist(id)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getPlaylist() != null && response.body().getSubsonicResponse().getPlaylist().getEntries() != null) {
                            List<Child> tracks = response.body().getSubsonicResponse().getPlaylist().getEntries();

                            setChildrenMetadata(tracks);

                            List<MediaItem> mediaItems = MappingUtil.mapMediaItems(tracks);

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        listenableFuture.setException(t);
                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getMadeForYou(String id, int count) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getSimilarSongs2(id, count)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getSimilarSongs2() != null && response.body().getSubsonicResponse().getSimilarSongs2().getSongs() != null) {
                            List<Child> tracks = response.body().getSubsonicResponse().getSimilarSongs2().getSongs();

                            setChildrenMetadata(tracks);

                            List<MediaItem> mediaItems = MappingUtil.mapMediaItems(tracks);

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        listenableFuture.setException(t);
                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> search(String query, String albumPrefix, String artistPrefix) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getSearchingClient()
                .search3(query, 20, 20, 20)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getSearchResult3() != null) {
                            List<MediaItem> mediaItems = new ArrayList<>();

                            if (response.body().getSubsonicResponse().getSearchResult3().getArtists() != null) {
                                for (ArtistID3 artist : response.body().getSubsonicResponse().getSearchResult3().getArtists()) {
                                    Uri artworkUri = Uri.parse(CustomGlideRequest.createUrl(artist.getCoverArtId(), Preferences.getImageSize()));

                                    MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                                            .setTitle(artist.getName())
                                            .setIsBrowsable(true)
                                            .setIsPlayable(false)
                                            .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                                            .setArtworkUri(artworkUri)
                                            .build();

                                    MediaItem mediaItem = new MediaItem.Builder()
                                            .setMediaId(artistPrefix + artist.getId())
                                            .setMediaMetadata(mediaMetadata)
                                            .setUri("")
                                            .build();

                                    mediaItems.add(mediaItem);
                                }
                            }

                            if (response.body().getSubsonicResponse().getSearchResult3().getAlbums() != null) {
                                for (AlbumID3 album : response.body().getSubsonicResponse().getSearchResult3().getAlbums()) {
                                    Uri artworkUri = Uri.parse(CustomGlideRequest.createUrl(album.getCoverArtId(), Preferences.getImageSize()));

                                    MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                                            .setTitle(album.getName())
                                            .setAlbumTitle(album.getName())
                                            .setArtist(album.getArtist())
                                            .setGenre(album.getGenre())
                                            .setIsBrowsable(true)
                                            .setIsPlayable(false)
                                            .setMediaType(MediaMetadata.MEDIA_TYPE_ALBUM)
                                            .setArtworkUri(artworkUri)
                                            .build();

                                    MediaItem mediaItem = new MediaItem.Builder()
                                            .setMediaId(albumPrefix + album.getId())
                                            .setMediaMetadata(mediaMetadata)
                                            .setUri("")
                                            .build();

                                    mediaItems.add(mediaItem);
                                }
                            }

                            if (response.body().getSubsonicResponse().getSearchResult3().getSongs() != null) {
                                List<Child> tracks = response.body().getSubsonicResponse().getSearchResult3().getSongs();
                                setChildrenMetadata(tracks);
                                mediaItems.addAll(MappingUtil.mapMediaItems(tracks));
                            }

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            listenableFuture.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        listenableFuture.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_IO));
                    }
                });

        return listenableFuture;
    }

    @OptIn(markerClass = UnstableApi.class)
    public void setChildrenMetadata(List<Child> children) {
        long timestamp = System.currentTimeMillis();
        ArrayList<SessionMediaItem> sessionMediaItems = new ArrayList<>();

        for (Child child : children) {
            SessionMediaItem sessionMediaItem = new SessionMediaItem(child);
            sessionMediaItem.setTimestamp(timestamp);
            sessionMediaItems.add(sessionMediaItem);
        }

        AppExecutors.io().execute(() -> sessionMediaItemDao.insertAll(sessionMediaItems));
    }

    @OptIn(markerClass = UnstableApi.class)
    public void setPodcastEpisodesMetadata(List<PodcastEpisode> podcastEpisodes) {
        long timestamp = System.currentTimeMillis();
        ArrayList<SessionMediaItem> sessionMediaItems = new ArrayList<>();

        for (PodcastEpisode podcastEpisode : podcastEpisodes) {
            SessionMediaItem sessionMediaItem = new SessionMediaItem(podcastEpisode);
            sessionMediaItem.setTimestamp(timestamp);
            sessionMediaItems.add(sessionMediaItem);
        }

        AppExecutors.io().execute(() -> sessionMediaItemDao.insertAll(sessionMediaItems));
    }

    @OptIn(markerClass = UnstableApi.class)
    public void setInternetRadioStationsMetadata(List<InternetRadioStation> internetRadioStations) {
        long timestamp = System.currentTimeMillis();
        ArrayList<SessionMediaItem> sessionMediaItems = new ArrayList<>();

        for (InternetRadioStation internetRadioStation : internetRadioStations) {
            SessionMediaItem sessionMediaItem = new SessionMediaItem(internetRadioStation);
            sessionMediaItem.setTimestamp(timestamp);
            sessionMediaItems.add(sessionMediaItem);
        }

        AppExecutors.io().execute(() -> sessionMediaItemDao.insertAll(sessionMediaItems));
    }

    public SessionMediaItem getSessionMediaItem(String id) {
        Future<SessionMediaItem> future = AppExecutors.io().submit(() -> sessionMediaItemDao.get(id));
        try {
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    public List<MediaItem> getMetadatas(long timestamp) {
        Future<List<MediaItem>> future = AppExecutors.io().submit(() -> {
            List<SessionMediaItem> sessionMediaItems = sessionMediaItemDao.get(timestamp);
            List<MediaItem> mediaItems = new ArrayList<>();
            for (SessionMediaItem sessionMediaItem : sessionMediaItems) {
                mediaItems.add(sessionMediaItem.getMediaItem());
            }
            return mediaItems;
        });
        try {
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            return Collections.emptyList();
        }
    }

    public void deleteMetadata() {
        AppExecutors.io().execute(sessionMediaItemDao::deleteAll);
    }
}
