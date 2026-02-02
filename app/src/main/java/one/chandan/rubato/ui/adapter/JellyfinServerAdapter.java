package one.chandan.rubato.ui.adapter;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import one.chandan.rubato.databinding.ItemLoginServerBinding;
import one.chandan.rubato.interfaces.JellyfinClickCallback;
import one.chandan.rubato.model.JellyfinServer;

import java.util.ArrayList;
import java.util.List;

public class JellyfinServerAdapter extends RecyclerView.Adapter<JellyfinServerAdapter.ViewHolder> {
    private final JellyfinClickCallback clickCallback;
    private List<JellyfinServer> servers;

    public JellyfinServerAdapter(JellyfinClickCallback clickCallback) {
        this.clickCallback = clickCallback;
        this.servers = new ArrayList<>();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemLoginServerBinding view = ItemLoginServerBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        JellyfinServer server = servers.get(position);
        holder.item.serverNameTextView.setText(server.getName());
        String summary = server.getLibraryName();
        if (server.getAddress() != null && !server.getAddress().isEmpty()) {
            summary = summary + " · " + server.getAddress();
        }
        holder.item.serverAddressTextView.setText(summary);
    }

    @Override
    public int getItemCount() {
        return servers.size();
    }

    public void setItems(List<JellyfinServer> servers) {
        this.servers = servers == null ? new ArrayList<>() : servers;
        notifyDataSetChanged();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        ItemLoginServerBinding item;

        ViewHolder(ItemLoginServerBinding item) {
            super(item.getRoot());
            this.item = item;
            item.serverNameTextView.setSelected(true);

            itemView.setOnClickListener(v -> onClick());
            itemView.setOnLongClickListener(v -> onLongClick());
        }

        private void onClick() {
            Bundle bundle = new Bundle();
            bundle.putParcelable("jellyfin_server", servers.get(getBindingAdapterPosition()));
            clickCallback.onJellyfinClick(bundle);
        }

        private boolean onLongClick() {
            Bundle bundle = new Bundle();
            bundle.putParcelable("jellyfin_server", servers.get(getBindingAdapterPosition()));
            clickCallback.onJellyfinLongClick(bundle);
            return true;
        }
    }
}
