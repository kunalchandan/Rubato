package one.chandan.rubato.ui.adapter;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import one.chandan.rubato.databinding.ItemLibraryCatalogueArtistBinding;
import one.chandan.rubato.glide.CustomGlideRequest;
import one.chandan.rubato.interfaces.ClickCallback;
import one.chandan.rubato.model.ArtistPlayStat;
import one.chandan.rubato.subsonic.models.ArtistID3;
import one.chandan.rubato.util.Constants;
import one.chandan.rubato.util.MusicUtil;
import one.chandan.rubato.util.SearchIndexUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ArtistCatalogueAdapter extends RecyclerView.Adapter<ArtistCatalogueAdapter.ViewHolder> implements Filterable {
    private final ClickCallback click;
    private Map<String, ArtistPlayStat> statsById = Collections.emptyMap();
    private Map<String, ArtistPlayStat> statsByName = Collections.emptyMap();

    private final Filter filtering = new Filter() {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            List<ArtistID3> filteredList = new ArrayList<>();

            if (constraint == null || constraint.length() == 0) {
                filteredList.addAll(artistFull);
            } else {
                String filterPattern = constraint.toString().toLowerCase().trim();

                for (ArtistID3 item : artistFull) {
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
            artists.clear();
            if (results.count > 0) artists.addAll((List) results.values);
            notifyDataSetChanged();
        }
    };

    private List<ArtistID3> artists;
    private List<ArtistID3> artistFull;

    public ArtistCatalogueAdapter(ClickCallback click) {
        this.click = click;
        this.artists = Collections.emptyList();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemLibraryCatalogueArtistBinding view = ItemLibraryCatalogueArtistBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        ArtistID3 artist = artists.get(position);

        holder.item.artistNameLabel.setText(artist.getName());

        CustomGlideRequest.Builder
                .from(holder.itemView.getContext(), artist.getCoverArtId(), CustomGlideRequest.ResourceType.Artist)
                .build()
                .into(holder.item.artistCatalogueCoverImageView);
    }

    @Override
    public int getItemCount() {
        return artists.size();
    }

    public ArtistID3 getItem(int position) {
        return artists.get(position);
    }

    public void setItems(List<ArtistID3> artists) {
        this.artists = artists;
        this.artistFull = new ArrayList<>(artists);
        notifyDataSetChanged();
    }

    public void setArtistStats(List<ArtistPlayStat> stats) {
        if (stats == null || stats.isEmpty()) {
            statsById = Collections.emptyMap();
            statsByName = Collections.emptyMap();
            return;
        }
        Map<String, ArtistPlayStat> idMap = new HashMap<>();
        Map<String, ArtistPlayStat> nameMap = new HashMap<>();
        for (ArtistPlayStat stat : stats) {
            if (stat == null) continue;
            if (stat.artistId != null && !stat.artistId.isEmpty()) {
                idMap.put(stat.artistId, stat);
            }
            if (stat.artistName != null && !stat.artistName.isEmpty()) {
                nameMap.put(SearchIndexUtil.normalize(stat.artistName), stat);
            }
        }
        statsById = idMap;
        statsByName = nameMap;
    }

    @Override
    public int getItemViewType(int position) {
        return position;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public Filter getFilter() {
        return filtering;
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        ItemLibraryCatalogueArtistBinding item;

        ViewHolder(ItemLibraryCatalogueArtistBinding item) {
            super(item.getRoot());

            this.item = item;

            item.artistNameLabel.setSelected(true);

            itemView.setOnClickListener(v -> onClick());
            itemView.setOnLongClickListener(v -> onLongClick());
        }

        public void onClick() {
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
            case Constants.ARTIST_ORDER_BY_RANDOM:
                Collections.shuffle(artists);
                break;
            case Constants.ARTIST_ORDER_BY_MOST_PLAYED:
                artists.sort((a, b) -> {
                    ArtistPlayStat sa = resolveStat(a);
                    ArtistPlayStat sb = resolveStat(b);
                    int countA = sa != null ? sa.playCount : 0;
                    int countB = sb != null ? sb.playCount : 0;
                    if (countA != countB) return countB - countA;
                    String nameA = a.getName() != null ? a.getName() : "";
                    String nameB = b.getName() != null ? b.getName() : "";
                    return nameA.compareToIgnoreCase(nameB);
                });
                break;
            case Constants.ARTIST_ORDER_BY_RECENTLY_PLAYED:
                artists.sort((a, b) -> {
                    ArtistPlayStat sa = resolveStat(a);
                    ArtistPlayStat sb = resolveStat(b);
                    long tsA = sa != null ? sa.lastPlayed : 0L;
                    long tsB = sb != null ? sb.lastPlayed : 0L;
                    if (tsA != tsB) return Long.compare(tsB, tsA);
                    String nameA = a.getName() != null ? a.getName() : "";
                    String nameB = b.getName() != null ? b.getName() : "";
                    return nameA.compareToIgnoreCase(nameB);
                });
                break;
        }

        notifyDataSetChanged();
    }

    private ArtistPlayStat resolveStat(ArtistID3 artist) {
        if (artist == null) return null;
        String id = artist.getId();
        if (id != null && statsById.containsKey(id)) {
            return statsById.get(id);
        }
        String name = artist.getName();
        if (name != null) {
            return statsByName.get(SearchIndexUtil.normalize(name));
        }
        return null;
    }
}
