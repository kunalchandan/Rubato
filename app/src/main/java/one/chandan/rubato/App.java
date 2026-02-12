package one.chandan.rubato;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.StrictMode;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import one.chandan.rubato.github.Github;
import one.chandan.rubato.helper.ThemeHelper;
import one.chandan.rubato.subsonic.Subsonic;
import one.chandan.rubato.subsonic.SubsonicPreferences;
import one.chandan.rubato.util.PerformanceMarkers;
import one.chandan.rubato.util.Preferences;

public class App extends Application {
    private static App instance;
    private static Context context;
    private static Subsonic subsonic;
    private static Github github;
    private static SharedPreferences preferences;

    @Override
    public void onCreate() {
        super.onCreate();
        PerformanceMarkers.start("app_start");

        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build());
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .build());
        }

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String themePref = sharedPreferences.getString(Preferences.THEME, ThemeHelper.DEFAULT_MODE);
        ThemeHelper.applyTheme(themePref);

        instance = this;
        context = getApplicationContext();
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public static App getInstance() {
        return instance;
    }

    public static Context getContext() {
        return context;
    }

    public static Subsonic getSubsonicClientInstance(boolean override) {
        if (subsonic == null || override) {
            subsonic = getSubsonicClient();
        }
        return subsonic;
    }

    public static Github getGithubClientInstance() {
        if (github == null) {
            github = new Github();
        }
        return github;
    }

    public SharedPreferences getPreferences() {
        return preferences;
    }

    public static void refreshSubsonicClient() {
        subsonic = getSubsonicClient();
    }

    private static Subsonic getSubsonicClient() {
        SubsonicPreferences preferences = getSubsonicPreferences();

        if (preferences.getAuthentication() != null) {
            if (preferences.getAuthentication().getPassword() != null)
                Preferences.setPassword(preferences.getAuthentication().getPassword());
            if (preferences.getAuthentication().getToken() != null)
                Preferences.setToken(preferences.getAuthentication().getToken());
            if (preferences.getAuthentication().getSalt() != null)
                Preferences.setSalt(preferences.getAuthentication().getSalt());
        }

        return new Subsonic(preferences);
    }

    @NonNull
    private static SubsonicPreferences getSubsonicPreferences() {
        String server = Preferences.getInUseServerAddress();
        String username = Preferences.getUser();
        String password = Preferences.getPassword();
        String token = Preferences.getToken();
        String salt = Preferences.getSalt();
        boolean isLowSecurity = Preferences.isLowScurity();

        SubsonicPreferences preferences = new SubsonicPreferences();
        preferences.setServerUrl(server);
        preferences.setUsername(username);
        preferences.setAuthentication(password, token, salt, isLowSecurity);

        return preferences;
    }
}
