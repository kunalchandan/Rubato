package one.chandan.rubato.jellyfin.model;

import com.google.gson.annotations.SerializedName;

public class JellyfinItemRef {
    @SerializedName("Id")
    private String id;

    @SerializedName("Name")
    private String name;

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
