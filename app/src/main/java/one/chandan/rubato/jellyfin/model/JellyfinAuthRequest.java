package one.chandan.rubato.jellyfin.model;

import com.google.gson.annotations.SerializedName;

public class JellyfinAuthRequest {
    @SerializedName("Username")
    private final String username;

    @SerializedName("Pw")
    private final String password;

    public JellyfinAuthRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
}
