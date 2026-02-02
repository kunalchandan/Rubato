package one.chandan.rubato.ui.adapter;

import android.content.Context;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;

import java.util.List;

import one.chandan.rubato.R;
import one.chandan.rubato.ui.model.ThemeOption;

public class ThemeOptionAdapter extends RecyclerView.Adapter<ThemeOptionAdapter.ThemeOptionViewHolder> {
    public interface ThemeClickListener {
        void onThemeSelected(ThemeOption option);
    }

    private final List<ThemeOption> items;
    private final ThemeClickListener listener;
    private String selectedId;

    public ThemeOptionAdapter(List<ThemeOption> items, String selectedId, ThemeClickListener listener) {
        this.items = items;
        this.selectedId = selectedId;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ThemeOptionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_theme_option, parent, false);
        return new ThemeOptionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ThemeOptionViewHolder holder, int position) {
        ThemeOption option = items.get(position);
        Context context = holder.itemView.getContext();

        holder.title.setText(option.titleRes);
        holder.subtitle.setText(option.subtitleRes);

        int primary = resolveColor(context, option, option.primaryColorRes, android.R.color.system_accent1_500);
        int secondary = resolveColor(context, option, option.secondaryColorRes, android.R.color.system_accent2_500);
        int tertiary = resolveColor(context, option, option.tertiaryColorRes, android.R.color.system_accent3_500);

        holder.primary.setBackgroundTintList(android.content.res.ColorStateList.valueOf(primary));
        holder.secondary.setBackgroundTintList(android.content.res.ColorStateList.valueOf(secondary));
        holder.tertiary.setBackgroundTintList(android.content.res.ColorStateList.valueOf(tertiary));

        boolean isSelected = option.id.equals(selectedId);
        holder.check.setVisibility(isSelected ? View.VISIBLE : View.GONE);
        holder.card.setStrokeWidth(isSelected ? dpToPx(context, 2) : dpToPx(context, 1));
        holder.card.setStrokeColor(isSelected
                ? MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimary, primary)
                : MaterialColors.getColor(context, com.google.android.material.R.attr.colorOutline, primary));

        holder.card.setOnClickListener(v -> {
            if (listener != null) listener.onThemeSelected(option);
        });
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    public void setSelectedId(String selectedId) {
        this.selectedId = selectedId;
        notifyDataSetChanged();
    }

    private int dpToPx(Context context, int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    @ColorInt
    private int resolveColor(Context context, ThemeOption option, int fallbackRes, int systemColorRes) {
        if (!option.dynamic) {
            return ContextCompat.getColor(context, fallbackRes);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.getColor(context, systemColorRes);
        }

        return MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimary, ContextCompat.getColor(context, fallbackRes));
    }

    static class ThemeOptionViewHolder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final TextView title;
        final TextView subtitle;
        final View primary;
        final View secondary;
        final View tertiary;
        final ImageView check;

        ThemeOptionViewHolder(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.theme_option_card);
            title = itemView.findViewById(R.id.theme_option_title);
            subtitle = itemView.findViewById(R.id.theme_option_subtitle);
            primary = itemView.findViewById(R.id.theme_option_primary);
            secondary = itemView.findViewById(R.id.theme_option_secondary);
            tertiary = itemView.findViewById(R.id.theme_option_tertiary);
            check = itemView.findViewById(R.id.theme_option_check);
        }
    }
}
