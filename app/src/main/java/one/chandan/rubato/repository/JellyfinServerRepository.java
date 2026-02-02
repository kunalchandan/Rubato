package one.chandan.rubato.repository;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import one.chandan.rubato.App;
import one.chandan.rubato.model.JellyfinServer;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class JellyfinServerRepository {
    private static final String PREFS = "jellyfin_prefs";
    private static final String KEY_SERVERS = "servers";

    private final SharedPreferences preferences;
    private final Gson gson = new Gson();
    private final MutableLiveData<List<JellyfinServer>> serversLiveData = new MutableLiveData<>();

    public JellyfinServerRepository() {
        preferences = App.getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        serversLiveData.setValue(loadServers());
    }

    public LiveData<List<JellyfinServer>> getServers() {
        return serversLiveData;
    }

    public void upsert(JellyfinServer server) {
        List<JellyfinServer> current = new ArrayList<>(getCurrent());
        int index = indexOf(current, server.getId());
        if (index >= 0) {
            current.set(index, server);
        } else {
            current.add(server);
        }
        saveServers(current);
    }

    public void delete(JellyfinServer server) {
        if (server == null) return;
        List<JellyfinServer> current = new ArrayList<>(getCurrent());
        current.removeIf(item -> item.getId().equals(server.getId()));
        saveServers(current);
    }

    public JellyfinServer findById(String id) {
        if (id == null) return null;
        for (JellyfinServer server : getCurrent()) {
            if (server != null && id.equals(server.getId())) {
                return server;
            }
        }
        return null;
    }

    public List<JellyfinServer> getServersSnapshot() {
        return new ArrayList<>(getCurrent());
    }

    private List<JellyfinServer> getCurrent() {
        List<JellyfinServer> current = serversLiveData.getValue();
        return current != null ? current : Collections.emptyList();
    }

    private List<JellyfinServer> loadServers() {
        String json = preferences.getString(KEY_SERVERS, null);
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<>();
        }
        Type type = new TypeToken<List<JellyfinServer>>() {}.getType();
        List<JellyfinServer> parsed = gson.fromJson(json, type);
        return parsed != null ? parsed : new ArrayList<>();
    }

    private void saveServers(List<JellyfinServer> servers) {
        preferences.edit().putString(KEY_SERVERS, gson.toJson(servers)).apply();
        serversLiveData.postValue(new ArrayList<>(servers));
    }

    private int indexOf(List<JellyfinServer> servers, String id) {
        for (int i = 0; i < servers.size(); i++) {
            if (servers.get(i).getId().equals(id)) {
                return i;
            }
        }
        return -1;
    }
}
