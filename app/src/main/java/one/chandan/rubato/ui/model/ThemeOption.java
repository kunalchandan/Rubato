package one.chandan.rubato.ui.model;

import androidx.annotation.ColorRes;
import androidx.annotation.StringRes;

public class ThemeOption {
    public final String id;
    public final int titleRes;
    public final int subtitleRes;
    public final int primaryColorRes;
    public final int secondaryColorRes;
    public final int tertiaryColorRes;
    public final boolean dynamic;

    public ThemeOption(String id,
                       @StringRes int titleRes,
                       @StringRes int subtitleRes,
                       @ColorRes int primaryColorRes,
                       @ColorRes int secondaryColorRes,
                       @ColorRes int tertiaryColorRes,
                       boolean dynamic) {
        this.id = id;
        this.titleRes = titleRes;
        this.subtitleRes = subtitleRes;
        this.primaryColorRes = primaryColorRes;
        this.secondaryColorRes = secondaryColorRes;
        this.tertiaryColorRes = tertiaryColorRes;
        this.dynamic = dynamic;
    }
}
