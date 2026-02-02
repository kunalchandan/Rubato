package one.chandan.rubato.jellyfin.model;

import com.google.gson.annotations.SerializedName;

public class JellyfinAuthResponse {
    @SerializedName("AccessToken")
    private String accessToken;

    @SerializedName("User")
    private JellyfinUser user;

    public String getAccessToken() {
        return accessToken;
    }

    public JellyfinUser getUser() {
        return user;
    }
}
