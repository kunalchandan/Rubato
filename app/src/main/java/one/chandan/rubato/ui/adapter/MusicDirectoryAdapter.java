package one.chandan.rubato.ui.adapter;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.media3.common.util.UnstableApi;
import androidx.recyclerview.widget.RecyclerView;

import one.chandan.rubato.databinding.ItemLibraryMusicDirectoryBinding;
import one.chandan.rubato.glide.CustomGlideRequest;
import one.chandan.rubato.interfaces.ClickCallback;
import one.chandan.rubato.subsonic.models.Child;
import one.chandan.rubato.repository.LocalMusicRepository;
import one.chandan.rubato.util.Constants;
import one.chandan.rubato.util.DownloadUtil;
import one.chandan.rubato.util.OfflinePolicy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@UnstableApi
public class MusicDirectoryAdapter extends RecyclerView.Adapter<MusicDirectoryAdapter.ViewHolder> {
    private final ClickCallback click;

    private List<Child> children;

    public MusicDirectoryAdapter(ClickCallback click) {
        this.click = click;
        this.children = Collections.emptyList();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemLibraryMusicDirectoryBinding view = ItemLibraryMusicDirectoryBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Child child = children.get(position);
        boolean isDownloaded = child.isDir() || DownloadUtil.getDownloadTracker(holder.itemView.getContext()).isDownloaded(child.getId());
        boolean isLocal = LocalMusicRepository.isLocalSong(child);
        boolean offlineUnavailable = !child.isDir() && OfflinePolicy.isOffline() && !isDownloaded && !isLocal;

        holder.item.musicDirectoryTitleTextView.setText(child.getTitle());

        CustomGlideRequest.ResourceType type = child.isDir()
                ? CustomGlideRequest.ResourceType.Directory
                : CustomGlideRequest.ResourceType.Song;

        CustomGlideRequest.Builder
                .from(holder.itemView.getContext(), child.getCoverArtId(), type)
                .build()
                .into(holder.item.musicDirectoryCoverImageView);

        holder.item.musicDirectoryMoreButton.setVisibility(child.isDir() ? View.VISIBLE : View.INVISIBLE);
        holder.item.musicDirectoryPlayButton.setVisibility(child.isDir() ? View.INVISIBLE : View.VISIBLE);
        holder.item.musicDirectoryPlayButton.setEnabled(!offlineUnavailable);
        holder.item.musicDirectoryPlayButton.setAlpha(offlineUnavailable ? 0.5f : 1f);
        holder.itemView.setAlpha(offlineUnavailable ? 0.5f : 1f);
        holder.offlineUnavailable = offlineUnavailable;
    }

    @Override
    public int getItemCount() {
        return children.size();
    }

    public void setItems(List<Child> children) {
        this.children = children != null ? children : Collections.emptyList();
        notifyDataSetChanged();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        ItemLibraryMusicDirectoryBinding item;
        boolean offlineUnavailable = false;

        ViewHolder(ItemLibraryMusicDirectoryBinding item) {
            super(item.getRoot());

            this.item = item;

            item.musicDirectoryTitleTextView.setSelected(true);

            itemView.setOnClickListener(v -> onClick());
            itemView.setOnLongClickListener(v -> onLongClick());

            item.musicDirectoryMoreButton.setOnClickListener(v -> onClick());
        }

        public void onClick() {
            Bundle bundle = new Bundle();

            if (children.get(getBindingAdapterPosition()).isDir()) {
                bundle.putString(Constants.MUSIC_DIRECTORY_ID, children.get(getBindingAdapterPosition()).getId());
                click.onMusicDirectoryClick(bundle);
            } else {
                if (offlineUnavailable) {
                    bundle.putParcelable(Constants.TRACK_OBJECT, children.get(getBindingAdapterPosition()));
                    click.onMediaLongClick(bundle);
                    return;
                }

                bundle.putParcelableArrayList(Constants.TRACKS_OBJECT, new ArrayList<>(children));
                bundle.putInt(Constants.ITEM_POSITION, getBindingAdapterPosition());
                click.onMediaClick(bundle);
            }
        }

        private boolean onLongClick() {
            if (!children.get(getBindingAdapterPosition()).isDir()) {
                Bundle bundle = new Bundle();
                bundle.putParcelable(Constants.TRACK_OBJECT, children.get(getBindingAdapterPosition()));

                click.onMediaLongClick(bundle);

                return true;
            } else {
                return false;
            }
        }
    }
}
