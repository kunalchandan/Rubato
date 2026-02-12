package one.chandan.rubato.util;

import androidx.media3.common.MediaItem;
import one.chandan.rubato.subsonic.models.Child;
import one.chandan.rubato.subsonic.models.InternetRadioStation;
import one.chandan.rubato.subsonic.models.PodcastEpisode;

import java.util.List;

public class MappingUtil {
    public static List<MediaItem> mapMediaItems(List<Child> items) {
        return MediaItemBuilder.fromChildren(items);
    }

    public static MediaItem mapMediaItem(Child media) {
        return MediaItemBuilder.fromChild(media);
    }

    public static List<MediaItem> mapDownloads(List<Child> items) {
        return MediaItemBuilder.fromDownloads(items);
    }

    public static MediaItem mapDownload(Child media) {
        return MediaItemBuilder.fromDownload(media);
    }

    public static MediaItem mapInternetRadioStation(InternetRadioStation internetRadioStation) {
        return MediaItemBuilder.fromInternetRadioStation(internetRadioStation);
    }

    public static MediaItem mapMediaItem(PodcastEpisode podcastEpisode) {
        return MediaItemBuilder.fromPodcastEpisode(podcastEpisode);
    }
}
