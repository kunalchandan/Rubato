package one.chandan.rubato.model;

import androidx.annotation.Nullable;

import one.chandan.rubato.subsonic.models.MusicFolder;

public class LibrarySourceItem {
    public enum Kind {
        SUBSONIC,
        LOCAL
    }

    private final Kind kind;
    private final String title;
    private final String sourceName;
    @Nullable
    private final MusicFolder musicFolder;
    @Nullable
    private final LocalSource localSource;

    private LibrarySourceItem(Kind kind, String title, String sourceName, @Nullable MusicFolder musicFolder, @Nullable LocalSource localSource) {
        this.kind = kind;
        this.title = title;
        this.sourceName = sourceName;
        this.musicFolder = musicFolder;
        this.localSource = localSource;
    }

    public static LibrarySourceItem fromSubsonic(MusicFolder folder, String sourceName) {
        String title = folder != null && folder.getName() != null ? folder.getName() : "";
        return new LibrarySourceItem(Kind.SUBSONIC, title, sourceName, folder, null);
    }

    public static LibrarySourceItem fromLocal(LocalSource source) {
        String title = source != null && source.getRelativePath() != null && !source.getRelativePath().isEmpty()
                ? source.getRelativePath()
                : (source != null ? source.getDisplayName() : "");
        String sourceName = source != null ? source.getDisplayName() : "";
        return new LibrarySourceItem(Kind.LOCAL, title, sourceName, null, source);
    }

    public Kind getKind() {
        return kind;
    }

    public String getTitle() {
        return title;
    }

    public String getSourceName() {
        return sourceName;
    }

    @Nullable
    public MusicFolder getMusicFolder() {
        return musicFolder;
    }

    @Nullable
    public LocalSource getLocalSource() {
        return localSource;
    }
}
