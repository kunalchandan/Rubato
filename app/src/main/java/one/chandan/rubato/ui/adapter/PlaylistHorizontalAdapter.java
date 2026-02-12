package one.chandan.rubato.ui.adapter;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;

import one.chandan.rubato.R;
import one.chandan.rubato.databinding.ItemHorizontalPlaylistBinding;
import one.chandan.rubato.glide.CustomGlideRequest;
import one.chandan.rubato.interfaces.ClickCallback;
import one.chandan.rubato.repository.CacheRepository;
import one.chandan.rubato.subsonic.models.Child;
import one.chandan.rubato.subsonic.models.Playlist;
import one.chandan.rubato.util.Constants;
import one.chandan.rubato.util.MusicUtil;
import one.chandan.rubato.util.OfflinePolicy;
import one.chandan.rubato.util.PlaylistCoverCache;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.lang.reflect.Type;

public class PlaylistHorizontalAdapter extends RecyclerView.Adapter<PlaylistHorizontalAdapter.ViewHolder> implements Filterable {
    private final ClickCallback click;
    private final boolean enableItemLongPress;
    private final CacheRepository cacheRepository = new CacheRepository();

    private List<Playlist> playlists;
    private List<Playlist> playlistsFull;

    private final Filter filtering = new Filter() {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            List<Playlist> filteredList = new ArrayList<>();

            if (constraint == null || constraint.length() == 0) {
                filteredList.addAll(playlistsFull);
            } else {
                String filterPattern = constraint.toString().toLowerCase().trim();

                for (Playlist item : playlistsFull) {
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
            List<Playlist> next = results.values == null ? Collections.emptyList() : (List<Playlist>) results.values;
            DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new PlaylistDiffCallback(playlists, next));
            playlists = new ArrayList<>(next);
            diffResult.dispatchUpdatesTo(PlaylistHorizontalAdapter.this);
        }
    };

    public PlaylistHorizontalAdapter(ClickCallback click) {
        this(click, true);
    }

    public PlaylistHorizontalAdapter(ClickCallback click, boolean enableItemLongPress) {
        this.click = click;
        this.enableItemLongPress = enableItemLongPress;
        this.playlists = Collections.emptyList();
        setHasStableIds(true);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemHorizontalPlaylistBinding view = ItemHorizontalPlaylistBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Playlist playlist = playlists.get(position);

        holder.item.playlistTitleTextView.setText(playlist.getName());
        holder.item.playlistSubtitleTextView.setText(holder.itemView.getContext().getString(R.string.playlist_counted_tracks, playlist.getSongCount(), MusicUtil.getReadableDurationString(playlist.getDuration(), false)));
        holder.item.playlistCoverImageView.setTag(playlist.getId());

        Drawable cachedCover = PlaylistCoverCache.load(holder.itemView.getContext(), playlist.getId());
        boolean hasCoverArt = playlist.getCoverArtId() != null && !playlist.getCoverArtId().isEmpty();
        boolean useCachedOnly = OfflinePolicy.isOffline() || !hasCoverArt;

        if (cachedCover != null && useCachedOnly) {
            holder.item.playlistCoverImageView.setImageDrawable(cachedCover);
            return;
        }

        if (!hasCoverArt && cachedCover == null && playlist.getId() != null) {
            requestCompositeCover(holder, playlist);
        }

        RequestOptions cachedFallback = cachedCover == null ? null : new RequestOptions()
                .placeholder(cachedCover)
                .error(cachedCover)
                .fallback(cachedCover);

        RequestBuilder<Drawable> request = CustomGlideRequest.Builder
                .from(holder.itemView.getContext(), playlist.getCoverArtId(), CustomGlideRequest.ResourceType.Playlist)
                .build();

        if (cachedFallback != null) {
            request = request.apply(cachedFallback);
        }

        request.listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                        PlaylistCoverCache.save(holder.itemView.getContext(), playlist.getId(), resource);
                        return false;
                    }
                })
                .into(holder.item.playlistCoverImageView);
    }

    @Override
    public long getItemId(int position) {
        if (position < 0 || position >= playlists.size()) {
            return RecyclerView.NO_ID;
        }
        Playlist playlist = playlists.get(position);
        if (playlist == null) {
            return RecyclerView.NO_ID;
        }
        if (playlist.getId() != null) {
            return playlist.getId().hashCode();
        }
        return (playlist.getName() == null ? "" : playlist.getName()).hashCode();
    }

    @Override
    public int getItemCount() {
        return playlists.size();
    }

    public Playlist getItem(int id) {
        return playlists.get(id);
    }

    public List<Playlist> getItems() {
        return playlists;
    }

    public void swapItems(int fromPosition, int toPosition) {
        if (playlists == null || playlists.isEmpty()) return;
        if (fromPosition < 0 || toPosition < 0 || fromPosition >= playlists.size() || toPosition >= playlists.size()) return;

        Collections.swap(playlists, fromPosition, toPosition);
        if (playlistsFull != null && playlistsFull.size() == playlists.size()) {
            Collections.swap(playlistsFull, fromPosition, toPosition);
        }

        notifyItemMoved(fromPosition, toPosition);
    }

    public void setItems(List<Playlist> playlists) {
        List<Playlist> next = playlists == null ? new ArrayList<>() : new ArrayList<>(playlists);
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new PlaylistDiffCallback(this.playlists, next));
        this.playlists = next;
        this.playlistsFull = new ArrayList<>(this.playlists);
        diffResult.dispatchUpdatesTo(this);
    }

    @Override
    public Filter getFilter() {
        return filtering;
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        ItemHorizontalPlaylistBinding item;

        ViewHolder(ItemHorizontalPlaylistBinding item) {
            super(item.getRoot());

            this.item = item;
            item.playlistTitleTextView.setSelected(true);

            itemView.setOnClickListener(v -> onClick());
            if (enableItemLongPress) {
                itemView.setOnLongClickListener(v -> onLongClick());
            }

            item.playlistMoreButton.setOnClickListener(v -> onLongClick());
        }

        public void onClick() {
            Bundle bundle = new Bundle();
            bundle.putParcelable(Constants.PLAYLIST_OBJECT, playlists.get(getBindingAdapterPosition()));

            click.onPlaylistClick(bundle);
        }

        public boolean onLongClick() {
            Bundle bundle = new Bundle();
            bundle.putParcelable(Constants.PLAYLIST_OBJECT, playlists.get(getBindingAdapterPosition()));

            click.onPlaylistLongClick(bundle);

            return true;
        }
    }

    public void sort(String order) {
        switch (order) {
            case Constants.PLAYLIST_ORDER_BY_NAME:
                playlists.sort(Comparator.comparing(Playlist::getName));
                break;
            case Constants.PLAYLIST_ORDER_BY_RANDOM:
                Collections.shuffle(playlists);
                break;
        }

        notifyDataSetChanged();
    }

    private void requestCompositeCover(ViewHolder holder, Playlist playlist) {
        String playlistId = playlist.getId();
        if (playlistId == null || playlistId.isEmpty()) return;
        if (PlaylistCoverCache.exists(holder.itemView.getContext(), playlistId)) return;
        Type type = new TypeToken<List<Child>>() {}.getType();
        cacheRepository.loadOrNull("playlist_songs_" + playlistId, type, new CacheRepository.CacheResult<List<Child>>() {
            @Override
            public void onLoaded(List<Child> songs) {
                if (songs == null || songs.isEmpty()) return;
                List<String> coverIds = new ArrayList<>();
                for (Child song : songs) {
                    if (song == null) continue;
                    String coverArtId = song.getCoverArtId();
                    if (coverArtId == null || coverArtId.isEmpty()) continue;
                    if (!coverIds.contains(coverArtId)) {
                        coverIds.add(coverArtId);
                    }
                    if (coverIds.size() >= 4) break;
                }
                if (coverIds.isEmpty()) return;
                PlaylistCoverCache.requestComposite(holder.itemView.getContext(), playlistId, coverIds, drawable -> {
                    Object tag = holder.item.playlistCoverImageView.getTag();
                    if (tag != null && tag.equals(playlistId)) {
                        holder.item.playlistCoverImageView.setImageDrawable(drawable);
                    }
                });
            }
        });
    }

    private static class PlaylistDiffCallback extends DiffUtil.Callback {
        private final List<Playlist> oldList;
        private final List<Playlist> newList;

        PlaylistDiffCallback(List<Playlist> oldList, List<Playlist> newList) {
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
            Playlist oldItem = oldList.get(oldItemPosition);
            Playlist newItem = newList.get(newItemPosition);
            if (oldItem == null || newItem == null) {
                return false;
            }
            return Objects.equals(oldItem.getId(), newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            Playlist oldItem = oldList.get(oldItemPosition);
            Playlist newItem = newList.get(newItemPosition);
            if (oldItem == null || newItem == null) {
                return false;
            }
            return Objects.equals(oldItem.getName(), newItem.getName())
                    && Objects.equals(oldItem.getCoverArtId(), newItem.getCoverArtId())
                    && oldItem.getSongCount() == newItem.getSongCount()
                    && oldItem.getDuration() == newItem.getDuration();
        }
    }
}
