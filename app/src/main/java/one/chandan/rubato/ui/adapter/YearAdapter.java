package one.chandan.rubato.ui.adapter;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import one.chandan.rubato.R;
import one.chandan.rubato.databinding.ItemHomeYearBinding;
import one.chandan.rubato.interfaces.ClickCallback;
import one.chandan.rubato.util.Constants;

import java.util.Collections;
import java.util.List;

public class YearAdapter extends RecyclerView.Adapter<YearAdapter.ViewHolder> {
    private final ClickCallback click;

    private List<Integer> years;

    public YearAdapter(ClickCallback click) {
        this.click = click;
        this.years = Collections.emptyList();
        setHasStableIds(true);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemHomeYearBinding view = ItemHomeYearBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        int year = years.get(position);

        holder.item.yearLabel.setText(formatDecadeLabel(year));
        applyDecadeStyle(holder, year);
    }

    @Override
    public int getItemViewType(int position) {
        return R.layout.item_home_year;
    }

    @Override
    public long getItemId(int position) {
        if (position < 0 || position >= years.size()) {
            return RecyclerView.NO_ID;
        }
        return years.get(position);
    }

    @Override
    public int getItemCount() {
        return years.size();
    }

    public Integer getItem(int position) {
        return years.get(position);
    }

    public void setItems(List<Integer> years) {
        List<Integer> next = years == null ? Collections.emptyList() : years;
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new YearDiffCallback(this.years, next));
        this.years = next;
        diffResult.dispatchUpdatesTo(this);
    }

    private static class YearDiffCallback extends DiffUtil.Callback {
        private final List<Integer> oldList;
        private final List<Integer> newList;

        YearDiffCallback(List<Integer> oldList, List<Integer> newList) {
            this.oldList = oldList == null ? Collections.emptyList() : oldList;
            this.newList = newList == null ? Collections.emptyList() : newList;
        }

        @Override
        public int getOldListSize() {
            return oldList.size();
        }

        @Override
        public int getNewListSize() {
            return newList.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return oldList.get(oldItemPosition).equals(newList.get(newItemPosition));
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            return oldList.get(oldItemPosition).equals(newList.get(newItemPosition));
        }
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        ItemHomeYearBinding item;

        ViewHolder(ItemHomeYearBinding item) {
            super(item.getRoot());

            this.item = item;

            itemView.setOnClickListener(v -> onClick());
        }

        public void onClick() {
            Bundle bundle = new Bundle();
            bundle.putString(Constants.MEDIA_BY_YEAR, Constants.MEDIA_BY_YEAR);
            bundle.putInt("year_object", years.get(getBindingAdapterPosition()));

            click.onYearClick(bundle);
        }
    }

    private String formatDecadeLabel(int decade) {
        return decade + "s";
    }

    private void applyDecadeStyle(ViewHolder holder, int decade) {
        Context context = holder.itemView.getContext();
        int normalizedDecade = decade - (decade % 10);

        holder.item.flashbackContainer.setBackgroundResource(R.drawable.bg_flashback_default);
        holder.item.yearLabel.setRotation(0f);
        holder.item.yearLabel.setLetterSpacing(0f);
        holder.item.yearLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f);
        holder.item.yearLabel.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT);
        holder.item.yearLabel.setTextColor(ContextCompat.getColor(context, R.color.titleTextColor));

        Typeface defaultTypeface = ResourcesCompat.getFont(context, R.font.inter_medium);
        holder.item.yearLabel.setTypeface(defaultTypeface, Typeface.NORMAL);

        if (normalizedDecade <= 1950) {
            holder.item.flashbackContainer.setBackgroundResource(R.drawable.bg_flashback_1950s);
            holder.item.yearLabel.setTypeface(ResourcesCompat.getFont(context, R.font.lobster), Typeface.NORMAL);
            holder.item.yearLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f);
            holder.item.yearLabel.setRotation(-4f);
            holder.item.yearLabel.setTextColor(Color.parseColor("#4E342E"));
        } else if (normalizedDecade == 1960) {
            holder.item.flashbackContainer.setBackgroundResource(R.drawable.bg_flashback_1960s);
            holder.item.yearLabel.setTypeface(ResourcesCompat.getFont(context, R.font.monoton), Typeface.NORMAL);
            holder.item.yearLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f);
            holder.item.yearLabel.setRotation(3f);
            holder.item.yearLabel.setTextColor(Color.parseColor("#FFF8E1"));
        } else if (normalizedDecade == 1970) {
            holder.item.flashbackContainer.setBackgroundResource(R.drawable.bg_flashback_1970s);
            holder.item.yearLabel.setTypeface(ResourcesCompat.getFont(context, R.font.bungee), Typeface.NORMAL);
            holder.item.yearLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f);
            holder.item.yearLabel.setRotation(-6f);
            holder.item.yearLabel.setTextColor(Color.parseColor("#3E2723"));
        } else if (normalizedDecade == 1980) {
            holder.item.flashbackContainer.setBackgroundResource(R.drawable.bg_flashback_1980s);
            holder.item.yearLabel.setTypeface(ResourcesCompat.getFont(context, R.font.audiowide), Typeface.NORMAL);
            holder.item.yearLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f);
            holder.item.yearLabel.setRotation(4f);
            holder.item.yearLabel.setTextColor(Color.parseColor("#FFFFFF"));
            holder.item.yearLabel.setShadowLayer(8f, 0f, 0f, Color.parseColor("#7C4DFF"));
        } else if (normalizedDecade == 1990) {
            holder.item.flashbackContainer.setBackgroundResource(R.drawable.bg_flashback_1990s);
            holder.item.yearLabel.setTypeface(ResourcesCompat.getFont(context, R.font.bungee), Typeface.NORMAL);
            holder.item.yearLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f);
            holder.item.yearLabel.setRotation(-6f);
            holder.item.yearLabel.setLetterSpacing(0.06f);
            holder.item.yearLabel.setTextColor(Color.parseColor("#102D2A"));
        } else if (normalizedDecade == 2000) {
            holder.item.flashbackContainer.setBackgroundResource(R.drawable.bg_flashback_2000s);
            holder.item.yearLabel.setTypeface(ResourcesCompat.getFont(context, R.font.audiowide), Typeface.NORMAL);
            holder.item.yearLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 23f);
            holder.item.yearLabel.setRotation(3f);
            holder.item.yearLabel.setLetterSpacing(0.08f);
            holder.item.yearLabel.setTextColor(Color.parseColor("#0D2A4A"));
        } else if (normalizedDecade == 2010) {
            holder.item.flashbackContainer.setBackgroundResource(R.drawable.bg_flashback_2010s);
            holder.item.yearLabel.setTypeface(ResourcesCompat.getFont(context, R.font.montserrat_alternates), Typeface.NORMAL);
            holder.item.yearLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 23f);
            holder.item.yearLabel.setRotation(-2f);
            holder.item.yearLabel.setLetterSpacing(0.12f);
            holder.item.yearLabel.setTextColor(Color.parseColor("#1C2B30"));
        } else {
            holder.item.flashbackContainer.setBackgroundResource(R.drawable.bg_flashback_2020s);
            holder.item.yearLabel.setTypeface(ResourcesCompat.getFont(context, R.font.space_grotesk), Typeface.NORMAL);
            holder.item.yearLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f);
            holder.item.yearLabel.setRotation(6f);
            holder.item.yearLabel.setLetterSpacing(0.14f);
            holder.item.yearLabel.setTextColor(Color.parseColor("#E3F2FD"));
            holder.item.yearLabel.setShadowLayer(10f, 0f, 0f, Color.parseColor("#1B1F3B"));
        }
    }
}
