package one.chandan.rubato.ui.dialog;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.FragmentResultOwner;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.slider.Slider;

import one.chandan.rubato.R;

public class ThemeColorPickerDialog extends BottomSheetDialogFragment {
    public static final String RESULT_KEY = "theme_color_picker_result";
    public static final String RESULT_COLOR = "theme_color_picker_color";
    public static final String RESULT_TARGET = "theme_color_picker_target";

    private static final String ARG_TITLE = "theme_color_picker_title";
    private static final String ARG_COLOR = "theme_color_picker_color";
    private static final String ARG_TARGET = "theme_color_picker_target";

    public static ThemeColorPickerDialog newInstance(@StringRes int titleRes,
                                                     @ColorInt int color,
                                                     @NonNull String target) {
        ThemeColorPickerDialog dialog = new ThemeColorPickerDialog();
        Bundle args = new Bundle();
        args.putInt(ARG_TITLE, titleRes);
        args.putInt(ARG_COLOR, color);
        args.putString(ARG_TARGET, target);
        dialog.setArguments(args);
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_color_picker, container, false);

        TextView title = view.findViewById(R.id.color_picker_title);
        TextView hex = view.findViewById(R.id.color_picker_hex);
        View preview = view.findViewById(R.id.color_picker_preview);
        Slider red = view.findViewById(R.id.color_picker_red);
        Slider green = view.findViewById(R.id.color_picker_green);
        Slider blue = view.findViewById(R.id.color_picker_blue);

        Bundle args = getArguments();
        int titleRes = args != null ? args.getInt(ARG_TITLE, R.string.theme_color_picker_title_default) : R.string.theme_color_picker_title_default;
        int initial = args != null ? args.getInt(ARG_COLOR, Color.WHITE) : Color.WHITE;
        String target = args != null ? args.getString(ARG_TARGET, "") : "";

        title.setText(titleRes);

        int initRed = Color.red(initial);
        int initGreen = Color.green(initial);
        int initBlue = Color.blue(initial);

        red.setValue(initRed);
        green.setValue(initGreen);
        blue.setValue(initBlue);

        updatePreview(preview, hex, initRed, initGreen, initBlue);

        Slider.OnChangeListener listener = (slider, value, fromUser) -> {
            int r = Math.round(red.getValue());
            int g = Math.round(green.getValue());
            int b = Math.round(blue.getValue());
            updatePreview(preview, hex, r, g, b);
        };

        red.addOnChangeListener(listener);
        green.addOnChangeListener(listener);
        blue.addOnChangeListener(listener);

        view.findViewById(R.id.color_picker_cancel).setOnClickListener(v -> dismiss());
        view.findViewById(R.id.color_picker_save).setOnClickListener(v -> {
            int r = Math.round(red.getValue());
            int g = Math.round(green.getValue());
            int b = Math.round(blue.getValue());
            int color = Color.rgb(r, g, b);

            Bundle result = new Bundle();
            result.putInt(RESULT_COLOR, color);
            result.putString(RESULT_TARGET, target);
            FragmentResultOwner owner = getParentFragmentManager();
            owner.setFragmentResult(RESULT_KEY, result);
            dismiss();
        });

        return view;
    }

    private void updatePreview(View preview, TextView hex, int r, int g, int b) {
        int color = Color.rgb(r, g, b);
        ViewCompat.setBackgroundTintList(preview, ColorStateList.valueOf(color));
        String hexValue = String.format("#%06X", 0xFFFFFF & color);
        hex.setText(hexValue);
    }
}
