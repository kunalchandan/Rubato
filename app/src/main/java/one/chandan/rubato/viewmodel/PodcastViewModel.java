package one.chandan.rubato.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import one.chandan.rubato.repository.PodcastRepository;
import one.chandan.rubato.subsonic.models.PodcastChannel;
import one.chandan.rubato.subsonic.models.PodcastEpisode;
import one.chandan.rubato.util.CollectionUtil;

import java.util.Collections;
import java.util.List;

public class PodcastViewModel extends AndroidViewModel {
    private final PodcastRepository podcastRepository;

    private final MutableLiveData<List<PodcastEpisode>> newestPodcastEpisodes = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<List<PodcastChannel>> podcastChannels = new MutableLiveData<>(Collections.emptyList());

    public PodcastViewModel(@NonNull Application application) {
        super(application);

        podcastRepository = new PodcastRepository();
    }

    public LiveData<List<PodcastEpisode>> getNewestPodcastEpisodes(LifecycleOwner owner) {
        if (newestPodcastEpisodes.getValue() == null || newestPodcastEpisodes.getValue().isEmpty()) {
            podcastRepository.getNewestPodcastEpisodes(20)
                    .observe(owner, items -> newestPodcastEpisodes.postValue(CollectionUtil.arrayListOrEmpty(items)));
        }

        return newestPodcastEpisodes;
    }

    public LiveData<List<PodcastChannel>> getPodcastChannels(LifecycleOwner owner) {
        if (podcastChannels.getValue() == null || podcastChannels.getValue().isEmpty()) {
            podcastRepository.getPodcastChannels(false, null)
                    .observe(owner, items -> podcastChannels.postValue(CollectionUtil.arrayListOrEmpty(items)));
        }

        return podcastChannels;
    }

    public void refreshNewestPodcastEpisodes(LifecycleOwner owner) {
        podcastRepository.getNewestPodcastEpisodes(20)
                .observe(owner, items -> newestPodcastEpisodes.postValue(CollectionUtil.arrayListOrEmpty(items)));
    }

    public void refreshPodcastChannels(LifecycleOwner owner) {
        podcastRepository.getPodcastChannels(false, null)
                .observe(owner, items -> podcastChannels.postValue(CollectionUtil.arrayListOrEmpty(items)));
    }
}
