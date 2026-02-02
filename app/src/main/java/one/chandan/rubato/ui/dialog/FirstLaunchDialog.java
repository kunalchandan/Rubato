package one.chandan.rubato.ui.dialog;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import one.chandan.rubato.databinding.DialogFirstLaunchBinding;
import one.chandan.rubato.helper.ThemeHelper;
import one.chandan.rubato.util.Preferences;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class FirstLaunchDialog extends DialogFragment {
    public static final String TAG = "FirstLaunchDialog";

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        DialogFirstLaunchBinding bind = DialogFirstLaunchBinding.inflate(getLayoutInflater());

        bind.onboardingBatteryButton.setOnClickListener(v -> openPowerSettings());
        bind.onboardingThemeButton.setOnClickListener(v -> {
            ThemeSelectorDialog dialog = ThemeSelectorDialog.newInstance(Preferences.getTheme());
            dialog.show(getParentFragmentManager(), "ThemeSelectorDialog");
        });
        bind.onboardingServerButton.setOnClickListener(v -> {
            Preferences.setCoachmarkAddServerPending(true);
            dismiss();
        });
        bind.onboardingDoneButton.setOnClickListener(v -> dismiss());

        return new MaterialAlertDialogBuilder(requireContext())
                .setView(bind.getRoot())
                .create();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getParentFragmentManager().setFragmentResultListener(
                ThemeSelectorDialog.RESULT_KEY,
                this,
                (requestKey, result) -> {
                    String themeOption = result.getString(ThemeSelectorDialog.RESULT_THEME, ThemeHelper.DEFAULT_MODE);
                    Preferences.setTheme(themeOption);
                    ThemeHelper.applyTheme(themeOption);
                    if (getActivity() != null) {
                        getActivity().recreate();
                    }
                }
        );
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        Preferences.setOnboardingShown(true);
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
