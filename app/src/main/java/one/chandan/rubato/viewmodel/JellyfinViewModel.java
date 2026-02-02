package one.chandan.rubato.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import one.chandan.rubato.model.JellyfinServer;
import one.chandan.rubato.repository.JellyfinServerRepository;

import java.util.List;

public class JellyfinViewModel extends AndroidViewModel {
    private final JellyfinServerRepository repository;
    private JellyfinServer toEdit;

    public JellyfinViewModel(@NonNull Application application) {
        super(application);
        repository = new JellyfinServerRepository();
    }

    public LiveData<List<JellyfinServer>> getServers() {
        return repository.getServers();
    }

    public void saveServer(JellyfinServer server) {
        repository.upsert(server);
    }

    public void deleteServer(JellyfinServer server) {
        if (server != null) {
            repository.delete(server);
        } else if (toEdit != null) {
            repository.delete(toEdit);
        }
    }

    public void setServerToEdit(JellyfinServer server) {
        toEdit = server;
    }

    public JellyfinServer getServerToEdit() {
        return toEdit;
    }
}
