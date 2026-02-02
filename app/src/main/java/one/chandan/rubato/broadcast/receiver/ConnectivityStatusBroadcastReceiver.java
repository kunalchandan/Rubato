package one.chandan.rubato.broadcast.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;

import one.chandan.rubato.ui.activity.MainActivity;
import one.chandan.rubato.util.MetadataSyncManager;

@OptIn(markerClass = UnstableApi.class)
public class ConnectivityStatusBroadcastReceiver extends BroadcastReceiver {
    private final MainActivity activity;

    public ConnectivityStatusBroadcastReceiver(MainActivity activity) {
        this.activity = activity;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
            activity.updateOfflineBanner();
            if (!activity.isFinishing()) {
                MetadataSyncManager.startIfNeeded(activity);
            }
        }
    }
}
