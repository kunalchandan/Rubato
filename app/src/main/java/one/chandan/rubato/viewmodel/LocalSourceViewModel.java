package one.chandan.rubato.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import one.chandan.rubato.model.LocalSource;
import one.chandan.rubato.repository.LocalSourceRepository;

import java.util.List;

public class LocalSourceViewModel extends AndroidViewModel {
    private final LocalSourceRepository localSourceRepository;

    public LocalSourceViewModel(@NonNull Application application) {
        super(application);
        localSourceRepository = new LocalSourceRepository();
    }

    public LiveData<List<LocalSource>> getSources() {
        return localSourceRepository.getLiveSources();
    }

    public void addSource(LocalSource source) {
        localSourceRepository.insert(source);
    }

    public void removeSource(LocalSource source) {
        localSourceRepository.delete(source);
    }
}
