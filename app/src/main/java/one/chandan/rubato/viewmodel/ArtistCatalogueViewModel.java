package one.chandan.rubato.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import one.chandan.rubato.model.ArtistPlayStat;
import one.chandan.rubato.repository.ChronologyRepository;
import one.chandan.rubato.repository.LibraryRepository;
import one.chandan.rubato.subsonic.models.ArtistID3;
import one.chandan.rubato.util.AppExecutors;
import one.chandan.rubato.util.Preferences;

import java.util.ArrayList;
import java.util.List;

public class ArtistCatalogueViewModel extends AndroidViewModel {
    private final MutableLiveData<List<ArtistID3>> artistList = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<ArtistPlayStat>> artistStats = new MutableLiveData<>(new ArrayList<>());
    private final LibraryRepository libraryRepository = new LibraryRepository();
    private final ChronologyRepository chronologyRepository = new ChronologyRepository();

    public ArtistCatalogueViewModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<List<ArtistID3>> getArtistList() {
        return artistList;
    }

    public LiveData<List<ArtistPlayStat>> getArtistStats() {
        return artistStats;
    }

    public void loadArtists() {
        libraryRepository.loadArtistsLegacy(items -> artistList.postValue(items != null ? new ArrayList<>(items) : new ArrayList<>()));
        AppExecutors.io().execute(() -> {
            List<ArtistPlayStat> stats = chronologyRepository.getArtistStats(Preferences.getServerId());
            artistStats.postValue(stats != null ? stats : new ArrayList<>());
        });
    }
}
