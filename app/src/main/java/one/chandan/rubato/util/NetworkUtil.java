package one.chandan.rubato.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;

import one.chandan.rubato.App;
import one.chandan.rubato.util.ServerConfigUtil;
import one.chandan.rubato.util.ServerStatus;

public class NetworkUtil {
    public static boolean hasInternet() {
        ConnectivityManager connectivityManager = (ConnectivityManager) App.getContext().getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager != null) {
            Network network = connectivityManager.getActiveNetwork();

            if (network != null) {
                NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);

                if (capabilities != null) {
                    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
                }
            }
        }

        return false;
    }

    public static boolean isOffline() {
        return !hasInternet();
    }
}
