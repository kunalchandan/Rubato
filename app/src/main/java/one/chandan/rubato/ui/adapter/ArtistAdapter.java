package one.chandan.rubato.ui.adapter;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.media3.common.util.UnstableApi;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import one.chandan.rubato.R;
import one.chandan.rubato.databinding.ItemLibraryArtistBinding;
import one.chandan.rubato.glide.CustomGlideRequest;
import one.chandan.rubato.interfaces.ClickCallback;
import one.chandan.rubato.subsonic.models.ArtistID3;
import one.chandan.rubato.util.Constants;
import one.chandan.rubato.util.MusicUtil;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@UnstableApi
public class ArtistAdapter extends RecyclerView.Adapter<ArtistAdapter.ViewHolder> {
    private final ClickCallback click;
    private final boolean mix;
    private final boolean bestOf;

    private List<ArtistID3> artists;

    public ArtistAdapter(ClickCallback click, Boolean mix, Boolean bestOf) {
        this.click = click;
        this.mix = mix;
        this.bestOf = bestOf;
        this.artists = Collections.emptyList();
        setHasStableIds(true);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemLibraryArtistBinding view = ItemLibraryArtistBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        ArtistID3 artist = artists.get(position);

        holder.item.artistNameLabel.setText(artist.getName());

        CustomGlideRequest.Builder
                .from(holder.itemView.getContext(), artist.getCoverArtId(), CustomGlideRequest.ResourceType.Artist)
                .build()
                .into(holder.item.artistCoverImageView);
    }

    @Override
    public int getItemViewType(int position) {
        return R.layout.item_library_artist;
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
    public int getItemCount() {
        return artists.size();
    }

    public ArtistID3 getItem(int position) {
        return artists.get(position);
    }

    public void setItems(List<ArtistID3> artists) {
        List<ArtistID3> next = artists == null ? Collections.emptyList() : artists;
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new ArtistDiffCallback(this.artists, next));
        this.artists = next;
        diffResult.dispatchUpdatesTo(this);
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
        ItemLibraryArtistBinding item;

        ViewHolder(ItemLibraryArtistBinding item) {
            super(item.getRoot());

            this.item = item;

            item.artistNameLabel.setSelected(true);

            itemView.setOnClickListener(v -> onClick());
            itemView.setOnLongClickListener(v -> onLongClick());
        }

        public void onClick() {
            Bundle bundle = new Bundle();
            bundle.putParcelable(Constants.ARTIST_OBJECT, artists.get(getBindingAdapterPosition()));
            bundle.putBoolean(Constants.MEDIA_MIX, mix);
            bundle.putBoolean(Constants.MEDIA_BEST_OF, bestOf);

            click.onArtistClick(bundle);
        }

        public boolean onLongClick() {
            Bundle bundle = new Bundle();
            bundle.putParcelable(Constants.ARTIST_OBJECT, artists.get(getBindingAdapterPosition()));

            click.onArtistLongClick(bundle);

            return true;
        }
    }
}
