package one.chandan.rubato.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import one.chandan.rubato.repository.PodcastRepository;
import one.chandan.rubato.subsonic.models.PodcastChannel;
import one.chandan.rubato.subsonic.models.PodcastEpisode;

import java.util.List;

public class PodcastChannelPageViewModel extends AndroidViewModel {
    private final PodcastRepository podcastRepository;

    private PodcastChannel podcastChannel;

    public PodcastChannelPageViewModel(@NonNull Application application) {
        super(application);

        podcastRepository = new PodcastRepository();
    }

    public LiveData<List<PodcastChannel>> getPodcastChannelEpisodes() {
        return podcastRepository.getPodcastChannels(true, podcastChannel.getId());
    }

    public PodcastChannel getPodcastChannel() {
        return podcastChannel;
    }

    public void setPodcastChannel(PodcastChannel podcastChannel) {
        this.podcastChannel = podcastChannel;
    }

    public void requestPodcastEpisodeDownload(PodcastEpisode podcastEpisode) {
        podcastRepository.downloadPodcastEpisode(podcastEpisode.getId());
    }
}
