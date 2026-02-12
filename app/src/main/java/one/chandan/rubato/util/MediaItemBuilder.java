package one.chandan.rubato.util;

import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;

import one.chandan.rubato.App;
import one.chandan.rubato.glide.CustomGlideRequest;
import one.chandan.rubato.model.Download;
import one.chandan.rubato.provider.AutoArtworkProvider;
import one.chandan.rubato.repository.DownloadRepository;
import one.chandan.rubato.repository.LocalMusicRepository;
import one.chandan.rubato.subsonic.models.Child;
import one.chandan.rubato.subsonic.models.InternetRadioStation;
import one.chandan.rubato.subsonic.models.PodcastEpisode;

import java.util.ArrayList;
import java.util.List;

@OptIn(markerClass = UnstableApi.class)
public final class MediaItemBuilder {
    private MediaItemBuilder() {
    }

    public static List<MediaItem> fromChildren(List<Child> items) {
        ArrayList<MediaItem> mediaItems = new ArrayList<>();
        if (items == null) {
            return mediaItems;
        }
        for (int i = 0; i < items.size(); i++) {
            mediaItems.add(fromChild(items.get(i)));
        }
        return mediaItems;
    }

    public static MediaItem fromChild(Child media) {
        Uri uri = resolveUri(media);
        String resolvedCoverArtId = resolveCoverArtId(media);
        Uri artworkUri = resolveArtworkUri(resolvedCoverArtId, LocalMusicRepository.isLocalSong(media));
        String mediaType = media.getType() != null ? media.getType() : Constants.MEDIA_TYPE_MUSIC;

        Bundle bundle = new Bundle();
        bundle.putString("id", media.getId());
        bundle.putString("parentId", media.getParentId());
        bundle.putBoolean("isDir", media.isDir());
        bundle.putString("title", media.getTitle());
        bundle.putString("album", media.getAlbum());
        bundle.putString("artist", media.getArtist());
        bundle.putInt("track", media.getTrack() != null ? media.getTrack() : 0);
        bundle.putInt("year", media.getYear() != null ? media.getYear() : 0);
        bundle.putString("genre", media.getGenre());
        bundle.putString("coverArtId", resolvedCoverArtId);
        bundle.putLong("size", media.getSize() != null ? media.getSize() : 0);
        bundle.putString("contentType", media.getContentType());
        bundle.putString("suffix", media.getSuffix());
        bundle.putString("transcodedContentType", media.getTranscodedContentType());
        bundle.putString("transcodedSuffix", media.getTranscodedSuffix());
        bundle.putInt("duration", media.getDuration() != null ? media.getDuration() : 0);
        bundle.putInt("bitrate", media.getBitrate() != null ? media.getBitrate() : 0);
        bundle.putString("path", media.getPath());
        bundle.putBoolean("isVideo", media.isVideo());
        bundle.putInt("userRating", media.getUserRating() != null ? media.getUserRating() : 0);
        bundle.putDouble("averageRating", media.getAverageRating() != null ? media.getAverageRating() : 0);
        bundle.putLong("playCount", media.getPlayCount() != null ? media.getPlayCount() : 0);
        bundle.putInt("discNumber", media.getDiscNumber() != null ? media.getDiscNumber() : 0);
        bundle.putLong("created", media.getCreated() != null ? media.getCreated().getTime() : 0);
        bundle.putLong("starred", media.getStarred() != null ? media.getStarred().getTime() : 0);
        bundle.putString("albumId", media.getAlbumId());
        bundle.putString("artistId", media.getArtistId());
        bundle.putString("type", mediaType);
        bundle.putLong("bookmarkPosition", media.getBookmarkPosition() != null ? media.getBookmarkPosition() : 0);
        bundle.putInt("originalWidth", media.getOriginalWidth() != null ? media.getOriginalWidth() : 0);
        bundle.putInt("originalHeight", media.getOriginalHeight() != null ? media.getOriginalHeight() : 0);
        bundle.putString("uri", uri.toString());

        return new MediaItem.Builder()
                .setMediaId(media.getId())
                .setMediaMetadata(
                        new MediaMetadata.Builder()
                                .setTitle(media.getTitle())
                                .setTrackNumber(media.getTrack() != null ? media.getTrack() : 0)
                                .setDiscNumber(media.getDiscNumber() != null ? media.getDiscNumber() : 0)
                                .setReleaseYear(media.getYear() != null ? media.getYear() : 0)
                                .setAlbumTitle(media.getAlbum())
                                .setArtist(media.getArtist())
                                .setArtworkUri(artworkUri)
                                .setExtras(bundle)
                                .setIsBrowsable(false)
                                .setIsPlayable(true)
                                .build()
                )
                .setRequestMetadata(
                        new MediaItem.RequestMetadata.Builder()
                                .setMediaUri(uri)
                                .setExtras(bundle)
                                .build()
                )
                .setMimeType(MimeTypes.BASE_TYPE_AUDIO)
                .setUri(uri)
                .build();
    }

    public static List<MediaItem> fromDownloads(List<Child> items) {
        ArrayList<MediaItem> downloads = new ArrayList<>();
        if (items == null) {
            return downloads;
        }
        for (int i = 0; i < items.size(); i++) {
            downloads.add(fromDownload(items.get(i)));
        }
        return downloads;
    }

    public static MediaItem fromDownload(Child media) {
        Uri uri = Preferences.preferTranscodedDownload()
                ? MusicUtil.getTranscodedDownloadUri(media.getId())
                : MusicUtil.getDownloadUri(media.getId());
        return new MediaItem.Builder()
                .setMediaId(media.getId())
                .setMediaMetadata(
                        new MediaMetadata.Builder()
                                .setTitle(media.getTitle())
                                .setTrackNumber(media.getTrack() != null ? media.getTrack() : 0)
                                .setDiscNumber(media.getDiscNumber() != null ? media.getDiscNumber() : 0)
                                .setReleaseYear(media.getYear() != null ? media.getYear() : 0)
                                .setAlbumTitle(media.getAlbum())
                                .setArtist(media.getArtist())
                                .setIsBrowsable(false)
                                .setIsPlayable(true)
                                .build()
                )
                .setRequestMetadata(
                        new MediaItem.RequestMetadata.Builder()
                                .setMediaUri(uri)
                                .build()
                )
                .setMimeType(MimeTypes.BASE_TYPE_AUDIO)
                .setUri(uri)
                .build();
    }

    public static MediaItem fromInternetRadioStation(InternetRadioStation internetRadioStation) {
        Uri uri = Uri.parse(internetRadioStation.getStreamUrl());

        Bundle bundle = new Bundle();
        bundle.putString("id", internetRadioStation.getId());
        bundle.putString("title", internetRadioStation.getName());
        bundle.putString("artist", uri.toString());
        bundle.putString("uri", uri.toString());
        bundle.putString("type", Constants.MEDIA_TYPE_RADIO);

        return new MediaItem.Builder()
                .setMediaId(internetRadioStation.getId())
                .setMediaMetadata(
                        new MediaMetadata.Builder()
                                .setTitle(internetRadioStation.getName())
                                .setArtist(internetRadioStation.getStreamUrl())
                                .setExtras(bundle)
                                .setIsBrowsable(false)
                                .setIsPlayable(true)
                                .build()
                )
                .setRequestMetadata(
                        new MediaItem.RequestMetadata.Builder()
                                .setMediaUri(uri)
                                .setExtras(bundle)
                                .build()
                )
                .setUri(uri)
                .build();
    }

    public static MediaItem fromPodcastEpisode(PodcastEpisode podcastEpisode) {
        Uri uri = resolveUri(podcastEpisode);
        Uri artworkUri = Uri.parse(CustomGlideRequest.createUrl(podcastEpisode.getCoverArtId(), Preferences.getImageSize()));

        Bundle bundle = new Bundle();
        bundle.putString("id", podcastEpisode.getId());
        bundle.putString("parentId", podcastEpisode.getParentId());
        bundle.putBoolean("isDir", podcastEpisode.isDir());
        bundle.putString("title", podcastEpisode.getTitle());
        bundle.putString("album", podcastEpisode.getAlbum());
        bundle.putString("artist", podcastEpisode.getArtist());
        bundle.putInt("year", podcastEpisode.getYear() != null ? podcastEpisode.getYear() : 0);
        bundle.putString("coverArtId", podcastEpisode.getCoverArtId());
        bundle.putLong("size", podcastEpisode.getSize() != null ? podcastEpisode.getSize() : 0);
        bundle.putString("contentType", podcastEpisode.getContentType());
        bundle.putString("suffix", podcastEpisode.getSuffix());
        bundle.putInt("duration", podcastEpisode.getDuration() != null ? podcastEpisode.getDuration() : 0);
        bundle.putInt("bitrate", podcastEpisode.getBitrate() != null ? podcastEpisode.getBitrate() : 0);
        bundle.putBoolean("isVideo", podcastEpisode.isVideo());
        bundle.putLong("created", podcastEpisode.getCreated() != null ? podcastEpisode.getCreated().getTime() : 0);
        bundle.putString("artistId", podcastEpisode.getArtistId());
        bundle.putString("description", podcastEpisode.getDescription());
        bundle.putString("type", Constants.MEDIA_TYPE_PODCAST);
        bundle.putString("uri", uri.toString());

        return new MediaItem.Builder()
                .setMediaId(podcastEpisode.getId())
                .setMediaMetadata(
                        new MediaMetadata.Builder()
                                .setTitle(podcastEpisode.getTitle())
                                .setReleaseYear(podcastEpisode.getYear() != null ? podcastEpisode.getYear() : 0)
                                .setAlbumTitle(podcastEpisode.getAlbum())
                                .setArtist(podcastEpisode.getArtist())
                                .setArtworkUri(artworkUri)
                                .setExtras(bundle)
                                .setIsBrowsable(false)
                                .setIsPlayable(true)
                                .build()
                )
                .setRequestMetadata(
                        new MediaItem.RequestMetadata.Builder()
                                .setMediaUri(uri)
                                .setExtras(bundle)
                                .build()
                )
                .setMimeType(MimeTypes.BASE_TYPE_AUDIO)
                .setUri(uri)
                .build();
    }

    private static Uri resolveUri(Child media) {
        if (LocalMusicRepository.isLocalSong(media)) {
            if (media.getPath() != null) {
                return Uri.parse(media.getPath());
            }
        }
        return DownloadUtil.getDownloadTracker(App.getContext()).isDownloaded(media.getId())
                ? resolveDownloadUri(media.getId())
                : MusicUtil.getStreamUri(media.getId());
    }

    private static Uri resolveUri(PodcastEpisode podcastEpisode) {
        return DownloadUtil.getDownloadTracker(App.getContext()).isDownloaded(podcastEpisode.getStreamId())
                ? resolveDownloadUri(podcastEpisode.getStreamId())
                : MusicUtil.getStreamUri(podcastEpisode.getStreamId());
    }

    private static Uri resolveDownloadUri(String id) {
        Download download = new DownloadRepository().getDownload(id);
        return download != null && !download.getDownloadUri().isEmpty()
                ? Uri.parse(download.getDownloadUri())
                : MusicUtil.getDownloadUri(id);
    }

    private static Uri resolveArtworkUri(String coverArtId, boolean isLocal) {
        if (coverArtId == null) return Uri.EMPTY;
        if (isLocal) {
            Uri local = Uri.parse(coverArtId);
            Uri wrapped = AutoArtworkProvider.buildCoverUri(local, Preferences.getImageSize());
            return wrapped != null ? wrapped : local;
        }
        String url = CustomGlideRequest.createUrl(coverArtId, Preferences.getImageSize());
        if (url == null || url.isEmpty()) return Uri.EMPTY;
        Uri remote = Uri.parse(url);
        Uri wrapped = AutoArtworkProvider.buildCoverUri(remote, Preferences.getImageSize());
        return wrapped != null ? wrapped : remote;
    }

    private static String resolveCoverArtId(Child media) {
        if (media == null) return null;
        String coverArtId = media.getCoverArtId();
        if (coverArtId != null && !coverArtId.isEmpty()) {
            return coverArtId;
        }
        String albumId = media.getAlbumId();
        if (albumId == null || albumId.isEmpty()) {
            return coverArtId;
        }
        if (LocalMusicRepository.isLocalSong(media)) {
            if (albumId.startsWith("content://")
                    || albumId.startsWith("file://")
                    || albumId.startsWith("android.resource://")
                    || SearchIndexUtil.isSourceTagged(albumId, SearchIndexUtil.SOURCE_LOCAL)) {
                return albumId;
            }
            return coverArtId;
        }
        return albumId;
    }
}
