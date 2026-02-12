package one.chandan.rubato.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import one.chandan.rubato.model.RecentSearch;
import one.chandan.rubato.model.SearchSuggestion;
import one.chandan.rubato.repository.LibraryRepository;
import one.chandan.rubato.repository.SearchRepository;
import one.chandan.rubato.subsonic.models.Playlist;
import one.chandan.rubato.subsonic.models.SearchResult2;
import one.chandan.rubato.subsonic.models.SearchResult3;

import java.util.ArrayList;
import java.util.List;

public class SearchViewModel extends AndroidViewModel {
    private static final String TAG = "SearchViewModel";

    private String query = "";

    private final SearchRepository searchingRepository;
    private final LibraryRepository libraryRepository;

    public SearchViewModel(@NonNull Application application) {
        super(application);

        searchingRepository = new SearchRepository();
        libraryRepository = new LibraryRepository();
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;

        if (!query.isEmpty()) {
            insertNewSearch(query);
        }
    }

    public LiveData<SearchResult2> search2(String title) {
        return searchingRepository.search2(title);
    }

    public LiveData<SearchResult3> search3(String title) {
        return libraryRepository.searchLegacy(title);
    }

    public LiveData<List<Playlist>> searchPlaylists(String title) {
        return libraryRepository.searchPlaylistsLegacy(title);
    }

    public void insertNewSearch(String search) {
        searchingRepository.insert(new RecentSearch(search));
    }

    public void deleteRecentSearch(String search) {
        searchingRepository.delete(new RecentSearch(search));
    }

    public LiveData<List<SearchSuggestion>> getSearchSuggestion(String query) {
        return searchingRepository.getSuggestions(query);
    }

    public List<String> getRecentSearchSuggestion() {
        ArrayList<String> suggestions = new ArrayList<>();
        suggestions.addAll(searchingRepository.getRecentSearchSuggestion());

        return suggestions;
    }
}
