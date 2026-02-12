package one.chandan.rubato.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import one.chandan.rubato.github.models.LatestRelease;
import one.chandan.rubato.repository.QueueRepository;
import one.chandan.rubato.repository.SystemRepository;
import one.chandan.rubato.subsonic.models.OpenSubsonicExtension;
import one.chandan.rubato.subsonic.models.SubsonicResponse;

import java.util.List;

public class MainViewModel extends AndroidViewModel {
    private static final String TAG = "SearchViewModel";

    private final SystemRepository systemRepository;

    public MainViewModel(@NonNull Application application) {
        super(application);

        systemRepository = new SystemRepository();
    }

    public boolean isQueueLoaded() {
        QueueRepository queueRepository = new QueueRepository();
        return queueRepository.count() != 0;
    }

    public LiveData<SubsonicResponse> ping() {
        return systemRepository.ping();
    }

    public LiveData<List<OpenSubsonicExtension>> getOpenSubsonicExtensions() {
        return systemRepository.getOpenSubsonicExtensions();
    }

    public LiveData<LatestRelease> checkRubatoUpdate() {
        return systemRepository.checkRubatoUpdate();
    }
}
