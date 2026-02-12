package one.chandan.rubato.model;

import androidx.room.Ignore;

public class ArtistPlayStat {
    public String artistId;
    public String artistName;
    public long lastPlayed;
    public int playCount;

    public ArtistPlayStat() {
    }

    @Ignore
    public ArtistPlayStat(String artistId, String artistName, long lastPlayed, int playCount) {
        this.artistId = artistId;
        this.artistName = artistName;
        this.lastPlayed = lastPlayed;
        this.playCount = playCount;
    }
}
