package one.chandan.rubato.jellyfin.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

public class JellyfinItem {
    @SerializedName("Id")
    private String id;

    @SerializedName("Name")
    private String name;

    @SerializedName("Type")
    private String type;

    @SerializedName("Album")
    private String album;

    @SerializedName("AlbumId")
    private String albumId;

    @SerializedName("AlbumArtist")
    private String albumArtist;

    @SerializedName("Artists")
    private List<String> artists;

    @SerializedName("ArtistItems")
    private List<JellyfinItemRef> artistItems;

    @SerializedName("ImageTags")
    private Map<String, String> imageTags;

    @SerializedName("RunTimeTicks")
    private Long runTimeTicks;

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getAlbum() {
        return album;
    }

    public String getAlbumId() {
        return albumId;
    }

    public String getAlbumArtist() {
        return albumArtist;
    }

    public List<String> getArtists() {
        return artists;
    }

    public List<JellyfinItemRef> getArtistItems() {
        return artistItems;
    }

    public Map<String, String> getImageTags() {
        return imageTags;
    }

    public Long getRunTimeTicks() {
        return runTimeTicks;
    }
}
