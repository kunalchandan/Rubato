package one.chandan.rubato.github;

import one.chandan.rubato.github.api.release.ReleaseClient;

public class Github {
    private static final String OWNER = "kunalchandan";
    private static final String REPO = "Rubato";
    private ReleaseClient releaseClient;

    public ReleaseClient getReleaseClient() {
        if (releaseClient == null) {
            releaseClient = new ReleaseClient(this);
        }

        return releaseClient;
    }

    public String getUrl() {
        return "https://api.github.com/";
    }

    public static String getOwner() {
        return OWNER;
    }

    public static String getRepo() {
        return REPO;
    }
}
