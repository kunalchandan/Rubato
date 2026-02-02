package one.chandan.rubato.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import one.chandan.rubato.repository.PodcastRepository;
import one.chandan.rubato.subsonic.models.PodcastChannel;

public class PodcastChannelBottomSheetViewModel extends AndroidViewModel {
    private final PodcastRepository podcastRepository;

    private PodcastChannel podcastChannel;

    public PodcastChannelBottomSheetViewModel(@NonNull Application application) {
        super(application);

        podcastRepository = new PodcastRepository();
    }

    public PodcastChannel getPodcastChannel() {
        return podcastChannel;
    }

    public void setPodcastChannel(PodcastChannel podcastChannel) {
        this.podcastChannel = podcastChannel;
    }

    public void deletePodcastChannel() {
        if (podcastChannel != null) podcastRepository.deletePodcastChannel(podcastChannel.getId());
    }
}
