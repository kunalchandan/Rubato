package one.chandan.rubato.helper;

import android.graphics.Bitmap;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.DynamicColorsOptions;

import one.chandan.rubato.R;
import one.chandan.rubato.util.Preferences;

public class ThemeHelper {
    private static final String TAG = "ThemeHelper";

    public static final String LIGHT_MODE = "light";
    public static final String DARK_MODE = "dark";
    public static final String DEFAULT_MODE = "default";
    public static final String AMOLED_MODE = "amoled";
    public static final String MATERIAL_YOU = "material_you";
    public static final String CUSTOM = "custom";
    public static final String OCEAN = "ocean";
    public static final String FOREST = "forest";
    public static final String SUNSET = "sunset";
    public static final String ROSE = "rose";
    public static final String SLATE = "slate";
    public static final String CITRUS = "citrus";
    public static final String SAGE = "sage";
    public static final String LAGOON = "lagoon";
    public static final String DUNE = "dune";
    public static final String MIDNIGHT = "midnight";
    public static final String EMBER = "ember";
    public static final String OBSIDIAN = "obsidian";
    public static final String AURORA = "aurora";

    public static void applyTheme(@NonNull String themePref) {
        switch (themePref) {
            case LIGHT_MODE: {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            }
            case OCEAN:
            case FOREST:
            case SUNSET:
            case ROSE:
            case SLATE:
            case CITRUS:
            case SAGE:
            case LAGOON:
            case DUNE: {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            }
            case DARK_MODE: {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            }
            case AMOLED_MODE: {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            }
            case MIDNIGHT:
            case EMBER:
            case OBSIDIAN:
            case AURORA: {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            }
            default: {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                } else {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY);
                }
                break;
            }
        }
    }

    public static int getThemeResId(@NonNull String themePref) {
        switch (themePref) {
            case OCEAN:
                return R.style.AppTheme_Ocean;
            case FOREST:
                return R.style.AppTheme_Forest;
            case SUNSET:
                return R.style.AppTheme_Sunset;
            case ROSE:
                return R.style.AppTheme_Rose;
            case SLATE:
                return R.style.AppTheme_Slate;
            case CITRUS:
                return R.style.AppTheme_Citrus;
            case SAGE:
                return R.style.AppTheme_Sage;
            case LAGOON:
                return R.style.AppTheme_Lagoon;
            case DUNE:
                return R.style.AppTheme_Dune;
            case MIDNIGHT:
                return R.style.AppTheme_Midnight;
            case EMBER:
                return R.style.AppTheme_Ember;
            case OBSIDIAN:
                return R.style.AppTheme_Obsidian;
            case AURORA:
                return R.style.AppTheme_Aurora;
            case AMOLED_MODE:
                return R.style.AppTheme_Amoled;
            default:
                return R.style.AppTheme;
        }
    }

    public static boolean isMaterialYou(@NonNull String themePref) {
        return MATERIAL_YOU.equals(themePref);
    }

    public static void applyDynamicColorsIfAvailable(android.app.Activity activity, @NonNull String themePref) {
        if (activity == null) return;
        if (MATERIAL_YOU.equals(themePref)) {
            DynamicColors.applyToActivityIfAvailable(activity);
            return;
        }
        if (!CUSTOM.equals(themePref)) return;

        int primary = Preferences.getCustomThemePrimary();
        int secondary = Preferences.getCustomThemeSecondary();
        int tertiary = Preferences.getCustomThemeTertiary();

        Bitmap bitmap = Bitmap.createBitmap(3, 1, Bitmap.Config.ARGB_8888);
        bitmap.setPixel(0, 0, primary);
        bitmap.setPixel(1, 0, secondary);
        bitmap.setPixel(2, 0, tertiary);

        DynamicColorsOptions options = new DynamicColorsOptions.Builder()
                .setContentBasedSource(bitmap)
                .build();
        DynamicColors.applyToActivityIfAvailable(activity, options);
    }
}
