package one.chandan.rubato.model;

public class SearchSuggestion {
    public enum Kind {
        ARTIST,
        ALBUM,
        SONG,
        UNKNOWN
    }

    private final String title;
    private final String coverArtId;
    private final Kind kind;

    public SearchSuggestion(String title, String coverArtId, Kind kind) {
        this.title = title;
        this.coverArtId = coverArtId;
        this.kind = kind != null ? kind : Kind.UNKNOWN;
    }

    public String getTitle() {
        return title;
    }

    public String getCoverArtId() {
        return coverArtId;
    }

    public Kind getKind() {
        return kind;
    }

    public int getPriority() {
        switch (kind) {
            case ARTIST:
                return 3;
            case ALBUM:
                return 2;
            case SONG:
                return 1;
            case UNKNOWN:
            default:
                return 0;
        }
    }
}
