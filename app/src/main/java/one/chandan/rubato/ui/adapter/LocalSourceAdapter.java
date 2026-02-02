package one.chandan.rubato.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import one.chandan.rubato.databinding.ItemLocalSourceBinding;
import one.chandan.rubato.model.LocalSource;

import java.util.ArrayList;
import java.util.List;

public class LocalSourceAdapter extends RecyclerView.Adapter<LocalSourceAdapter.ViewHolder> {
    public interface Listener {
        void onLocalSourceRemove(LocalSource source);
    }

    private final Listener listener;
    private List<LocalSource> sources;

    public LocalSourceAdapter(Listener listener) {
        this.listener = listener;
        this.sources = new ArrayList<>();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemLocalSourceBinding view = ItemLocalSourceBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LocalSource source = sources.get(position);
        if (source == null) {
            holder.item.localSourceName.setText("");
            holder.item.localSourcePath.setText("");
            holder.item.localSourceRemoveButton.setOnClickListener(null);
            return;
        }
        holder.item.localSourceName.setText(source.getDisplayName());
        String path = source.getRelativePath();
        holder.item.localSourcePath.setText(path != null && !path.isEmpty() ? path : source.getTreeUri());
        holder.item.localSourceRemoveButton.setOnClickListener(v -> listener.onLocalSourceRemove(source));
    }

    @Override
    public int getItemCount() {
        return sources.size();
    }

    public void setItems(List<LocalSource> sources) {
        this.sources = sources != null ? sources : new ArrayList<>();
        notifyDataSetChanged();
    }

    public LocalSource getItem(int id) {
        return sources.get(id);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ItemLocalSourceBinding item;

        ViewHolder(ItemLocalSourceBinding item) {
            super(item.getRoot());
            this.item = item;
        }
    }
}
