package one.chandan.rubato.ui.adapter;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.media3.common.util.UnstableApi;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import one.chandan.rubato.R;
import one.chandan.rubato.databinding.ItemHorizontalDownloadBinding;
import one.chandan.rubato.glide.CustomGlideRequest;
import one.chandan.rubato.interfaces.ClickCallback;
import one.chandan.rubato.subsonic.models.Child;
import one.chandan.rubato.util.Constants;
import one.chandan.rubato.util.MusicUtil;
import one.chandan.rubato.util.Util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@UnstableApi
public class DownloadHorizontalAdapter extends RecyclerView.Adapter<DownloadHorizontalAdapter.ViewHolder> {
    private final ClickCallback click;

    private String view;
    private String filterKey;
    private String filterValue;

    private List<Child> songs;
    private List<Child> shuffling;
    private List<Child> grouped;

    public DownloadHorizontalAdapter(ClickCallback click) {
        this.click = click;
        this.view = Constants.DOWNLOAD_TYPE_TRACK;
        this.songs = Collections.emptyList();
        this.grouped = Collections.emptyList();
        setHasStableIds(true);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemHorizontalDownloadBinding view = ItemHorizontalDownloadBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        switch (view) {
            case Constants.DOWNLOAD_TYPE_TRACK:
                initTrackLayout(holder, position);
                break;
            case Constants.DOWNLOAD_TYPE_ALBUM:
                initAlbumLayout(holder, position);
                break;
            case Constants.DOWNLOAD_TYPE_ARTIST:
                initArtistLayout(holder, position);
                break;
            case Constants.DOWNLOAD_TYPE_GENRE:
                initGenreLayout(holder, position);
                break;
            case Constants.DOWNLOAD_TYPE_YEAR:
                initYearLayout(holder, position);
                break;
        }
    }

    @Override
    public int getItemCount() {
        return grouped.size();
    }

    public void setItems(String view, String filterKey, String filterValue, List<Child> songs) {
        String nextView = filterValue != null ? view : filterKey;
        boolean viewChanged = !Objects.equals(this.view, nextView)
                || !Objects.equals(this.filterKey, filterKey)
                || !Objects.equals(this.filterValue, filterValue);

        this.view = nextView;
        this.filterKey = filterKey;
        this.filterValue = filterValue;

        List<Child> nextSongs = songs != null ? songs : Collections.emptyList();
        this.songs = nextSongs;
        List<Child> nextGrouped = groupSong(nextSongs);
        this.shuffling = shufflingSong(new ArrayList<>(nextSongs));

        if (viewChanged || !Constants.DOWNLOAD_TYPE_TRACK.equals(this.view)) {
            this.grouped = nextGrouped;
            notifyDataSetChanged();
            return;
        }

        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DownloadDiffCallback(this.grouped, nextGrouped));
        this.grouped = nextGrouped;
        diffResult.dispatchUpdatesTo(this);
    }

    public Child getItem(int id) {
        return grouped.get(id);
    }

    public List<Child> getShuffling() {
        return shuffling;
    }

    public List<Child> getOrderedPlaybackList() {
        if (grouped == null || grouped.isEmpty()) {
            return Collections.emptyList();
        }
        if (Constants.DOWNLOAD_TYPE_TRACK.equals(view)) {
            return new ArrayList<>(grouped);
        }
        List<Child> ordered = new ArrayList<>();
        for (Child group : grouped) {
            if (group == null) continue;
            switch (view) {
                case Constants.DOWNLOAD_TYPE_ALBUM:
                    appendMatching(ordered, group.getAlbumId(), Constants.DOWNLOAD_TYPE_ALBUM);
                    break;
                case Constants.DOWNLOAD_TYPE_ARTIST:
                    appendMatching(ordered, group.getArtistId(), Constants.DOWNLOAD_TYPE_ARTIST);
                    break;
                case Constants.DOWNLOAD_TYPE_GENRE:
                    appendMatching(ordered, group.getGenre(), Constants.DOWNLOAD_TYPE_GENRE);
                    break;
                case Constants.DOWNLOAD_TYPE_YEAR:
                    appendMatching(ordered, group.getYear() != null ? group.getYear().toString() : null, Constants.DOWNLOAD_TYPE_YEAR);
                    break;
                default:
                    break;
            }
        }
        return ordered;
    }

    @Override
    public int getItemViewType(int position) {
        return 0;
    }

    @Override
    public long getItemId(int position) {
        if (position < 0 || position >= grouped.size()) return RecyclerView.NO_ID;
        Child item = grouped.get(position);
        String key;
        switch (view) {
            case Constants.DOWNLOAD_TYPE_TRACK:
                key = item.getId();
                break;
            case Constants.DOWNLOAD_TYPE_ALBUM:
                key = item.getAlbumId();
                break;
            case Constants.DOWNLOAD_TYPE_ARTIST:
                key = item.getArtistId();
                break;
            case Constants.DOWNLOAD_TYPE_GENRE:
                key = item.getGenre();
                break;
            case Constants.DOWNLOAD_TYPE_YEAR:
                key = item.getYear() != null ? item.getYear().toString() : null;
                break;
            default:
                key = null;
                break;
        }

        return key != null ? key.hashCode() : RecyclerView.NO_ID;
    }

    private List<Child> groupSong(List<Child> songs) {
        switch (view) {
            case Constants.DOWNLOAD_TYPE_TRACK:
                return filterSong(filterKey, filterValue, songs.stream().filter(song -> Objects.nonNull(song.getId())).filter(Util.distinctByKey(Child::getId)).collect(Collectors.toList()));
            case Constants.DOWNLOAD_TYPE_ALBUM:
                return filterSong(filterKey, filterValue, songs.stream().filter(song -> Objects.nonNull(song.getAlbumId())).filter(Util.distinctByKey(Child::getAlbumId)).collect(Collectors.toList()));
            case Constants.DOWNLOAD_TYPE_ARTIST:
                return filterSong(filterKey, filterValue, songs.stream().filter(song -> Objects.nonNull(song.getArtistId())).filter(Util.distinctByKey(Child::getArtistId)).collect(Collectors.toList()));
            case Constants.DOWNLOAD_TYPE_GENRE:
                return filterSong(filterKey, filterValue, songs.stream().filter(song -> Objects.nonNull(song.getGenre())).filter(Util.distinctByKey(Child::getGenre)).collect(Collectors.toList()));
            case Constants.DOWNLOAD_TYPE_YEAR:
                return filterSong(filterKey, filterValue, songs.stream().filter(song -> Objects.nonNull(song.getYear())).filter(Util.distinctByKey(Child::getYear)).collect(Collectors.toList()));
        }

        return Collections.emptyList();
    }

    private static final class DownloadDiffCallback extends DiffUtil.Callback {
        private final List<Child> oldList;
        private final List<Child> newList;

        private DownloadDiffCallback(List<Child> oldList, List<Child> newList) {
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
                    && Objects.equals(oldItem.getDuration(), newItem.getDuration());
        }
    }

    private List<Child> filterSong(String filterKey, String filterValue, List<Child> songs) {
        if (filterValue != null) {
            switch (filterKey) {
                case Constants.DOWNLOAD_TYPE_TRACK:
                    return songs.stream().filter(child -> child.getId().equals(filterValue)).collect(Collectors.toList());
                case Constants.DOWNLOAD_TYPE_ALBUM:
                    return songs.stream().filter(child -> Objects.equals(child.getAlbumId(), filterValue)).collect(Collectors.toList());
                case Constants.DOWNLOAD_TYPE_GENRE:
                    return songs.stream().filter(child -> Objects.equals(child.getGenre(), filterValue)).collect(Collectors.toList());
                case Constants.DOWNLOAD_TYPE_YEAR:
                    return songs.stream().filter(child -> Objects.equals(child.getYear(), Integer.valueOf(filterValue))).collect(Collectors.toList());
                case Constants.DOWNLOAD_TYPE_ARTIST:
                    return songs.stream().filter(child -> Objects.equals(child.getArtistId(), filterValue)).collect(Collectors.toList());
            }
        }

        return songs;
    }

    private List<Child> shufflingSong(List<Child> songs) {
        if (filterValue == null) {
            return songs;
        }

        switch (filterKey) {
            case Constants.DOWNLOAD_TYPE_TRACK:
                return songs.stream().filter(child -> child.getId().equals(filterValue)).collect(Collectors.toList());
            case Constants.DOWNLOAD_TYPE_ALBUM:
                return songs.stream().filter(child -> Objects.equals(child.getAlbumId(), filterValue)).collect(Collectors.toList());
            case Constants.DOWNLOAD_TYPE_GENRE:
                return songs.stream().filter(child -> Objects.equals(child.getGenre(), filterValue)).collect(Collectors.toList());
            case Constants.DOWNLOAD_TYPE_YEAR:
                return songs.stream().filter(child -> Objects.equals(child.getYear(), Integer.valueOf(filterValue))).collect(Collectors.toList());
            case Constants.DOWNLOAD_TYPE_ARTIST:
                return songs.stream().filter(child -> Objects.equals(child.getArtistId(), filterValue)).collect(Collectors.toList());
            default:
                return songs;
        }
    }

    private void appendMatching(List<Child> target, String value, String type) {
        if (value == null || target == null) return;
        if (songs == null || songs.isEmpty()) return;
        for (Child song : songs) {
            if (song == null) continue;
            switch (type) {
                case Constants.DOWNLOAD_TYPE_ALBUM:
                    if (value.equals(song.getAlbumId())) {
                        target.add(song);
                    }
                    break;
                case Constants.DOWNLOAD_TYPE_ARTIST:
                    if (value.equals(song.getArtistId())) {
                        target.add(song);
                    }
                    break;
                case Constants.DOWNLOAD_TYPE_GENRE:
                    if (value.equals(song.getGenre())) {
                        target.add(song);
                    }
                    break;
                case Constants.DOWNLOAD_TYPE_YEAR:
                    if (song.getYear() != null && value.equals(song.getYear().toString())) {
                        target.add(song);
                    }
                    break;
                default:
                    break;
            }
        }
    }

    private String countSong(String filterKey, String filterValue, List<Child> songs) {
        if (filterValue != null) {
            switch (filterKey) {
                case Constants.DOWNLOAD_TYPE_TRACK:
                    return String.valueOf(songs.stream().filter(child -> child.getId().equals(filterValue)).count());
                case Constants.DOWNLOAD_TYPE_ALBUM:
                    return String.valueOf(songs.stream().filter(child -> Objects.equals(child.getAlbumId(), filterValue)).count());
                case Constants.DOWNLOAD_TYPE_GENRE:
                    return String.valueOf(songs.stream().filter(child -> Objects.equals(child.getGenre(), filterValue)).count());
                case Constants.DOWNLOAD_TYPE_YEAR:
                    return String.valueOf(songs.stream().filter(child -> Objects.equals(child.getYear(), Integer.valueOf(filterValue))).count());
                case Constants.DOWNLOAD_TYPE_ARTIST:
                    return String.valueOf(songs.stream().filter(child -> Objects.equals(child.getArtistId(), filterValue)).count());
            }
        }

        return "0";
    }

    private void initTrackLayout(ViewHolder holder, int position) {
        Child song = grouped.get(position);
        Drawable placeholder = CustomGlideRequest.getPlaceholderDrawable(holder.itemView.getContext(), CustomGlideRequest.ResourceType.Song);
        holder.item.itemCoverImageView.setImageDrawable(placeholder);

        holder.item.downloadedItemTitleTextView.setText(song.getTitle());
        holder.item.downloadedItemSubtitleTextView.setText(
                holder.itemView.getContext().getString(
                        R.string.song_subtitle_formatter,
                        song.getArtist(),
                        MusicUtil.getReadableDurationString(song.getDuration(), false),
                        ""
                )
        );

        holder.item.downloadedItemPreTextView.setText(song.getAlbum());

        String coverArtId = resolveCoverArtId(song);
        CustomGlideRequest.Builder
                .from(holder.itemView.getContext(), coverArtId, CustomGlideRequest.ResourceType.Song)
                .build()
                .placeholder(placeholder)
                .error(placeholder)
                .fallback(placeholder)
                .into(holder.item.itemCoverImageView);

        holder.item.itemCoverImageView.setVisibility(View.VISIBLE);
        holder.item.downloadedItemMoreButton.setVisibility(View.VISIBLE);
        holder.item.divider.setVisibility(View.VISIBLE);

        if (position > 0 && grouped.get(position - 1) != null && !Objects.equals(grouped.get(position - 1).getAlbum(), grouped.get(position).getAlbum())) {
            holder.item.divider.setPadding(0, (int) holder.itemView.getContext().getResources().getDimension(R.dimen.downloaded_item_padding), 0, 0);
        } else {
            if (position > 0) holder.item.divider.setVisibility(View.GONE);
        }
    }

    private void initAlbumLayout(ViewHolder holder, int position) {
        Child song = grouped.get(position);
        Drawable placeholder = CustomGlideRequest.getPlaceholderDrawable(holder.itemView.getContext(), CustomGlideRequest.ResourceType.Album);
        holder.item.itemCoverImageView.setImageDrawable(placeholder);

        holder.item.downloadedItemTitleTextView.setText(song.getAlbum());
        holder.item.downloadedItemSubtitleTextView.setText(holder.itemView.getContext().getString(R.string.download_item_single_subtitle_formatter, countSong(Constants.DOWNLOAD_TYPE_ALBUM, song.getAlbumId(), songs)));
        holder.item.downloadedItemPreTextView.setText(song.getArtist());

        String coverArtId = resolveCoverArtId(song);
        CustomGlideRequest.Builder
                .from(holder.itemView.getContext(), coverArtId, CustomGlideRequest.ResourceType.Album)
                .build()
                .placeholder(placeholder)
                .error(placeholder)
                .fallback(placeholder)
                .into(holder.item.itemCoverImageView);

        holder.item.itemCoverImageView.setVisibility(View.VISIBLE);
        holder.item.downloadedItemMoreButton.setVisibility(View.VISIBLE);
        holder.item.divider.setVisibility(View.VISIBLE);

        if (position > 0 && grouped.get(position - 1) != null && !Objects.equals(grouped.get(position - 1).getArtist(), grouped.get(position).getArtist())) {
            holder.item.divider.setPadding(0, (int) holder.itemView.getContext().getResources().getDimension(R.dimen.downloaded_item_padding), 0, 0);
        } else {
            if (position > 0) holder.item.divider.setVisibility(View.GONE);
        }
    }

    private void initArtistLayout(ViewHolder holder, int position) {
        Child song = grouped.get(position);
        Drawable placeholder = CustomGlideRequest.getPlaceholderDrawable(holder.itemView.getContext(), CustomGlideRequest.ResourceType.Artist);
        holder.item.itemCoverImageView.setImageDrawable(placeholder);

        holder.item.downloadedItemTitleTextView.setText(song.getArtist());
        holder.item.downloadedItemSubtitleTextView.setText(holder.itemView.getContext().getString(R.string.download_item_single_subtitle_formatter, countSong(Constants.DOWNLOAD_TYPE_ARTIST, song.getArtistId(), songs)));

        String coverArtId = resolveCoverArtId(song);
        CustomGlideRequest.Builder
                .from(holder.itemView.getContext(), coverArtId, CustomGlideRequest.ResourceType.Artist)
                .build()
                .placeholder(placeholder)
                .error(placeholder)
                .fallback(placeholder)
                .into(holder.item.itemCoverImageView);

        holder.item.itemCoverImageView.setVisibility(View.VISIBLE);
        holder.item.downloadedItemMoreButton.setVisibility(View.VISIBLE);
        holder.item.divider.setVisibility(View.GONE);
    }

    private void initGenreLayout(ViewHolder holder, int position) {
        Child song = grouped.get(position);

        holder.item.downloadedItemTitleTextView.setText(song.getGenre());
        holder.item.downloadedItemSubtitleTextView.setText(holder.itemView.getContext().getString(R.string.download_item_single_subtitle_formatter, countSong(Constants.DOWNLOAD_TYPE_GENRE, song.getGenre(), songs)));

        holder.item.itemCoverImageView.setVisibility(View.GONE);
        holder.item.downloadedItemMoreButton.setVisibility(View.VISIBLE);
        holder.item.divider.setVisibility(View.GONE);
    }

    private void initYearLayout(ViewHolder holder, int position) {
        Child song = grouped.get(position);

        holder.item.downloadedItemTitleTextView.setText(String.valueOf(song.getYear()));
        holder.item.downloadedItemSubtitleTextView.setText(holder.itemView.getContext().getString(R.string.download_item_single_subtitle_formatter, countSong(Constants.DOWNLOAD_TYPE_YEAR, song.getYear().toString(), songs)));

        holder.item.itemCoverImageView.setVisibility(View.GONE);
        holder.item.downloadedItemMoreButton.setVisibility(View.VISIBLE);
        holder.item.divider.setVisibility(View.GONE);
    }

    private String resolveCoverArtId(Child item) {
        if (item == null) return null;
        String coverArtId = item.getCoverArtId();
        if (!TextUtils.isEmpty(coverArtId)) return coverArtId;
        if (songs == null || songs.isEmpty()) return null;
        String albumId = item.getAlbumId();
        if (!TextUtils.isEmpty(albumId)) {
            for (Child song : songs) {
                if (song == null) continue;
                if (albumId.equals(song.getAlbumId()) && !TextUtils.isEmpty(song.getCoverArtId())) {
                    return song.getCoverArtId();
                }
            }
        }
        String artistId = item.getArtistId();
        if (!TextUtils.isEmpty(artistId)) {
            for (Child song : songs) {
                if (song == null) continue;
                if (artistId.equals(song.getArtistId()) && !TextUtils.isEmpty(song.getCoverArtId())) {
                    return song.getCoverArtId();
                }
            }
        }
        return null;
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        ItemHorizontalDownloadBinding item;

        ViewHolder(ItemHorizontalDownloadBinding item) {
            super(item.getRoot());

            this.item = item;

            item.downloadedItemTitleTextView.setSelected(true);
            item.downloadedItemSubtitleTextView.setSelected(true);

            itemView.setOnClickListener(v -> onClick());
            itemView.setOnLongClickListener(v -> onLongClick());

            item.downloadedItemMoreButton.setOnClickListener(v -> onLongClick());
        }

        public void onClick() {
            Bundle bundle = new Bundle();

            switch (view) {
                case Constants.DOWNLOAD_TYPE_TRACK:
                    bundle.putParcelableArrayList(Constants.TRACKS_OBJECT, new ArrayList<>(grouped));
                    bundle.putInt(Constants.ITEM_POSITION, getBindingAdapterPosition());
                    click.onMediaClick(bundle);
                    break;
                case Constants.DOWNLOAD_TYPE_ALBUM:
                    bundle.putString(Constants.DOWNLOAD_TYPE_ALBUM, grouped.get(getBindingAdapterPosition()).getAlbumId());
                    click.onAlbumClick(bundle);
                    break;
                case Constants.DOWNLOAD_TYPE_ARTIST:
                    bundle.putString(Constants.DOWNLOAD_TYPE_ARTIST, grouped.get(getBindingAdapterPosition()).getArtistId());
                    click.onArtistClick(bundle);
                    break;
                case Constants.DOWNLOAD_TYPE_GENRE:
                    bundle.putString(Constants.DOWNLOAD_TYPE_GENRE, grouped.get(getBindingAdapterPosition()).getGenre());
                    click.onGenreClick(bundle);
                    break;
                case Constants.DOWNLOAD_TYPE_YEAR:
                    bundle.putString(Constants.DOWNLOAD_TYPE_YEAR, grouped.get(getBindingAdapterPosition()).getYear().toString());
                    click.onYearClick(bundle);
                    break;
            }
        }

        private boolean onLongClick() {
            ArrayList<Child> filteredSongs = new ArrayList<>();

            Bundle bundle = new Bundle();

            switch (view) {
                case Constants.DOWNLOAD_TYPE_TRACK:
                    filteredSongs.add(grouped.get(getBindingAdapterPosition()));
                    break;
                case Constants.DOWNLOAD_TYPE_ALBUM:
                    filteredSongs.addAll(filterSong(Constants.DOWNLOAD_TYPE_ALBUM, grouped.get(getBindingAdapterPosition()).getAlbumId(), songs));
                    break;
                case Constants.DOWNLOAD_TYPE_ARTIST:
                    filteredSongs.addAll(filterSong(Constants.DOWNLOAD_TYPE_ARTIST, grouped.get(getBindingAdapterPosition()).getArtistId(), songs));
                    break;
                case Constants.DOWNLOAD_TYPE_GENRE:
                    filteredSongs.addAll(filterSong(Constants.DOWNLOAD_TYPE_GENRE, grouped.get(getBindingAdapterPosition()).getGenre(), songs));
                    break;
                case Constants.DOWNLOAD_TYPE_YEAR:
                    filteredSongs.addAll(filterSong(Constants.DOWNLOAD_TYPE_YEAR, grouped.get(getBindingAdapterPosition()).getYear().toString(), songs));
                    break;
            }

            if (filteredSongs.isEmpty()) return false;

            bundle.putParcelableArrayList(Constants.DOWNLOAD_GROUP, new ArrayList<>(filteredSongs));
            bundle.putString(Constants.DOWNLOAD_GROUP_TITLE, item.downloadedItemTitleTextView.getText().toString());
            bundle.putString(Constants.DOWNLOAD_GROUP_SUBTITLE, item.downloadedItemSubtitleTextView.getText().toString());
            click.onDownloadGroupLongClick(bundle);

            return true;
        }
    }
}
