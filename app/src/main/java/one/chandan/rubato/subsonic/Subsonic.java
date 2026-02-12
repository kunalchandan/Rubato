package one.chandan.rubato.subsonic;

import one.chandan.rubato.subsonic.api.albumsonglist.AlbumSongListClient;
import one.chandan.rubato.subsonic.api.bookmarks.BookmarksClient;
import one.chandan.rubato.subsonic.api.browsing.BrowsingClient;
import one.chandan.rubato.subsonic.api.internetradio.InternetRadioClient;
import one.chandan.rubato.subsonic.api.mediaannotation.MediaAnnotationClient;
import one.chandan.rubato.subsonic.api.medialibraryscanning.MediaLibraryScanningClient;
import one.chandan.rubato.subsonic.api.mediaretrieval.MediaRetrievalClient;
import one.chandan.rubato.subsonic.api.open.OpenClient;
import one.chandan.rubato.subsonic.api.playlist.PlaylistClient;
import one.chandan.rubato.subsonic.api.podcast.PodcastClient;
import one.chandan.rubato.subsonic.api.searching.SearchingClient;
import one.chandan.rubato.subsonic.api.sharing.SharingClient;
import one.chandan.rubato.subsonic.api.system.SystemClient;
import one.chandan.rubato.subsonic.base.Version;

import java.util.HashMap;
import java.util.Map;

public class Subsonic {
    private static final Version API_MAX_VERSION = Version.of("1.15.0");

    private final Version apiVersion = API_MAX_VERSION;
    private final SubsonicPreferences preferences;

    private SystemClient systemClient;
    private BrowsingClient browsingClient;
    private MediaRetrievalClient mediaRetrievalClient;
    private PlaylistClient playlistClient;
    private SearchingClient searchingClient;
    private AlbumSongListClient albumSongListClient;
    private MediaAnnotationClient mediaAnnotationClient;
    private PodcastClient podcastClient;
    private MediaLibraryScanningClient mediaLibraryScanningClient;
    private BookmarksClient bookmarksClient;
    private InternetRadioClient internetRadioClient;
    private SharingClient sharingClient;
    private OpenClient openClient;

    public Subsonic(SubsonicPreferences preferences) {
        this.preferences = preferences;
    }

    public Version getApiVersion() {
        return apiVersion;
    }

    public SystemClient getSystemClient() {
        if (systemClient == null) {
            systemClient = new SystemClient(this);
        }
        return systemClient;
    }

    public BrowsingClient getBrowsingClient() {
        if (browsingClient == null) {
            browsingClient = new BrowsingClient(this);
        }
        return browsingClient;
    }

    public MediaRetrievalClient getMediaRetrievalClient() {
        if (mediaRetrievalClient == null) {
            mediaRetrievalClient = new MediaRetrievalClient(this);
        }
        return mediaRetrievalClient;
    }

    public PlaylistClient getPlaylistClient() {
        if (playlistClient == null) {
            playlistClient = new PlaylistClient(this);
        }
        return playlistClient;
    }

    public SearchingClient getSearchingClient() {
        if (searchingClient == null) {
            searchingClient = new SearchingClient(this);
        }
        return searchingClient;
    }

    public AlbumSongListClient getAlbumSongListClient() {
        if (albumSongListClient == null) {
            albumSongListClient = new AlbumSongListClient(this);
        }
        return albumSongListClient;
    }

    public MediaAnnotationClient getMediaAnnotationClient() {
        if (mediaAnnotationClient == null) {
            mediaAnnotationClient = new MediaAnnotationClient(this);
        }
        return mediaAnnotationClient;
    }

    public PodcastClient getPodcastClient() {
        if (podcastClient == null) {
            podcastClient = new PodcastClient(this);
        }
        return podcastClient;
    }

    public MediaLibraryScanningClient getMediaLibraryScanningClient() {
        if (mediaLibraryScanningClient == null) {
            mediaLibraryScanningClient = new MediaLibraryScanningClient(this);
        }
        return mediaLibraryScanningClient;
    }

    public BookmarksClient getBookmarksClient() {
        if (bookmarksClient == null) {
            bookmarksClient = new BookmarksClient(this);
        }
        return bookmarksClient;
    }

    public InternetRadioClient getInternetRadioClient() {
        if (internetRadioClient == null) {
            internetRadioClient = new InternetRadioClient(this);
        }
        return internetRadioClient;
    }

    public SharingClient getSharingClient() {
        if (sharingClient == null) {
            sharingClient = new SharingClient(this);
        }
        return sharingClient;
    }

    public OpenClient getOpenClient() {
        if (openClient == null) {
            openClient = new OpenClient(this);
        }
        return openClient;
    }

    public String getUrl() {
        String serverUrl = preferences.getServerUrl();
        if (serverUrl == null || serverUrl.trim().isEmpty()) {
            return "http://localhost/rest/";
        }

        String url = serverUrl + "/rest/";
        return url.replace("//rest", "/rest");
    }

    public Map<String, String> getParams() {
        Map<String, String> params = new HashMap<>();
        if (preferences.getUsername() != null) {
            params.put("u", preferences.getUsername());
        }

        if (preferences.getAuthentication() != null) {
            if (preferences.getAuthentication().getPassword() != null)
                params.put("p", preferences.getAuthentication().getPassword());
            if (preferences.getAuthentication().getSalt() != null)
                params.put("s", preferences.getAuthentication().getSalt());
            if (preferences.getAuthentication().getToken() != null)
                params.put("t", preferences.getAuthentication().getToken());
        }

        params.put("v", getApiVersion().getVersionString());
        params.put("c", preferences.getClientName());
        params.put("f", "json");

        return params;
    }
}
