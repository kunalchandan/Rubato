package one.chandan.rubato.ui.adapter;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import one.chandan.rubato.databinding.ItemHorizontalAlbumBinding;
import one.chandan.rubato.glide.CustomGlideRequest;
import one.chandan.rubato.interfaces.ClickCallback;
import one.chandan.rubato.subsonic.models.AlbumID3;
import one.chandan.rubato.subsonic.models.Child;
import one.chandan.rubato.util.Constants;
import one.chandan.rubato.util.MusicUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class AlbumHorizontalAdapter extends RecyclerView.Adapter<AlbumHorizontalAdapter.ViewHolder> implements Filterable {
    private final ClickCallback click;
    private final boolean isOffline;

    private List<AlbumID3> albumsFull;
    private List<AlbumID3> albums;
    private String currentFilter;

    private final Filter filtering = new Filter() {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            List<AlbumID3> filteredList = new ArrayList<>();

            if (constraint == null || constraint.length() == 0) {
                filteredList.addAll(albumsFull);
            } else {
                String filterPattern = constraint.toString().toLowerCase().trim();
                currentFilter = filterPattern;

                for (AlbumID3 item : albumsFull) {
                    if (item.getName().toLowerCase().contains(filterPattern)) {
                        filteredList.add(item);
                    }
                }
            }

            FilterResults results = new FilterResults();
            results.values = filteredList;

            return results;
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            List<AlbumID3> next = results.values == null ? Collections.emptyList() : (List<AlbumID3>) results.values;
            DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new AlbumDiffCallback(albums, next));
            albums = next;
            diffResult.dispatchUpdatesTo(AlbumHorizontalAdapter.this);
        }
    };

    public AlbumHorizontalAdapter(ClickCallback click, boolean isOffline) {
        this.click = click;
        this.isOffline = isOffline;
        this.albums = Collections.emptyList();
        this.albumsFull = Collections.emptyList();
        this.currentFilter = "";
        setHasStableIds(true);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemHorizontalAlbumBinding view = ItemHorizontalAlbumBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        AlbumID3 album = albums.get(position);

        holder.item.albumTitleTextView.setText(album.getName());
        holder.item.albumArtistTextView.setText(album.getArtist());

        CustomGlideRequest.Builder
                .from(holder.itemView.getContext(), album.getCoverArtId(), CustomGlideRequest.ResourceType.Album)
                .build()
                .into(holder.item.albumCoverImageView);
    }

    @Override
    public int getItemCount() {
        return albums.size();
    }

    public void setItems(List<AlbumID3> albums) {
        this.albumsFull = albums != null ? albums : Collections.emptyList();
        filtering.filter(currentFilter);
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
    public Filter getFilter() {
        return filtering;
    }

    public AlbumID3 getItem(int id) {
        return albums.get(id);
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
        ItemHorizontalAlbumBinding item;

        ViewHolder(ItemHorizontalAlbumBinding item) {
            super(item.getRoot());

            this.item = item;

            item.albumTitleTextView.setSelected(true);

            itemView.setOnClickListener(v -> onClick());
            itemView.setOnLongClickListener(v -> onLongClick());

            item.albumMoreButton.setOnClickListener(v -> onLongClick());
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

    public void sort(String order) {
        switch (order) {
            case Constants.ALBUM_ORDER_BY_NAME:
                albums.sort(Comparator.comparing(AlbumID3::getName));
                break;
            case Constants.ALBUM_ORDER_BY_MOST_RECENTLY_STARRED:
                albums.sort(Comparator.comparing(AlbumID3::getStarred, Comparator.nullsLast(Comparator.reverseOrder())));
                break;
            case Constants.ALBUM_ORDER_BY_LEAST_RECENTLY_STARRED:
                albums.sort(Comparator.comparing(AlbumID3::getStarred, Comparator.nullsLast(Comparator.naturalOrder())));

                break;
        }

        notifyDataSetChanged();
    }
}
