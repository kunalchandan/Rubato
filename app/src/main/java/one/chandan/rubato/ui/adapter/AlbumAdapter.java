package one.chandan.rubato.ui.adapter;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import one.chandan.rubato.R;
import one.chandan.rubato.databinding.ItemLibraryAlbumBinding;
import one.chandan.rubato.glide.CustomGlideRequest;
import one.chandan.rubato.interfaces.ClickCallback;
import one.chandan.rubato.subsonic.models.AlbumID3;
import one.chandan.rubato.util.Constants;
import one.chandan.rubato.util.MusicUtil;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class AlbumAdapter extends RecyclerView.Adapter<AlbumAdapter.ViewHolder> {
    private final ClickCallback click;

    private List<AlbumID3> albums;

    public AlbumAdapter(ClickCallback click) {
        this.click = click;
        this.albums = Collections.emptyList();
        setHasStableIds(true);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemLibraryAlbumBinding view = ItemLibraryAlbumBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        AlbumID3 album = albums.get(position);

        holder.item.albumNameLabel.setText(album.getName());
        holder.item.artistNameLabel.setText(album.getArtist());

        CustomGlideRequest.Builder
                .from(holder.itemView.getContext(), album.getCoverArtId(), CustomGlideRequest.ResourceType.Album)
                .build()
                .into(holder.item.albumCoverImageView);
    }

    @Override
    public int getItemViewType(int position) {
        return R.layout.item_library_album;
    }

    @Override
    public long getItemId(int position) {
        if (position < 0 || position >= albums.size()) {
            return RecyclerView.NO_ID;
        }
        AlbumID3 album = albums.get(position);
        if (album == null) {
            return RecyclerView.NO_ID;
        }
        if (album.getId() != null) {
            return album.getId().hashCode();
        }
        String key = (album.getName() == null ? "" : album.getName())
                + "|" + (album.getArtist() == null ? "" : album.getArtist());
        return key.hashCode();
    }

    @Override
    public int getItemCount() {
        return albums.size();
    }

    public AlbumID3 getItem(int position) {
        return albums.get(position);
    }

    public void setItems(List<AlbumID3> albums) {
        List<AlbumID3> next = albums == null ? Collections.emptyList() : albums;
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new AlbumDiffCallback(this.albums, next));
        this.albums = next;
        diffResult.dispatchUpdatesTo(this);
    }

    private static class AlbumDiffCallback extends DiffUtil.Callback {
        private final List<AlbumID3> oldList;
        private final List<AlbumID3> newList;

        AlbumDiffCallback(List<AlbumID3> oldList, List<AlbumID3> newList) {
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
            AlbumID3 oldItem = oldList.get(oldItemPosition);
            AlbumID3 newItem = newList.get(newItemPosition);
            if (oldItem == null || newItem == null) {
                return false;
            }
            return Objects.equals(oldItem.getId(), newItem.getId())
                    && Objects.equals(oldItem.getName(), newItem.getName());
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            AlbumID3 oldItem = oldList.get(oldItemPosition);
            AlbumID3 newItem = newList.get(newItemPosition);
            if (oldItem == null || newItem == null) {
                return false;
            }
            return Objects.equals(oldItem.getName(), newItem.getName())
                    && Objects.equals(oldItem.getArtist(), newItem.getArtist())
                    && Objects.equals(oldItem.getCoverArtId(), newItem.getCoverArtId());
        }
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        ItemLibraryAlbumBinding item;

        ViewHolder(ItemLibraryAlbumBinding item) {
            super(item.getRoot());

            this.item = item;

            item.albumNameLabel.setSelected(true);
            item.artistNameLabel.setSelected(true);

            itemView.setOnClickListener(v -> onClick());
            itemView.setOnLongClickListener(v -> onLongClick());
        }

        private void onClick() {
            Bundle bundle = new Bundle();
            bundle.putParcelable(Constants.ALBUM_OBJECT, albums.get(getBindingAdapterPosition()));

            click.onAlbumClick(bundle);
        }

        private boolean onLongClick() {
            Bundle bundle = new Bundle();
            bundle.putParcelable(Constants.ALBUM_OBJECT, albums.get(getBindingAdapterPosition()));

            click.onAlbumLongClick(bundle);

            return true;
        }
    }
}
