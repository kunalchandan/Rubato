package one.chandan.rubato.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import one.chandan.rubato.repository.RadioRepository;
import one.chandan.rubato.subsonic.models.InternetRadioStation;
import one.chandan.rubato.util.CollectionUtil;

import java.util.Collections;
import java.util.List;

public class RadioViewModel extends AndroidViewModel {
    private final RadioRepository radioRepository;

    private final MutableLiveData<List<InternetRadioStation>> internetRadioStations = new MutableLiveData<>(Collections.emptyList());

    public RadioViewModel(@NonNull Application application) {
        super(application);

        radioRepository = new RadioRepository();
    }

    public LiveData<List<InternetRadioStation>> getInternetRadioStations(LifecycleOwner owner) {
        radioRepository.getInternetRadioStations()
                .observe(owner, items -> internetRadioStations.postValue(CollectionUtil.arrayListOrEmpty(items)));
        return internetRadioStations;
    }

    public void refreshInternetRadioStations(LifecycleOwner owner) {
        radioRepository.getInternetRadioStations()
                .observe(owner, items -> internetRadioStations.postValue(CollectionUtil.arrayListOrEmpty(items)));
    }
}
