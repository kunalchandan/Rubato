package one.chandan.rubato.ui.adapter;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import one.chandan.rubato.databinding.ItemLibraryCatalogueGenreBinding;
import one.chandan.rubato.interfaces.ClickCallback;
import one.chandan.rubato.subsonic.models.Genre;
import one.chandan.rubato.util.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class GenreCatalogueAdapter extends RecyclerView.Adapter<GenreCatalogueAdapter.ViewHolder> implements Filterable {
    private final ClickCallback click;

    private final Filter filtering = new Filter() {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            List<Genre> filteredList = new ArrayList<>();

            if (constraint == null || constraint.length() == 0) {
                filteredList.addAll(genresFull);
            } else {
                String filterPattern = constraint.toString().toLowerCase().trim();

                for (Genre item : genresFull) {
                    if (item == null) {
                        continue;
                    }
                    String name = item.getGenre();
                    if (name != null && name.toLowerCase().contains(filterPattern)) {
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
            genres.clear();
            if (results != null && results.values instanceof List) {
                genres.addAll((List) results.values);
            }
            notifyDataSetChanged();
        }
    };

    private List<Genre> genres;
    private List<Genre> genresFull;

    public GenreCatalogueAdapter(ClickCallback click) {
        this.click = click;
        this.genres = new ArrayList<>();
        this.genresFull = new ArrayList<>();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemLibraryCatalogueGenreBinding view = ItemLibraryCatalogueGenreBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        if (position < 0 || position >= genres.size()) {
            return;
        }
        Genre genre = genres.get(position);
        if (genre == null) {
            holder.item.genreLabel.setText("");
            return;
        }
        String name = genre.getGenre();
        holder.item.genreLabel.setText(name == null ? "" : name);
    }

    @Override
    public int getItemCount() {
        return genres.size();
    }

    public Genre getItem(int position) {
        return genres.get(position);
    }

    public void setItems(List<Genre> genres) {
        List<Genre> safe = genres != null ? genres : Collections.emptyList();
        this.genres = new ArrayList<>(safe);
        this.genresFull = new ArrayList<>(safe);
        notifyDataSetChanged();
    }

    @Override
    public Filter getFilter() {
        return filtering;
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        ItemLibraryCatalogueGenreBinding item;

        ViewHolder(ItemLibraryCatalogueGenreBinding item) {
            super(item.getRoot());

            this.item = item;

            itemView.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION || position >= genres.size()) {
                    return;
                }
                Bundle bundle = new Bundle();
                bundle.putString(Constants.MEDIA_BY_GENRE, Constants.MEDIA_BY_GENRE);
                bundle.putParcelable(Constants.GENRE_OBJECT, genres.get(position));

                click.onGenreClick(bundle);
            });
        }
    }

    public void sort(String order) {
        switch (order) {
            case Constants.GENRE_ORDER_BY_NAME:
                genres.sort(Comparator.comparing(genre -> genre.getGenre() == null ? "" : genre.getGenre()));
                if (genresFull != null) {
                    genresFull.sort(Comparator.comparing(genre -> genre.getGenre() == null ? "" : genre.getGenre()));
                }
                break;
            case Constants.GENRE_ORDER_BY_RANDOM:
                Collections.shuffle(genres);
                if (genresFull != null) {
                    Collections.shuffle(genresFull);
                }
                break;
            case Constants.GENRE_ORDER_BY_MOST_SONGS:
                genres.sort(Comparator.comparingInt((Genre genre) -> genre == null ? 0 : genre.getSongCount()).reversed());
                if (genresFull != null) {
                    genresFull.sort(Comparator.comparingInt((Genre genre) -> genre == null ? 0 : genre.getSongCount()).reversed());
                }
                break;
        }

        notifyDataSetChanged();
    }
}
