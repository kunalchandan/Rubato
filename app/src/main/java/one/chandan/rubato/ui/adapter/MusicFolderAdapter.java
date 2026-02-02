package one.chandan.rubato.ui.adapter;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.media3.common.util.UnstableApi;
import androidx.recyclerview.widget.RecyclerView;

import one.chandan.rubato.databinding.ItemLibraryMusicFolderBinding;
import one.chandan.rubato.glide.CustomGlideRequest;
import one.chandan.rubato.interfaces.ClickCallback;
import one.chandan.rubato.model.LibrarySourceItem;
import one.chandan.rubato.subsonic.models.MusicFolder;
import one.chandan.rubato.util.Constants;
import one.chandan.rubato.R;

import java.util.Collections;
import java.util.List;

@UnstableApi
public class MusicFolderAdapter extends RecyclerView.Adapter<MusicFolderAdapter.ViewHolder> {
    private final ClickCallback click;

    private List<LibrarySourceItem> sources;

    public MusicFolderAdapter(ClickCallback click) {
        this.click = click;
        this.sources = Collections.emptyList();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemLibraryMusicFolderBinding view = ItemLibraryMusicFolderBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        LibrarySourceItem source = sources.get(position);

        holder.item.musicFolderTitleTextView.setText(source.getTitle());
        String sourceTypeLabel = source.getKind() == LibrarySourceItem.Kind.SUBSONIC
                ? holder.itemView.getContext().getString(R.string.library_source_type_subsonic)
                : holder.itemView.getContext().getString(R.string.library_source_type_local);
        holder.item.musicFolderSubtitleTextView.setText(
                holder.itemView.getContext().getString(
                        R.string.library_source_subtitle_format,
                        source.getSourceName(),
                        sourceTypeLabel
                )
        );

        if (source.getKind() == LibrarySourceItem.Kind.SUBSONIC) {
            MusicFolder musicFolder = source.getMusicFolder();
            String name = musicFolder != null ? musicFolder.getName() : null;
            CustomGlideRequest.Builder
                    .from(holder.itemView.getContext(), name, CustomGlideRequest.ResourceType.Folder)
                    .build()
                    .into(holder.item.musicFolderCoverImageView);
        } else {
            holder.item.musicFolderCoverImageView.setImageResource(R.drawable.ic_placeholder_folder);
        }
    }

    @Override
    public int getItemCount() {
        return sources.size();
    }

    public void setItems(List<LibrarySourceItem> sources) {
        this.sources = sources != null ? sources : Collections.emptyList();
        notifyDataSetChanged();
    }

    public LibrarySourceItem getItem(int position) {
        return sources.get(position);
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        ItemLibraryMusicFolderBinding item;

        ViewHolder(ItemLibraryMusicFolderBinding item) {
            super(item.getRoot());

            this.item = item;

            item.musicFolderTitleTextView.setSelected(true);

            itemView.setOnClickListener(v -> onClick());

            item.musicFolderMoreButton.setOnClickListener(v -> onClick());
        }

        public void onClick() {
            LibrarySourceItem source = sources.get(getBindingAdapterPosition());
            Bundle bundle = new Bundle();
            if (source.getKind() == LibrarySourceItem.Kind.SUBSONIC && source.getMusicFolder() != null) {
                bundle.putParcelable(Constants.MUSIC_FOLDER_OBJECT, source.getMusicFolder());
            } else if (source.getLocalSource() != null) {
                bundle.putParcelable(Constants.LOCAL_SOURCE_OBJECT, source.getLocalSource());
            }
            click.onMusicFolderClick(bundle);
        }
    }
}
