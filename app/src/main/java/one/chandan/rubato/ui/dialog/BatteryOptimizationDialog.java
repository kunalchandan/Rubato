package one.chandan.rubato.ui.dialog;

import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.fragment.app.DialogFragment;
import androidx.media3.common.util.UnstableApi;

import one.chandan.rubato.R;
import one.chandan.rubato.databinding.DialogBatteryOptimizationBinding;
import one.chandan.rubato.util.Preferences;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

@OptIn(markerClass = UnstableApi.class)
public class BatteryOptimizationDialog extends DialogFragment {

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        DialogBatteryOptimizationBinding bind = DialogBatteryOptimizationBinding.inflate(getLayoutInflater());

        return new MaterialAlertDialogBuilder(requireContext())
                .setView(bind.getRoot())
                .setTitle(R.string.activity_battery_optimizations_title)
                .setPositiveButton(R.string.battery_optimization_positive_button, (dialog, listener) -> openPowerSettings())
                .setNeutralButton(R.string.battery_optimization_neutral_button, (dialog, listener) -> Preferences.dontAskForOptimization())
                .setNegativeButton(R.string.battery_optimization_negative_button, null)
                .create();
    }

    private void openPowerSettings() {
        String packageName = requireContext().getPackageName();

        Intent requestIgnore = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        requestIgnore.setData(Uri.parse("package:" + packageName));
        requestIgnore.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            startActivity(requestIgnore);
            return;
        } catch (Exception ignored) {
        }

        Intent appDetails = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        appDetails.setData(Uri.parse("package:" + packageName));
        appDetails.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            startActivity(appDetails);
            return;
        } catch (Exception ignored) {
        }

        Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }
}
