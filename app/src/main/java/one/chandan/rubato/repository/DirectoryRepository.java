package one.chandan.rubato.repository;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.lifecycle.MutableLiveData;

import one.chandan.rubato.App;
import one.chandan.rubato.R;
import one.chandan.rubato.subsonic.base.ApiResponse;
import one.chandan.rubato.subsonic.models.Child;
import one.chandan.rubato.subsonic.models.Directory;
import one.chandan.rubato.subsonic.models.Indexes;
import one.chandan.rubato.subsonic.models.MusicFolder;
import one.chandan.rubato.util.AppExecutors;
import one.chandan.rubato.util.Constants;
import one.chandan.rubato.util.OfflinePolicy;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DirectoryRepository {
    private static final String TAG = "DirectoryRepository";
    private static final String LOCAL_DIRECTORY_PREFIX = "local-dir:";
    private static final Set<String> AUDIO_EXTENSIONS = new HashSet<>(Arrays.asList(
            "aac", "aiff", "alac", "ape", "flac", "m4a", "m4b", "m4p", "mp3", "mp4", "oga",
            "ogg", "opus", "wav", "wma"
    ));
    private final CacheRepository cacheRepository = new CacheRepository();

    public MutableLiveData<List<MusicFolder>> getMusicFolders() {
        MutableLiveData<List<MusicFolder>> liveMusicFolders = new MutableLiveData<>();
        String cacheKey = "music_folders";

        if (OfflinePolicy.isOffline()) {
            loadCachedMusicFolders(cacheKey, liveMusicFolders);
            return liveMusicFolders;
        }

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getMusicFolders()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getMusicFolders() != null) {
                            List<MusicFolder> folders = response.body().getSubsonicResponse().getMusicFolders().getMusicFolders();
                            liveMusicFolders.setValue(folders);
                            cacheRepository.save(cacheKey, folders);
                            return;
                        }

                        loadCachedMusicFolders(cacheKey, liveMusicFolders);
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        loadCachedMusicFolders(cacheKey, liveMusicFolders);

                    }
                });

        return liveMusicFolders;
    }

    public MutableLiveData<Indexes> getIndexes(String musicFolderId, Long ifModifiedSince) {
        MutableLiveData<Indexes> liveIndexes = new MutableLiveData<>();
        String cacheKey = "indexes_" + musicFolderId;

        if (OfflinePolicy.isOffline()) {
            loadCachedIndexes(cacheKey, liveIndexes);
            return liveIndexes;
        }

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getIndexes(musicFolderId, ifModifiedSince)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getIndexes() != null) {
                            Indexes indexes = response.body().getSubsonicResponse().getIndexes();
                            liveIndexes.setValue(indexes);
                            cacheRepository.save(cacheKey, indexes);
                            return;
                        }

                        loadCachedIndexes(cacheKey, liveIndexes);
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        loadCachedIndexes(cacheKey, liveIndexes);

                    }
                });

        return liveIndexes;
    }

    public MutableLiveData<Directory> getMusicDirectory(String id) {
        MutableLiveData<Directory> liveMusicDirectory = new MutableLiveData<>();
        String cacheKey = "music_directory_" + id;

        if (isLocalDirectoryId(id)) {
            return getLocalDirectory(id);
        }

        if (OfflinePolicy.isOffline()) {
            loadCachedDirectory(cacheKey, liveMusicDirectory);
            return liveMusicDirectory;
        }

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getMusicDirectory(id)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getDirectory() != null) {
                            Directory directory = response.body().getSubsonicResponse().getDirectory();
                            liveMusicDirectory.setValue(directory);
                            cacheRepository.save(cacheKey, directory);
                            return;
                        }

                        loadCachedDirectory(cacheKey, liveMusicDirectory);
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        loadCachedDirectory(cacheKey, liveMusicDirectory);
                    }
                });

        return liveMusicDirectory;
    }

    public static boolean isLocalDirectoryId(@Nullable String id) {
        return id != null && id.startsWith(LOCAL_DIRECTORY_PREFIX);
    }

    @Nullable
    public static String buildLocalDirectoryId(@Nullable String uri) {
        if (uri == null) return null;
        if (isLocalDirectoryId(uri)) return uri;
        return LOCAL_DIRECTORY_PREFIX + uri;
    }

    private void loadCachedMusicFolders(String cacheKey, MutableLiveData<List<MusicFolder>> liveMusicFolders) {
        Type type = new TypeToken<List<MusicFolder>>() {
        }.getType();
        cacheRepository.load(cacheKey, type, new CacheRepository.CacheResult<List<MusicFolder>>() {
            @Override
            public void onLoaded(List<MusicFolder> musicFolders) {
                liveMusicFolders.postValue(musicFolders);
            }
        });
    }

    private void loadCachedIndexes(String cacheKey, MutableLiveData<Indexes> liveIndexes) {
        cacheRepository.load(cacheKey, Indexes.class, new CacheRepository.CacheResult<Indexes>() {
            @Override
            public void onLoaded(Indexes indexes) {
                liveIndexes.postValue(indexes);
            }
        });
    }

    private void loadCachedDirectory(String cacheKey, MutableLiveData<Directory> liveMusicDirectory) {
        cacheRepository.load(cacheKey, Directory.class, new CacheRepository.CacheResult<Directory>() {
            @Override
            public void onLoaded(Directory directory) {
                liveMusicDirectory.postValue(directory);
            }
        });
    }

    private MutableLiveData<Directory> getLocalDirectory(String id) {
        MutableLiveData<Directory> liveDirectory = new MutableLiveData<>();
        AppExecutors.localMusic().execute(() -> {
            Directory directory = buildLocalDirectory(App.getContext(), id);
            liveDirectory.postValue(directory);
        });
        return liveDirectory;
    }

    private Directory buildLocalDirectory(Context context, String id) {
        Directory directory = new Directory();
        directory.setId(id);

        String uriString = stripLocalDirectoryId(id);
        Uri uri = uriString != null ? Uri.parse(uriString) : null;
        DocumentFile documentFile = resolveDocumentFile(context, uri);

        String name = null;
        if (documentFile != null && !TextUtils.isEmpty(documentFile.getName())) {
            name = documentFile.getName();
        }
        if (TextUtils.isEmpty(name)) {
            name = context.getString(R.string.music_sources_local_default_name);
        }
        directory.setName(name);

        List<Child> children = new ArrayList<>();
        if (documentFile != null && documentFile.isDirectory()) {
            DocumentFile[] files = documentFile.listFiles();
            if (files != null) {
                for (DocumentFile file : files) {
                    if (file == null || !file.exists()) continue;
                    if (file.isDirectory()) {
                        Child child = buildLocalDirectoryChild(file, id);
                        if (child != null) {
                            children.add(child);
                        }
                        continue;
                    }
                    if (!isAudioFile(file)) continue;
                    Child child = buildLocalSongChild(file, id);
                    if (child != null) {
                        children.add(child);
                    }
                }
            }
        }

        if (!children.isEmpty()) {
            Collections.sort(children, LOCAL_DIRECTORY_SORT);
        }
        directory.setChildren(children);

        return directory;
    }

    private static final Comparator<Child> LOCAL_DIRECTORY_SORT = (left, right) -> {
        if (left == null && right == null) return 0;
        if (left == null) return 1;
        if (right == null) return -1;
        if (left.isDir() != right.isDir()) {
            return left.isDir() ? -1 : 1;
        }
        String leftName = left.getTitle() != null ? left.getTitle() : "";
        String rightName = right.getTitle() != null ? right.getTitle() : "";
        return leftName.compareToIgnoreCase(rightName);
    };

    @Nullable
    private static Child buildLocalDirectoryChild(DocumentFile file, String parentId) {
        if (file == null) return null;
        String name = file.getName();
        String id = buildLocalDirectoryId(file.getUri().toString());
        if (id == null) return null;
        Child child = new Child(id);
        child.setParentId(parentId);
        child.setDir(true);
        child.setTitle(name != null ? name : "");
        return child;
    }

    @Nullable
    private static Child buildLocalSongChild(DocumentFile file, String parentId) {
        if (file == null) return null;
        String uri = file.getUri().toString();
        Child child = new Child(LocalMusicRepository.LOCAL_SONG_PREFIX + uri);
        child.setParentId(parentId);
        child.setDir(false);
        child.setType(Constants.MEDIA_TYPE_LOCAL);
        child.setPath(uri);
        child.setContentType(file.getType());
        child.setSize(file.length());

        String name = file.getName();
        child.setTitle(stripExtension(name));
        child.setSuffix(resolveExtension(name, file.getType()));
        return child;
    }

    private static boolean isAudioFile(DocumentFile file) {
        String type = file.getType();
        if (type != null && type.startsWith("audio/")) {
            return true;
        }
        String name = file.getName();
        String extension = resolveExtension(name, type);
        return extension != null && AUDIO_EXTENSIONS.contains(extension);
    }

    @Nullable
    private static String resolveExtension(@Nullable String name, @Nullable String mime) {
        if (name != null) {
            int dot = name.lastIndexOf('.');
            if (dot > 0 && dot < name.length() - 1) {
                return name.substring(dot + 1).toLowerCase(Locale.US);
            }
        }
        if (mime == null) return null;
        int slash = mime.indexOf('/');
        if (slash >= 0 && slash < mime.length() - 1) {
            return mime.substring(slash + 1).toLowerCase(Locale.US);
        }
        return null;
    }

    private static String stripExtension(@Nullable String name) {
        if (name == null) return null;
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            return name.substring(0, dot);
        }
        return name;
    }

    @Nullable
    private static String stripLocalDirectoryId(@Nullable String id) {
        if (!isLocalDirectoryId(id)) return id;
        return id.substring(LOCAL_DIRECTORY_PREFIX.length());
    }

    @Nullable
    private static DocumentFile resolveDocumentFile(Context context, @Nullable Uri uri) {
        if (uri == null) return null;
        DocumentFile documentFile = DocumentFile.fromTreeUri(context, uri);
        if (documentFile != null) return documentFile;
        return DocumentFile.fromSingleUri(context, uri);
    }
}
