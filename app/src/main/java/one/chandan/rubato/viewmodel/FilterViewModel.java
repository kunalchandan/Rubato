package one.chandan.rubato.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import androidx.lifecycle.MutableLiveData;
import one.chandan.rubato.repository.LibraryRepository;
import one.chandan.rubato.subsonic.models.Genre;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FilterViewModel extends AndroidViewModel {
    private final LibraryRepository libraryRepository;
    private final MutableLiveData<List<Genre>> genres = new MutableLiveData<>(Collections.emptyList());

    private final ArrayList<String> selectedFiltersID = new ArrayList<>();
    private final ArrayList<String> selectedFilters = new ArrayList<>();

    public FilterViewModel(@NonNull Application application) {
        super(application);

        libraryRepository = new LibraryRepository();
    }

    public LiveData<List<Genre>> getGenreList() {
        if (genres.getValue() == null || genres.getValue().isEmpty()) {
            libraryRepository.loadGenresLegacy(items -> genres.postValue(items != null ? new ArrayList<>(items) : new ArrayList<>()));
        }
        return genres;
    }

    public void refreshGenreList() {
        libraryRepository.loadGenresLegacy(items -> genres.postValue(items != null ? new ArrayList<>(items) : new ArrayList<>()));
    }

    public void addFilter(String filterID, String filterName) {
        selectedFiltersID.add(filterID);
        selectedFilters.add(filterName);
    }

    public void removeFilter(String filterID, String filterName) {
        selectedFiltersID.remove(filterID);
        selectedFilters.remove(filterName);
    }

    public ArrayList<String> getFilters() {
        return selectedFiltersID;
    }

    public ArrayList<String> getFilterNames() {
        return selectedFilters;
    }
}
