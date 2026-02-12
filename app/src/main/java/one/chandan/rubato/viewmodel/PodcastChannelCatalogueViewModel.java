package one.chandan.rubato.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import one.chandan.rubato.repository.PodcastRepository;
import one.chandan.rubato.subsonic.models.PodcastChannel;
import one.chandan.rubato.util.CollectionUtil;

import java.util.Collections;
import java.util.List;

public class PodcastChannelCatalogueViewModel extends AndroidViewModel {
    private final PodcastRepository podcastRepository;

    private final MutableLiveData<List<PodcastChannel>> podcastChannels = new MutableLiveData<>(Collections.emptyList());


    public PodcastChannelCatalogueViewModel(@NonNull Application application) {
        super(application);

        podcastRepository = new PodcastRepository();
    }

    public LiveData<List<PodcastChannel>> getPodcastChannels(LifecycleOwner owner) {
        if (podcastChannels.getValue() == null || podcastChannels.getValue().isEmpty()) {
            podcastRepository.getPodcastChannels(false, null)
                    .observe(owner, items -> podcastChannels.postValue(CollectionUtil.arrayListOrEmpty(items)));
        }

        return podcastChannels;
    }
}
