package one.chandan.rubato.ui.adapter;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.media3.session.MediaBrowser;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import one.chandan.rubato.R;
import one.chandan.rubato.databinding.ItemPlayerQueueSongBinding;
import one.chandan.rubato.glide.CustomGlideRequest;
import one.chandan.rubato.interfaces.ClickCallback;
import one.chandan.rubato.subsonic.models.Child;
import one.chandan.rubato.util.Constants;
import one.chandan.rubato.util.MusicUtil;
import one.chandan.rubato.util.Preferences;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class PlayerSongQueueAdapter extends RecyclerView.Adapter<PlayerSongQueueAdapter.ViewHolder> {
    private final ClickCallback click;

    private ListenableFuture<MediaBrowser> mediaBrowserListenableFuture;
    private List<Child> songs;
    private int currentIndex = -1;

    public PlayerSongQueueAdapter(ClickCallback click) {
        this.click = click;
        this.songs = Collections.emptyList();
        setHasStableIds(true);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemPlayerQueueSongBinding view = ItemPlayerQueueSongBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Child song = songs.get(position);

        holder.item.queueSongTitleTextView.setText(song.getTitle());
        holder.item.queueSongSubtitleTextView.setText(
                holder.itemView.getContext().getString(
                        R.string.song_subtitle_formatter,
                        song.getArtist(),
                        MusicUtil.getReadableDurationString(song.getDuration(), false),
                        MusicUtil.getReadableAudioQualityString(song)
                )
        );

        CustomGlideRequest.Builder
                .from(holder.itemView.getContext(), song.getCoverArtId(), CustomGlideRequest.ResourceType.Song)
                .build()
                .into(holder.item.queueSongCoverImageView);

        boolean dimmed = currentIndex >= 0 && position < currentIndex;
        float alpha = dimmed ? 0.2f : 1.0f;
        holder.item.queueSongTitleTextView.setAlpha(alpha);
        holder.item.queueSongSubtitleTextView.setAlpha(alpha);
        holder.item.ratingIndicatorImageView.setAlpha(alpha);

        if (Preferences.showItemRating()) {
            if (song.getStarred() == null && song.getUserRating() == null) {
                holder.item.ratingIndicatorImageView.setVisibility(View.GONE);
            }

            holder.item.preferredIcon.setVisibility(song.getStarred() != null ? View.VISIBLE : View.GONE);
            holder.item.ratingBarLayout.setVisibility(song.getUserRating() != null ? View.VISIBLE : View.GONE);

            if (song.getUserRating() != null) {
                holder.item.oneStarIcon.setImageDrawable(AppCompatResources.getDrawable(holder.itemView.getContext(), song.getUserRating() >= 1 ? R.drawable.ic_star : R.drawable.ic_star_outlined));
                holder.item.twoStarIcon.setImageDrawable(AppCompatResources.getDrawable(holder.itemView.getContext(), song.getUserRating() >= 2 ? R.drawable.ic_star : R.drawable.ic_star_outlined));
                holder.item.threeStarIcon.setImageDrawable(AppCompatResources.getDrawable(holder.itemView.getContext(), song.getUserRating() >= 3 ? R.drawable.ic_star : R.drawable.ic_star_outlined));
                holder.item.fourStarIcon.setImageDrawable(AppCompatResources.getDrawable(holder.itemView.getContext(), song.getUserRating() >= 4 ? R.drawable.ic_star : R.drawable.ic_star_outlined));
                holder.item.fiveStarIcon.setImageDrawable(AppCompatResources.getDrawable(holder.itemView.getContext(), song.getUserRating() >= 5 ? R.drawable.ic_star : R.drawable.ic_star_outlined));
            }
        } else {
            holder.item.ratingIndicatorImageView.setVisibility(View.GONE);
        }
    }

    public List<Child> getItems() {
        return this.songs;
    }

    public void setItems(List<Child> songs) {
        List<Child> next = songs != null ? songs : Collections.emptyList();
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new QueueDiffCallback(this.songs, next));
        this.songs = next;
        if (currentIndex >= next.size()) {
            currentIndex = -1;
        }
        diffResult.dispatchUpdatesTo(this);
    }

    @Override
    public int getItemCount() {
        if (songs == null) {
            return 0;
        }
        return songs.size();
    }

    @Override
    public long getItemId(int position) {
        if (position < 0 || position >= songs.size()) {
            return RecyclerView.NO_ID;
        }
        Child song = songs.get(position);
        if (song != null && song.getId() != null) {
            return song.getId().hashCode();
        }
        return position;
    }

    public void setMediaBrowserListenableFuture(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture) {
        this.mediaBrowserListenableFuture = mediaBrowserListenableFuture;
    }

    public Child getItem(int id) {
        return songs.get(id);
    }

    public void setCurrentIndex(int index) {
        if (index == currentIndex) return;
        int oldIndex = currentIndex;
        currentIndex = index;
        if (songs == null || songs.isEmpty()) {
            notifyDataSetChanged();
            return;
        }
        if (oldIndex < 0 || currentIndex < 0) {
            notifyDataSetChanged();
            return;
        }
        int start = Math.min(oldIndex, currentIndex);
        int end = Math.max(oldIndex, currentIndex) - 1;
        if (start <= end) {
            notifyItemRangeChanged(start, end - start + 1);
        }
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        ItemPlayerQueueSongBinding item;

        ViewHolder(ItemPlayerQueueSongBinding item) {
            super(item.getRoot());

            this.item = item;

            item.queueSongTitleTextView.setSelected(true);
            item.queueSongSubtitleTextView.setSelected(true);

            itemView.setOnClickListener(v -> onClick());
        }

        public void onClick() {
            Bundle bundle = new Bundle();
            bundle.putParcelableArrayList(Constants.TRACKS_OBJECT, new ArrayList<>(songs));
            bundle.putInt(Constants.ITEM_POSITION, getBindingAdapterPosition());

            click.onMediaClick(bundle);
        }
    }

    private static final class QueueDiffCallback extends DiffUtil.Callback {
        private final List<Child> oldList;
        private final List<Child> newList;

        private QueueDiffCallback(List<Child> oldList, List<Child> newList) {
            this.oldList = oldList != null ? oldList : Collections.emptyList();
            this.newList = newList != null ? newList : Collections.emptyList();
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
            if (oldItem == null || newItem == null) return false;
            return Objects.equals(oldItem.getId(), newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            Child oldItem = oldList.get(oldItemPosition);
            Child newItem = newList.get(newItemPosition);
            if (oldItem == null || newItem == null) return false;
            return Objects.equals(oldItem.getTitle(), newItem.getTitle())
                    && Objects.equals(oldItem.getArtist(), newItem.getArtist())
                    && Objects.equals(oldItem.getAlbum(), newItem.getAlbum())
                    && Objects.equals(oldItem.getCoverArtId(), newItem.getCoverArtId())
                    && Objects.equals(oldItem.getDuration(), newItem.getDuration())
                    && Objects.equals(oldItem.getUserRating(), newItem.getUserRating())
                    && Objects.equals(oldItem.getStarred(), newItem.getStarred());
        }
    }
}
