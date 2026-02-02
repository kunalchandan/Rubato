package one.chandan.rubato.jellyfin.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class JellyfinViewsResponse {
    @SerializedName("Items")
    private List<JellyfinView> items;

    public List<JellyfinView> getItems() {
        return items;
    }
}
