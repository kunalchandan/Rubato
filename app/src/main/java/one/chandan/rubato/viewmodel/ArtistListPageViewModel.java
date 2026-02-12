package one.chandan.rubato.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import one.chandan.rubato.model.Download;
import one.chandan.rubato.repository.DownloadRepository;
import one.chandan.rubato.repository.LibraryRepository;
import one.chandan.rubato.subsonic.models.ArtistID3;
import one.chandan.rubato.util.CollectionUtil;
import one.chandan.rubato.util.Constants;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class ArtistListPageViewModel extends AndroidViewModel {
    private final LibraryRepository libraryRepository;
    private final DownloadRepository downloadRepository;

    public String title;

    private MutableLiveData<List<ArtistID3>> artistList;

    public ArtistListPageViewModel(@NonNull Application application) {
        super(application);

        libraryRepository = new LibraryRepository();
        downloadRepository = new DownloadRepository();
    }

    public LiveData<List<ArtistID3>> getArtistList(LifecycleOwner owner) {
        artistList = new MutableLiveData<>(new ArrayList<>());

        switch (title) {
            case Constants.ARTIST_STARRED:
                libraryRepository.getStarredArtists(false, -1)
                        .observe(owner, artists -> artistList.setValue(CollectionUtil.arrayListOrEmpty(artists)));
                break;
            case Constants.ARTIST_DOWNLOADED:
                downloadRepository.getLiveDownload().observe(owner, downloads -> {
                    if (downloads == null || downloads.isEmpty()) {
                        artistList.setValue(new ArrayList<>());
                        return;
                    }
                    List<Download> unique = downloads
                            .stream()
                            .collect(Collectors.collectingAndThen(
                                    Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(Download::getArtist))), ArrayList::new)
                            );

                    // TODO
                    // artistList.setValue(MappingUtil.mapDownloadToArtist(unique));
                });
                break;
        }

        return artistList;
    }
}
