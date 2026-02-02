package one.chandan.rubato.jellyfin.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class JellyfinItemsResponse {
    @SerializedName("Items")
    private List<JellyfinItem> items;

    @SerializedName("TotalRecordCount")
    private Integer totalRecordCount;

    public List<JellyfinItem> getItems() {
        return items;
    }

    public Integer getTotalRecordCount() {
        return totalRecordCount;
    }
}
