package one.chandan.rubato;

import android.app.UiModeManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Assume;

import android.Manifest;
import android.app.UiAutomation;

public final class TestDeviceUtil {
    private TestDeviceUtil() {}

    public static boolean isAutomotiveDevice() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        UiModeManager uiModeManager = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
        boolean automotiveMode = uiModeManager != null
                && uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_CAR;
        boolean automotiveFeature = context.getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
        return automotiveMode || automotiveFeature;
    }

    public static void assumeNotAutomotive() {
        Assume.assumeFalse(isAutomotiveDevice());
    }

    public static void grantPostNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        if (isAutomotiveDevice()) {
            return;
        }
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uiAutomation.grantRuntimePermission(
                context.getPackageName(),
                Manifest.permission.POST_NOTIFICATIONS
        );
    }
}
