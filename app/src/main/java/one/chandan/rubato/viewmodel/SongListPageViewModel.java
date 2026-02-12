package one.chandan.rubato.viewmodel;

import android.app.Application;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import one.chandan.rubato.repository.LibraryRepository;
import one.chandan.rubato.subsonic.models.AlbumID3;
import one.chandan.rubato.subsonic.models.ArtistID3;
import one.chandan.rubato.subsonic.models.Child;
import one.chandan.rubato.subsonic.models.Genre;
import one.chandan.rubato.util.Constants;

import java.util.ArrayList;
import java.util.List;

public class SongListPageViewModel extends AndroidViewModel {
    private final LibraryRepository libraryRepository;

    public String title;
    public String toolbarTitle;
    public Genre genre;
    public ArtistID3 artist;
    public AlbumID3 album;

    private MutableLiveData<List<Child>> songList;
    private MutableLiveData<List<Genre>> relatedGenres;

    public ArrayList<String> filters = new ArrayList<>();
    public ArrayList<String> filterNames = new ArrayList<>();

    public int year = 0;
    public int maxNumberByYear = 500;
    public int maxNumberByGenre = 100;

    public SongListPageViewModel(@NonNull Application application) {
        super(application);

        libraryRepository = new LibraryRepository();
    }

    public LiveData<List<Child>> getSongList() {
        songList = new MutableLiveData<>(new ArrayList<>());

        switch (title) {
            case Constants.MEDIA_BY_GENRE:
                songList = libraryRepository.getSongsByGenre(genre.getGenre(), 0);
                break;
            case Constants.MEDIA_BY_ARTIST:
                songList = libraryRepository.getArtistTopSongs(artist.getName(), 50);
                break;
            case Constants.MEDIA_BY_GENRES:
                songList = libraryRepository.getSongsByGenres(filters);
                break;
            case Constants.MEDIA_BY_YEAR:
                songList = libraryRepository.getRandomSample(maxNumberByYear, year, year + 10);
                break;
            case Constants.MEDIA_STARRED:
                songList = libraryRepository.getStarredSongs(false, -1);
                break;
        }

        return songList;
    }

    public LiveData<List<Genre>> getRelatedGenres() {
        if (relatedGenres == null) {
            relatedGenres = new MutableLiveData<>(new ArrayList<>());
        }
        if (Constants.MEDIA_BY_GENRE.equals(title) && genre != null) {
            relatedGenres = libraryRepository.getRelatedGenres(genre.getGenre(), 6);
        }
        return relatedGenres;
    }

    public void getSongsByPage(LifecycleOwner owner) {
        switch (title) {
            case Constants.MEDIA_BY_GENRE:
                int songCount = songList.getValue() != null ? songList.getValue().size() : 0;

                if (songCount > 0 && songCount % maxNumberByGenre != 0) return;

                int page = songCount / maxNumberByGenre;
                libraryRepository.getSongsByGenre(genre.getGenre(), page).observe(owner, children -> {
                    if (children != null && !children.isEmpty()) {
                        List<Child> currentMedia = songList.getValue();
                        currentMedia.addAll(children);
                        songList.setValue(currentMedia);
                    }
                });
                break;
            case Constants.MEDIA_BY_ARTIST:
            case Constants.MEDIA_BY_GENRES:
            case Constants.MEDIA_BY_YEAR:
            case Constants.MEDIA_STARRED:
                break;
        }
    }

    public String getFiltersTitle() {
        return TextUtils.join(", ", filterNames);
    }
}
