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

public class GenreCatalogueViewModel extends AndroidViewModel {
    private final LibraryRepository libraryRepository;
    private final MutableLiveData<List<Genre>> genres = new MutableLiveData<>(Collections.emptyList());

    public GenreCatalogueViewModel(@NonNull Application application) {
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
}
