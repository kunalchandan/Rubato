package one.chandan.rubato.ui.dialog;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.FragmentResultOwner;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.List;

import one.chandan.rubato.R;
import one.chandan.rubato.helper.ThemeHelper;
import one.chandan.rubato.ui.adapter.ThemeOptionAdapter;
import one.chandan.rubato.ui.model.ThemeOption;
import one.chandan.rubato.util.Preferences;

public class ThemeSelectorDialog extends BottomSheetDialogFragment {
    public static final String RESULT_KEY = "theme_selector_result";
    public static final String RESULT_THEME = "theme_selector_theme";
    private static final String ARG_SELECTED = "theme_selector_selected";

    private ThemeOptionAdapter systemAdapter;
    private ThemeOptionAdapter lightAdapter;
    private ThemeOptionAdapter darkAdapter;

    private MaterialCardView customCard;
    private ImageView customCheck;
    private View customPrimary;
    private View customSecondary;
    private View customTertiary;

    private String selectedTheme = ThemeHelper.DEFAULT_MODE;

    public static ThemeSelectorDialog newInstance(String selectedTheme) {
        ThemeSelectorDialog dialog = new ThemeSelectorDialog();
        Bundle args = new Bundle();
        args.putString(ARG_SELECTED, selectedTheme);
        dialog.setArguments(args);
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_theme_selector, container, false);

        selectedTheme = getArguments() != null
                ? getArguments().getString(ARG_SELECTED, ThemeHelper.DEFAULT_MODE)
                : ThemeHelper.DEFAULT_MODE;

        setupRecycler(view, R.id.theme_selector_system_recycler, buildSystemOptions());
        setupRecycler(view, R.id.theme_selector_light_recycler, buildLightOptions());
        setupRecycler(view, R.id.theme_selector_dark_recycler, buildDarkOptions());

        customCard = view.findViewById(R.id.theme_custom_card);
        customCheck = view.findViewById(R.id.theme_custom_check);
        customPrimary = view.findViewById(R.id.theme_custom_primary_swatch);
        customSecondary = view.findViewById(R.id.theme_custom_secondary_swatch);
        customTertiary = view.findViewById(R.id.theme_custom_tertiary_swatch);

        updateCustomSwatches();
        updateCustomSelection();

        getParentFragmentManager().setFragmentResultListener(
                ThemeColorPickerDialog.RESULT_KEY,
                this,
                (requestKey, result) -> {
                    String target = result.getString(ThemeColorPickerDialog.RESULT_TARGET, "");
                    int color = result.getInt(ThemeColorPickerDialog.RESULT_COLOR, ContextCompat.getColor(requireContext(), R.color.theme_custom_primary));
                    applyCustomColor(target, color);
                }
        );

        customPrimary.setOnClickListener(v -> openColorPicker(
                R.string.theme_custom_primary_label,
                Preferences.getCustomThemePrimary(),
                "primary"
        ));
        customSecondary.setOnClickListener(v -> openColorPicker(
                R.string.theme_custom_secondary_label,
                Preferences.getCustomThemeSecondary(),
                "secondary"
        ));
        customTertiary.setOnClickListener(v -> openColorPicker(
                R.string.theme_custom_tertiary_label,
                Preferences.getCustomThemeTertiary(),
                "tertiary"
        ));

        View applyButton = view.findViewById(R.id.theme_custom_apply_button);
        applyButton.setOnClickListener(v -> {
            selectedTheme = ThemeHelper.CUSTOM;
            deliverResult(ThemeHelper.CUSTOM);
        });

        customCard.setOnClickListener(v -> {
            selectedTheme = ThemeHelper.CUSTOM;
            deliverResult(ThemeHelper.CUSTOM);
        });

        return view;
    }

    private void setupRecycler(View root, int recyclerId, List<ThemeOption> options) {
        RecyclerView recyclerView = root.findViewById(recyclerId);
        recyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        recyclerView.setNestedScrollingEnabled(false);

        ThemeOptionAdapter adapter = new ThemeOptionAdapter(options, selectedTheme, option -> deliverResult(option.id));
        recyclerView.setAdapter(adapter);

        if (recyclerId == R.id.theme_selector_system_recycler) {
            systemAdapter = adapter;
        } else if (recyclerId == R.id.theme_selector_light_recycler) {
            lightAdapter = adapter;
        } else if (recyclerId == R.id.theme_selector_dark_recycler) {
            darkAdapter = adapter;
        }
    }

    private void deliverResult(String themeId) {
        FragmentResultOwner owner = getParentFragmentManager();
        Bundle result = new Bundle();
        result.putString(RESULT_THEME, themeId);
        owner.setFragmentResult(RESULT_KEY, result);
        dismiss();
    }

    private void openColorPicker(int titleRes, int color, String target) {
        ThemeColorPickerDialog dialog = ThemeColorPickerDialog.newInstance(titleRes, color, target);
        dialog.show(getParentFragmentManager(), "ThemeColorPickerDialog");
    }

    private void applyCustomColor(String target, int color) {
        switch (target) {
            case "primary":
                Preferences.setCustomThemePrimary(color);
                break;
            case "secondary":
                Preferences.setCustomThemeSecondary(color);
                break;
            case "tertiary":
                Preferences.setCustomThemeTertiary(color);
                break;
            default:
                break;
        }
        updateCustomSwatches();
    }

    private void updateCustomSwatches() {
        int primary = Preferences.getCustomThemePrimary();
        int secondary = Preferences.getCustomThemeSecondary();
        int tertiary = Preferences.getCustomThemeTertiary();

        ViewCompat.setBackgroundTintList(customPrimary, ColorStateList.valueOf(primary));
        ViewCompat.setBackgroundTintList(customSecondary, ColorStateList.valueOf(secondary));
        ViewCompat.setBackgroundTintList(customTertiary, ColorStateList.valueOf(tertiary));
    }

    private void updateCustomSelection() {
        boolean isSelected = ThemeHelper.CUSTOM.equals(selectedTheme);
        customCheck.setVisibility(isSelected ? View.VISIBLE : View.GONE);
        int strokeWidth = Math.round(getResources().getDisplayMetrics().density * (isSelected ? 2 : 1));
        customCard.setStrokeWidth(strokeWidth);
        int strokeColor = isSelected
                ? MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorPrimary, ContextCompat.getColor(requireContext(), R.color.theme_custom_primary))
                : MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorOutline, ContextCompat.getColor(requireContext(), R.color.theme_custom_primary));
        customCard.setStrokeColor(strokeColor);
    }

    private List<ThemeOption> buildSystemOptions() {
        List<ThemeOption> options = new ArrayList<>();
        options.add(new ThemeOption(
                ThemeHelper.DEFAULT_MODE,
                R.string.theme_option_system,
                R.string.theme_desc_system,
                R.color.md_theme_light_primary,
                R.color.md_theme_light_secondary,
                R.color.md_theme_light_tertiary,
                false
        ));
        options.add(new ThemeOption(
                ThemeHelper.MATERIAL_YOU,
                R.string.theme_option_material_you,
                R.string.theme_desc_material_you,
                R.color.md_theme_light_primary,
                R.color.md_theme_light_secondary,
                R.color.md_theme_light_tertiary,
                true
        ));
        return options;
    }

    private List<ThemeOption> buildLightOptions() {
        List<ThemeOption> options = new ArrayList<>();
        options.add(new ThemeOption(
                ThemeHelper.LIGHT_MODE,
                R.string.theme_option_light,
                R.string.theme_desc_light,
                R.color.md_theme_light_primary,
                R.color.md_theme_light_secondary,
                R.color.md_theme_light_tertiary,
                false
        ));
        options.add(new ThemeOption(
                ThemeHelper.OCEAN,
                R.string.theme_option_ocean,
                R.string.theme_desc_ocean,
                R.color.theme_ocean_primary,
                R.color.theme_ocean_secondary,
                R.color.theme_ocean_tertiary,
                false
        ));
        options.add(new ThemeOption(
                ThemeHelper.FOREST,
                R.string.theme_option_forest,
                R.string.theme_desc_forest,
                R.color.theme_forest_primary,
                R.color.theme_forest_secondary,
                R.color.theme_forest_tertiary,
                false
        ));
        options.add(new ThemeOption(
                ThemeHelper.SUNSET,
                R.string.theme_option_sunset,
                R.string.theme_desc_sunset,
                R.color.theme_sunset_primary,
                R.color.theme_sunset_secondary,
                R.color.theme_sunset_tertiary,
                false
        ));
        options.add(new ThemeOption(
                ThemeHelper.ROSE,
                R.string.theme_option_rose,
                R.string.theme_desc_rose,
                R.color.theme_rose_primary,
                R.color.theme_rose_secondary,
                R.color.theme_rose_tertiary,
                false
        ));
        options.add(new ThemeOption(
                ThemeHelper.SLATE,
                R.string.theme_option_slate,
                R.string.theme_desc_slate,
                R.color.theme_slate_primary,
                R.color.theme_slate_secondary,
                R.color.theme_slate_tertiary,
                false
        ));
        options.add(new ThemeOption(
                ThemeHelper.CITRUS,
                R.string.theme_option_citrus,
                R.string.theme_desc_citrus,
                R.color.theme_citrus_primary,
                R.color.theme_citrus_secondary,
                R.color.theme_citrus_tertiary,
                false
        ));
        options.add(new ThemeOption(
                ThemeHelper.SAGE,
                R.string.theme_option_sage,
                R.string.theme_desc_sage,
                R.color.theme_sage_primary,
                R.color.theme_sage_secondary,
                R.color.theme_sage_tertiary,
                false
        ));
        options.add(new ThemeOption(
                ThemeHelper.LAGOON,
                R.string.theme_option_lagoon,
                R.string.theme_desc_lagoon,
                R.color.theme_lagoon_primary,
                R.color.theme_lagoon_secondary,
                R.color.theme_lagoon_tertiary,
                false
        ));
        options.add(new ThemeOption(
                ThemeHelper.DUNE,
                R.string.theme_option_dune,
                R.string.theme_desc_dune,
                R.color.theme_dune_primary,
                R.color.theme_dune_secondary,
                R.color.theme_dune_tertiary,
                false
        ));
        return options;
    }

    private List<ThemeOption> buildDarkOptions() {
        List<ThemeOption> options = new ArrayList<>();
        options.add(new ThemeOption(
                ThemeHelper.DARK_MODE,
                R.string.theme_option_dark,
                R.string.theme_desc_dark,
                R.color.md_theme_dark_primary,
                R.color.md_theme_dark_secondary,
                R.color.md_theme_dark_tertiary,
                false
        ));
        options.add(new ThemeOption(
                ThemeHelper.AMOLED_MODE,
                R.string.theme_option_amoled,
                R.string.theme_desc_amoled,
                R.color.md_theme_dark_primary,
                R.color.md_theme_dark_secondary,
                R.color.md_theme_dark_tertiary,
                false
        ));
        options.add(new ThemeOption(
                ThemeHelper.MIDNIGHT,
                R.string.theme_option_midnight,
                R.string.theme_desc_midnight,
                R.color.theme_midnight_primary,
                R.color.theme_midnight_secondary,
                R.color.theme_midnight_tertiary,
                false
        ));
        options.add(new ThemeOption(
                ThemeHelper.EMBER,
                R.string.theme_option_ember,
                R.string.theme_desc_ember,
                R.color.theme_ember_primary,
                R.color.theme_ember_secondary,
                R.color.theme_ember_tertiary,
                false
        ));
        options.add(new ThemeOption(
                ThemeHelper.OBSIDIAN,
                R.string.theme_option_obsidian,
                R.string.theme_desc_obsidian,
                R.color.theme_obsidian_primary,
                R.color.theme_obsidian_secondary,
                R.color.theme_obsidian_tertiary,
                false
        ));
        options.add(new ThemeOption(
                ThemeHelper.AURORA,
                R.string.theme_option_aurora,
                R.string.theme_desc_aurora,
                R.color.theme_aurora_primary,
                R.color.theme_aurora_secondary,
                R.color.theme_aurora_tertiary,
                false
        ));
        return options;
    }
}
