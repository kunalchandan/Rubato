package one.chandan.rubato.jellyfin;

import android.content.Context;
import android.content.SharedPreferences;

import one.chandan.rubato.App;
import one.chandan.rubato.BuildConfig;

import java.util.UUID;

public class JellyfinAuthUtil {
    private static final String PREFS = "jellyfin_prefs";
    private static final String KEY_DEVICE_ID = "device_id";

    private JellyfinAuthUtil() {}

    public static String getDeviceId() {
        SharedPreferences prefs = App.getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String current = prefs.getString(KEY_DEVICE_ID, null);
        if (current != null && !current.trim().isEmpty()) {
            return current;
        }
        String generated = UUID.randomUUID().toString();
        prefs.edit().putString(KEY_DEVICE_ID, generated).apply();
        return generated;
    }

    public static String buildAuthHeader() {
        String deviceId = getDeviceId();
        return "MediaBrowser Client=\"Rubato\", Device=\"Android\", DeviceId=\"" + deviceId + "\", Version=\"" + BuildConfig.VERSION_NAME + "\"";
    }
}
