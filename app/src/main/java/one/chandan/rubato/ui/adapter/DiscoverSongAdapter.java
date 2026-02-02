package one.chandan.rubato.ui.adapter;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import one.chandan.rubato.databinding.ItemHomeDiscoverSongBinding;
import one.chandan.rubato.glide.CustomGlideRequest;
import one.chandan.rubato.interfaces.ClickCallback;
import one.chandan.rubato.subsonic.models.Child;
import one.chandan.rubato.util.Constants;
import one.chandan.rubato.util.MusicUtil;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.shape.AbsoluteCornerSize;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class DiscoverSongAdapter extends RecyclerView.Adapter<DiscoverSongAdapter.ViewHolder> {
    private final ClickCallback click;

    private List<Child> songs;

    public DiscoverSongAdapter(ClickCallback click) {
        this.click = click;
        this.songs = Collections.emptyList();
        setHasStableIds(true);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemHomeDiscoverSongBinding view = ItemHomeDiscoverSongBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Child song = songs.get(position);

        holder.item.titleDiscoverSongLabel.setText(song.getTitle());
        holder.item.albumDiscoverSongLabel.setText(song.getAlbum());

        CustomGlideRequest.Builder
                .from(holder.itemView.getContext(), song.getCoverArtId(), CustomGlideRequest.ResourceType.Album)
                .build()
                .into(holder.item.discoverSongCoverImageView);

        if (holder.item.discoverSongCoverImageView instanceof ShapeableImageView) {
            ShapeableImageView shapeableImageView = (ShapeableImageView) holder.item.discoverSongCoverImageView;
            float radius = CustomGlideRequest.getCornerRadius(CustomGlideRequest.ResourceType.Album);
            shapeableImageView.setShapeAppearanceModel(
                    shapeableImageView.getShapeAppearanceModel()
                            .toBuilder()
                            .setAllCornerSizes(new AbsoluteCornerSize(radius))
                            .build()
            );
        }
    }

    @Override
    public long getItemId(int position) {
        if (position < 0 || position >= songs.size()) {
            return RecyclerView.NO_ID;
        }
        Child song = songs.get(position);
        if (song == null || song.getId() == null) {
            return RecyclerView.NO_ID;
        }
        return song.getId().hashCode();
    }

    @Override
    public void onViewAttachedToWindow(@NonNull ViewHolder holder) {
        super.onViewAttachedToWindow(holder);
        startAnimation(holder);
    }

    @Override
    public int getItemCount() {
        return songs.size();
    }

    public void setItems(List<Child> songs) {
        List<Child> next = songs == null ? Collections.emptyList() : songs;
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new SongDiffCallback(this.songs, next));
        this.songs = next;
        diffResult.dispatchUpdatesTo(this);
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        ItemHomeDiscoverSongBinding item;

        ViewHolder(ItemHomeDiscoverSongBinding item) {
            super(item.getRoot());

            this.item = item;

            itemView.setOnClickListener(v -> onClick());
        }

        public void onClick() {
            Bundle bundle = new Bundle();
            bundle.putParcelable(Constants.TRACK_OBJECT, songs.get(getBindingAdapterPosition()));
            bundle.putBoolean(Constants.MEDIA_MIX, true);

            click.onMediaClick(bundle);
        }
    }

    private void startAnimation(ViewHolder holder) {
        holder.item.discoverSongCoverImageView.animate()
                .setDuration(20000)
                .setStartDelay(10)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .scaleX(1.4f)
                .scaleY(1.4f)
                .start();
    }

    private static class SongDiffCallback extends DiffUtil.Callback {
        private final List<Child> oldList;
        private final List<Child> newList;

        SongDiffCallback(List<Child> oldList, List<Child> newList) {
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
            Child oldItem = oldList.get(oldItemPosition);
            Child newItem = newList.get(newItemPosition);
            if (oldItem == null || newItem == null) {
                return false;
            }
            return Objects.equals(oldItem.getId(), newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            Child oldItem = oldList.get(oldItemPosition);
            Child newItem = newList.get(newItemPosition);
            if (oldItem == null || newItem == null) {
                return false;
            }
            return Objects.equals(oldItem.getTitle(), newItem.getTitle())
                    && Objects.equals(oldItem.getAlbum(), newItem.getAlbum())
                    && Objects.equals(oldItem.getCoverArtId(), newItem.getCoverArtId());
        }
    }
}
