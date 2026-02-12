package one.chandan.rubato.ui.fragment;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.recyclerview.widget.LinearLayoutManager;

import one.chandan.rubato.App;
import one.chandan.rubato.R;
import one.chandan.rubato.databinding.FragmentMusicSourcesBinding;
import one.chandan.rubato.interfaces.ClickCallback;
import one.chandan.rubato.interfaces.SystemCallback;
import one.chandan.rubato.model.LocalSource;
import one.chandan.rubato.model.Server;
import one.chandan.rubato.repository.SystemRepository;
import one.chandan.rubato.repository.LocalMusicRepository;
import one.chandan.rubato.ui.activity.MainActivity;
import one.chandan.rubato.ui.adapter.LocalSourceAdapter;
import one.chandan.rubato.ui.adapter.ServerAdapter;
import one.chandan.rubato.ui.adapter.JellyfinServerAdapter;
import one.chandan.rubato.ui.dialog.ServerSignupDialog;
import one.chandan.rubato.ui.dialog.JellyfinServerDialog;
import one.chandan.rubato.util.LocalMusicPermissions;
import one.chandan.rubato.util.LocalSourceUtil;
import one.chandan.rubato.util.MetadataSyncManager;
import one.chandan.rubato.util.Preferences;
import one.chandan.rubato.util.TelemetryLogger;
import one.chandan.rubato.util.AppExecutors;
import one.chandan.rubato.viewmodel.LocalSourceViewModel;
import one.chandan.rubato.viewmodel.LoginViewModel;
import one.chandan.rubato.viewmodel.JellyfinViewModel;
import one.chandan.rubato.interfaces.JellyfinClickCallback;


@UnstableApi
public class MusicSourcesFragment extends Fragment implements ClickCallback, JellyfinClickCallback, LocalSourceAdapter.Listener {
    private static final String TAG = "MusicSourcesFragment";
    private FragmentMusicSourcesBinding bind;
    private MainActivity activity;
    private LoginViewModel loginViewModel;
    private LocalSourceViewModel localSourceViewModel;
    private JellyfinViewModel jellyfinViewModel;

    private ServerAdapter serverAdapter;
    private LocalSourceAdapter localSourceAdapter;
    private JellyfinServerAdapter jellyfinServerAdapter;

    private ActivityResultLauncher<Uri> localFolderPicker;
    private ActivityResultLauncher<String[]> audioPermissionLauncher;
    private boolean pendingLocalSourceNav = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        audioPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean granted = false;
                    for (Boolean value : result.values()) {
                        if (Boolean.TRUE.equals(value)) {
                            granted = true;
                            break;
                        }
                    }

                    TelemetryLogger.logEvent("Sources", "local_source_audio_permission_result", granted ? "granted" : "denied", 0L, TelemetryLogger.SOURCE_UI, null);
                    if (granted) {
                        LocalMusicRepository.invalidateCache();
                    } else if (getActivity() != null) {
                        Toast.makeText(requireContext(), R.string.settings_local_music_permission_required, Toast.LENGTH_SHORT).show();
                    }

                    if (pendingLocalSourceNav) {
                        pendingLocalSourceNav = false;
                        navigateHomeAfterLocalSource();
                    }
                });

        localFolderPicker = registerForActivityResult(
                new ActivityResultContracts.OpenDocumentTree(),
                uri -> {
                    TelemetryLogger.logEvent("Sources", "local_source_picker_result", uri != null ? "ok" : "cancel", 0L, TelemetryLogger.SOURCE_UI, null);
                    Log.d(TAG, "local_source_picker_result uri=" + uri);
                    if (uri == null) return;
                    int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
                    try {
                        requireContext().getContentResolver().takePersistableUriPermission(uri, flags);
                        TelemetryLogger.logEvent("Sources", "local_source_permission", "granted", 0L, TelemetryLogger.SOURCE_UI, null);
                    } catch (SecurityException | IllegalArgumentException ex) {
                        TelemetryLogger.logEvent("Sources", "local_source_permission", "failed", 0L, TelemetryLogger.SOURCE_UI, ex.getMessage());
                        Log.w(TAG, "Failed to persist local source permission", ex);
                    }

                    LocalSource source = LocalSourceUtil.buildLocalSource(requireContext(), uri);
                    if (localSourceViewModel == null) {
                        localSourceViewModel = new ViewModelProvider(requireActivity()).get(LocalSourceViewModel.class);
                    }
                    if (source != null) {
                        Preferences.setLocalMusicEnabled(true);
                        localSourceViewModel.addSource(source);
                        TelemetryLogger.logEvent("Sources", "local_source_added", "id=" + source.getId(), 0L, TelemetryLogger.SOURCE_UI, null);
                        Log.d(TAG, "local_source_added id=" + source.getId() + " path=" + source.getRelativePath());
                        if (!LocalMusicPermissions.hasReadPermission(requireContext())) {
                            pendingLocalSourceNav = true;
                            TelemetryLogger.logEvent("Sources", "local_source_audio_permission_request", null, 0L, TelemetryLogger.SOURCE_UI, null);
                            audioPermissionLauncher.launch(LocalMusicPermissions.getReadPermissions());
                        } else {
                            navigateHomeAfterLocalSource();
                        }
                    }
                    LocalMusicRepository.invalidateCache();
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();
        bind = FragmentMusicSourcesBinding.inflate(inflater, container, false);
        View view = bind.getRoot();

        loginViewModel = new ViewModelProvider(requireActivity()).get(LoginViewModel.class);
        localSourceViewModel = new ViewModelProvider(requireActivity()).get(LocalSourceViewModel.class);
        jellyfinViewModel = new ViewModelProvider(requireActivity()).get(JellyfinViewModel.class);

        initToolbar();
        initSubsonicSection();
        initJellyfinSection();
        initLocalSection();
        initOtherSourcesCard();

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (activity != null) {
            activity.setBottomSheetVisibility(false);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (activity != null) {
            activity.setBottomSheetVisibility(true);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        bind = null;
    }

    private void initToolbar() {
        activity.setSupportActionBar(bind.musicSourcesToolbar.toolbar);
        bind.musicSourcesToolbar.toolbar.setTitle(R.string.empty_string);
        bind.musicSourcesToolbar.toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        bind.musicSourcesToolbar.toolbar.setNavigationOnClickListener(v -> activity.onBackPressed());
    }

    private void initSubsonicSection() {
        bind.subsonicRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        bind.subsonicRecyclerView.setHasFixedSize(true);
        bind.subsonicRecyclerView.setNestedScrollingEnabled(false);

        serverAdapter = new ServerAdapter(this);
        bind.subsonicRecyclerView.setAdapter(serverAdapter);

        bind.addSubsonicButton.setOnClickListener(v -> {
            ServerSignupDialog dialog = new ServerSignupDialog();
            dialog.show(activity.getSupportFragmentManager(), null);
        });

        loginViewModel.getServerList().observe(getViewLifecycleOwner(), servers -> {
            boolean hasServers = servers != null && !servers.isEmpty();
            bind.subsonicEmptyText.setVisibility(hasServers ? View.GONE : View.VISIBLE);
            bind.subsonicRecyclerView.setVisibility(hasServers ? View.VISIBLE : View.GONE);
            serverAdapter.setItems(servers);
        });
    }

    private void initJellyfinSection() {
        bind.jellyfinRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        bind.jellyfinRecyclerView.setHasFixedSize(true);
        bind.jellyfinRecyclerView.setNestedScrollingEnabled(false);

        jellyfinServerAdapter = new JellyfinServerAdapter(this);
        bind.jellyfinRecyclerView.setAdapter(jellyfinServerAdapter);

        bind.addJellyfinButton.setOnClickListener(v -> {
            JellyfinServerDialog dialog = new JellyfinServerDialog();
            dialog.show(activity.getSupportFragmentManager(), null);
        });

        jellyfinViewModel.getServers().observe(getViewLifecycleOwner(), servers -> {
            boolean hasServers = servers != null && !servers.isEmpty();
            bind.jellyfinEmptyText.setVisibility(hasServers ? View.GONE : View.VISIBLE);
            bind.jellyfinRecyclerView.setVisibility(hasServers ? View.VISIBLE : View.GONE);
            jellyfinServerAdapter.setItems(servers);
        });
    }

    private void initLocalSection() {
        bind.localRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        bind.localRecyclerView.setHasFixedSize(true);
        bind.localRecyclerView.setNestedScrollingEnabled(false);

        localSourceAdapter = new LocalSourceAdapter(this);
        bind.localRecyclerView.setAdapter(localSourceAdapter);

        bind.addLocalButton.setOnClickListener(v -> {
            TelemetryLogger.logEvent("Sources", "local_source_picker_launch", null, 0L, TelemetryLogger.SOURCE_UI, null);
            Log.d(TAG, "local_source_picker_launch");
            localFolderPicker.launch(null);
        });

        localSourceViewModel.getSources().observe(getViewLifecycleOwner(), sources -> {
            boolean hasSources = sources != null && !sources.isEmpty();
            bind.localEmptyText.setVisibility(hasSources ? View.GONE : View.VISIBLE);
            bind.localRecyclerView.setVisibility(hasSources ? View.VISIBLE : View.GONE);
            localSourceAdapter.setItems(sources);
        });
    }

    private void initOtherSourcesCard() {
        View.OnClickListener openGithub = v -> {
            String url = getString(R.string.settings_github_link);
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        };
        bind.musicSourcesOtherCard.setOnClickListener(openGithub);
        bind.musicSourcesRequestSourceButton.setOnClickListener(openGithub);
    }

    @Override
    public void onServerClick(Bundle bundle) {
        Server server = bundle.getParcelable("server_object");
        if (server == null) return;

        String prevServerId = Preferences.getServerId();
        String prevServer = Preferences.getServer();
        String prevLocal = Preferences.getLocalAddress();
        String prevUser = Preferences.getUser();
        String prevPassword = Preferences.getPassword();
        boolean prevLowSecurity = Preferences.isLowScurity();

        saveServerPreference(server);

        SystemRepository systemRepository = new SystemRepository();
        systemRepository.checkUserCredential(new SystemCallback() {
            @Override
            public void onError(Exception exception) {
                restoreServerPreference(prevServerId, prevServer, prevLocal, prevUser, prevPassword, prevLowSecurity);
                Toast.makeText(requireContext(), exception.getMessage(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onSuccess(String password, String token, String salt) {
                Toast.makeText(requireContext(), R.string.music_sources_server_active, Toast.LENGTH_SHORT).show();
                triggerMetadataSync();
            }
        });
    }

    @Override
    public void onServerLongClick(Bundle bundle) {
        ServerSignupDialog dialog = new ServerSignupDialog();
        dialog.setArguments(bundle);
        dialog.show(activity.getSupportFragmentManager(), null);
    }

    @Override
    public void onJellyfinClick(Bundle bundle) {
        JellyfinServerDialog dialog = new JellyfinServerDialog();
        dialog.setArguments(bundle);
        dialog.show(activity.getSupportFragmentManager(), null);
    }

    @Override
    public void onJellyfinLongClick(Bundle bundle) {
        JellyfinServerDialog dialog = new JellyfinServerDialog();
        dialog.setArguments(bundle);
        dialog.show(activity.getSupportFragmentManager(), null);
    }

    @Override
    public void onLocalSourceRemove(LocalSource source) {
        localSourceViewModel.removeSource(source);
        LocalMusicRepository.invalidateCache();
    }

    private void navigateHomeAfterLocalSource() {
        if (activity == null || activity.navController == null) {
            TelemetryLogger.logEvent("Sources", "local_source_navigate_home", "missing_activity", 0L, TelemetryLogger.SOURCE_UI, null);
            return;
        }
        boolean popped = activity.navController.popBackStack(R.id.homeFragment, false);
        if (!popped && activity.navController.getCurrentDestination() != null
                && activity.navController.getCurrentDestination().getId() != R.id.homeFragment) {
            activity.navController.navigate(R.id.homeFragment);
        }
        TelemetryLogger.logEvent("Sources", "local_source_navigate_home", popped ? "popBackStack" : "navigate", 0L, TelemetryLogger.SOURCE_UI, null);
    }

    private void saveServerPreference(Server server) {
        Preferences.setServerId(server.getServerId());
        Preferences.setServer(server.getAddress());
        Preferences.setLocalAddress(server.getLocalAddress());
        Preferences.setUser(server.getUsername());
        Preferences.setPassword(server.getPassword());
        Preferences.setLowSecurity(server.isLowSecurity());

        App.getSubsonicClientInstance(true);
    }

    private void restoreServerPreference(String serverId, String server, String localAddress, String user, String password, boolean isLowSecurity) {
        Preferences.setServerId(serverId);
        Preferences.setServer(server);
        Preferences.setLocalAddress(localAddress);
        Preferences.setUser(user);
        Preferences.setPassword(password);
        Preferences.setLowSecurity(isLowSecurity);

        App.getSubsonicClientInstance(true);
    }

    private void triggerMetadataSync() {
        if (getContext() == null) return;
        final android.content.Context appContext = getContext().getApplicationContext();
        AppExecutors.io().execute(() -> MetadataSyncManager.runSyncNow(appContext, false));
    }

}
