package one.chandan.rubato.jellyfin.model;

import com.google.gson.annotations.SerializedName;

public class JellyfinView {
    @SerializedName("Id")
    private String id;

    @SerializedName("Name")
    private String name;

    @SerializedName("CollectionType")
    private String collectionType;

    public JellyfinView() {
    }

    public JellyfinView(String id, String name, String collectionType) {
        this.id = id;
        this.name = name;
        this.collectionType = collectionType;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCollectionType() {
        return collectionType;
    }
}
