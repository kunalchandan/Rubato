package one.chandan.rubato.ui.adapter;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import one.chandan.rubato.R;
import one.chandan.rubato.databinding.ItemHorizontalShareBinding;
import one.chandan.rubato.glide.CustomGlideRequest;
import one.chandan.rubato.interfaces.ClickCallback;
import one.chandan.rubato.subsonic.models.Share;
import one.chandan.rubato.util.Constants;
import one.chandan.rubato.util.MusicUtil;
import one.chandan.rubato.util.UIUtil;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ShareHorizontalAdapter extends RecyclerView.Adapter<ShareHorizontalAdapter.ViewHolder> {
    private final ClickCallback click;

    private List<Share> shares;

    public ShareHorizontalAdapter(ClickCallback click) {
        this.click = click;
        this.shares = Collections.emptyList();
        setHasStableIds(true);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemHorizontalShareBinding view = ItemHorizontalShareBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Share share = shares.get(position);

        holder.item.shareTitleTextView.setText(share.getDescription());
        holder.item.shareSubtitleTextView.setText(holder.itemView.getContext().getString(R.string.share_subtitle_item, UIUtil.getReadableDate(share.getExpires())));

        if (share.getEntries() != null && !share.getEntries().isEmpty()) CustomGlideRequest.Builder
                .from(holder.itemView.getContext(), share.getEntries().get(0).getCoverArtId(), CustomGlideRequest.ResourceType.Album)
                .build()
                .into(holder.item.shareCoverImageView);
    }

    @Override
    public long getItemId(int position) {
        if (position < 0 || position >= shares.size()) {
            return RecyclerView.NO_ID;
        }
        Share share = shares.get(position);
        if (share == null) {
            return RecyclerView.NO_ID;
        }
        if (share.getId() != null) {
            return share.getId().hashCode();
        }
        return (share.getUrl() == null ? "" : share.getUrl()).hashCode();
    }

    @Override
    public int getItemCount() {
        return shares.size();
    }

    public void setItems(List<Share> shares) {
        List<Share> next = shares == null ? Collections.emptyList() : shares;
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new ShareDiffCallback(this.shares, next));
        this.shares = next;
        diffResult.dispatchUpdatesTo(this);
    }

    public Share getItem(int id) {
        return shares.get(id);
    }

    private static class ShareDiffCallback extends DiffUtil.Callback {
        private final List<Share> oldList;
        private final List<Share> newList;

        ShareDiffCallback(List<Share> oldList, List<Share> newList) {
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
            Share oldItem = oldList.get(oldItemPosition);
            Share newItem = newList.get(newItemPosition);
            if (oldItem == null || newItem == null) {
                return false;
            }
            return Objects.equals(oldItem.getId(), newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            Share oldItem = oldList.get(oldItemPosition);
            Share newItem = newList.get(newItemPosition);
            if (oldItem == null || newItem == null) {
                return false;
            }
            String oldCover = oldItem.getEntries() != null && !oldItem.getEntries().isEmpty()
                    ? oldItem.getEntries().get(0).getCoverArtId()
                    : null;
            String newCover = newItem.getEntries() != null && !newItem.getEntries().isEmpty()
                    ? newItem.getEntries().get(0).getCoverArtId()
                    : null;
            return Objects.equals(oldItem.getDescription(), newItem.getDescription())
                    && Objects.equals(oldItem.getExpires(), newItem.getExpires())
                    && Objects.equals(oldCover, newCover);
        }
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        ItemHorizontalShareBinding item;

        ViewHolder(ItemHorizontalShareBinding item) {
            super(item.getRoot());

            this.item = item;

            item.shareTitleTextView.setSelected(true);
            item.shareSubtitleTextView.setSelected(true);

            itemView.setOnClickListener(v -> onClick());
            itemView.setOnLongClickListener(v -> onLongClick());

            item.shareButton.setOnClickListener(v -> onLongClick());
        }

        private void onClick() {
            Bundle bundle = new Bundle();
            bundle.putParcelable(Constants.SHARE_OBJECT, shares.get(getBindingAdapterPosition()));

            click.onShareClick(bundle);
        }

        private boolean onLongClick() {
            Bundle bundle = new Bundle();
            bundle.putParcelable(Constants.SHARE_OBJECT, shares.get(getBindingAdapterPosition()));

            click.onShareLongClick(bundle);

            return true;
        }
    }
}
