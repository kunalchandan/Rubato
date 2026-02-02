package one.chandan.rubato.ui.adapter;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import one.chandan.rubato.databinding.ItemHorizontalArtistBinding;
import one.chandan.rubato.glide.CustomGlideRequest;
import one.chandan.rubato.interfaces.ClickCallback;
import one.chandan.rubato.subsonic.models.AlbumID3;
import one.chandan.rubato.subsonic.models.ArtistID3;
import one.chandan.rubato.util.Constants;
import one.chandan.rubato.util.MusicUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class ArtistHorizontalAdapter extends RecyclerView.Adapter<ArtistHorizontalAdapter.ViewHolder> implements Filterable {
    private final ClickCallback click;

    private List<ArtistID3> artistsFull;
    private List<ArtistID3> artists;
    private String currentFilter;

    private final Filter filtering = new Filter() {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            List<ArtistID3> filteredList = new ArrayList<>();

            if (constraint == null || constraint.length() == 0) {
                filteredList.addAll(artistsFull);
            } else {
                String filterPattern = constraint.toString().toLowerCase().trim();
                currentFilter = filterPattern;

                for (ArtistID3 item : artistsFull) {
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
            List<ArtistID3> next = results.values == null ? Collections.emptyList() : (List<ArtistID3>) results.values;
            DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new ArtistDiffCallback(artists, next));
            artists = next;
            diffResult.dispatchUpdatesTo(ArtistHorizontalAdapter.this);
        }
    };

    public ArtistHorizontalAdapter(ClickCallback click) {
        this.click = click;
        this.artists = Collections.emptyList();
        this.artistsFull = Collections.emptyList();
        this.currentFilter = "";
        setHasStableIds(true);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemHorizontalArtistBinding view = ItemHorizontalArtistBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        ArtistID3 artist = artists.get(position);

        holder.item.artistNameTextView.setText(artist.getName());

        if (artist.getAlbumCount() > 0) {
            holder.item.artistInfoTextView.setText("Album count: " + artist.getAlbumCount());
        } else {
            holder.item.artistInfoTextView.setVisibility(View.GONE);
        }

        CustomGlideRequest.Builder
                .from(holder.itemView.getContext(), artist.getCoverArtId(), CustomGlideRequest.ResourceType.Artist)
                .build()
                .into(holder.item.artistCoverImageView);
    }

    @Override
    public int getItemCount() {
        return artists.size();
    }

    public void setItems(List<ArtistID3> artists) {
        this.artistsFull = artists != null ? artists : Collections.emptyList();
        filtering.filter(currentFilter);
    }

    @Override
    public long getItemId(int position) {
        if (position < 0 || position >= artists.size()) {
            return RecyclerView.NO_ID;
        }
        ArtistID3 artist = artists.get(position);
        if (artist == null) {
            return RecyclerView.NO_ID;
        }
        if (artist.getId() != null) {
            return artist.getId().hashCode();
        }
        return (artist.getName() == null ? "" : artist.getName()).hashCode();
    }

    @Override
    public Filter getFilter() {
        return filtering;
    }

    public ArtistID3 getItem(int id) {
        return artists.get(id);
    }

    private static class ArtistDiffCallback extends DiffUtil.Callback {
        private final List<ArtistID3> oldList;
        private final List<ArtistID3> newList;

        ArtistDiffCallback(List<ArtistID3> oldList, List<ArtistID3> newList) {
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
            ArtistID3 oldItem = oldList.get(oldItemPosition);
            ArtistID3 newItem = newList.get(newItemPosition);
            if (oldItem == null || newItem == null) {
                return false;
            }
            return Objects.equals(oldItem.getId(), newItem.getId())
                    && Objects.equals(oldItem.getName(), newItem.getName());
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            ArtistID3 oldItem = oldList.get(oldItemPosition);
            ArtistID3 newItem = newList.get(newItemPosition);
            if (oldItem == null || newItem == null) {
                return false;
            }
            return Objects.equals(oldItem.getName(), newItem.getName())
                    && Objects.equals(oldItem.getCoverArtId(), newItem.getCoverArtId())
                    && oldItem.getAlbumCount() == newItem.getAlbumCount();
        }
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        ItemHorizontalArtistBinding item;

        ViewHolder(ItemHorizontalArtistBinding item) {
            super(item.getRoot());

            this.item = item;

            item.artistNameTextView.setSelected(true);

            itemView.setOnClickListener(v -> onClick());
            itemView.setOnLongClickListener(v -> onLongClick());

            item.artistMoreButton.setOnClickListener(v -> onLongClick());
        }

        private void onClick() {
            Bundle bundle = new Bundle();
            bundle.putParcelable(Constants.ARTIST_OBJECT, artists.get(getBindingAdapterPosition()));

            click.onArtistClick(bundle);
        }

        public boolean onLongClick() {
            Bundle bundle = new Bundle();
            bundle.putParcelable(Constants.ARTIST_OBJECT, artists.get(getBindingAdapterPosition()));

            click.onArtistLongClick(bundle);

            return true;
        }
    }

    public void sort(String order) {
        switch (order) {
            case Constants.ARTIST_ORDER_BY_NAME:
                artists.sort(Comparator.comparing(ArtistID3::getName));
                break;
            case Constants.ARTIST_ORDER_BY_MOST_RECENTLY_STARRED:
                artists.sort(Comparator.comparing(ArtistID3::getStarred, Comparator.nullsLast(Comparator.reverseOrder())));
                break;
            case Constants.ARTIST_ORDER_BY_LEAST_RECENTLY_STARRED:
                artists.sort(Comparator.comparing(ArtistID3::getStarred, Comparator.nullsLast(Comparator.naturalOrder())));

                break;
        }

        notifyDataSetChanged();
    }
}
