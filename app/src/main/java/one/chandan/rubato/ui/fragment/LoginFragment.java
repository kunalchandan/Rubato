package one.chandan.rubato.ui.fragment;

import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import android.util.Log;

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
import one.chandan.rubato.ui.adapter.ServerAdapter;
import one.chandan.rubato.databinding.FragmentLoginBinding;
import one.chandan.rubato.interfaces.ClickCallback;
import one.chandan.rubato.interfaces.SystemCallback;
import one.chandan.rubato.model.LocalSource;
import one.chandan.rubato.model.Server;
import one.chandan.rubato.repository.LocalMusicRepository;
import one.chandan.rubato.repository.SystemRepository;
import one.chandan.rubato.ui.activity.MainActivity;
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

@UnstableApi
public class LoginFragment extends Fragment implements ClickCallback {
    private static final String TAG = "LoginFragment";

    private FragmentLoginBinding bind;
    private MainActivity activity;
    private LoginViewModel loginViewModel;
    private LocalSourceViewModel localSourceViewModel;

    private ServerAdapter serverAdapter;
    private ActivityResultLauncher<Uri> localFolderPicker;
    private ActivityResultLauncher<String[]> audioPermissionLauncher;
    private boolean pendingLocalSourceNav = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

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

                    TelemetryLogger.logEvent("Login", "local_source_audio_permission_result", granted ? "granted" : "denied", 0L, TelemetryLogger.SOURCE_UI, null);
                    if (granted) {
                        LocalMusicRepository.invalidateCache();
                    } else if (getActivity() != null) {
                        Toast.makeText(requireContext(), R.string.settings_local_music_permission_required, Toast.LENGTH_SHORT).show();
                    }

                    if (pendingLocalSourceNav) {
                        pendingLocalSourceNav = false;
                        if (activity != null) {
                            activity.goFromLogin();
                            TelemetryLogger.logEvent("Login", "local_source_navigate_home", "permission_result", 0L, TelemetryLogger.SOURCE_UI, null);
                        }
                    }
                });

        localFolderPicker = registerForActivityResult(
                new ActivityResultContracts.OpenDocumentTree(),
                uri -> {
                    TelemetryLogger.logEvent("Login", "local_source_picker_result", uri != null ? "ok" : "cancel", 0L, TelemetryLogger.SOURCE_UI, null);
                    Log.d(TAG, "local_source_picker_result uri=" + uri);
                    if (uri == null) return;
                    int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
                    try {
                        requireContext().getContentResolver().takePersistableUriPermission(uri, flags);
                        TelemetryLogger.logEvent("Login", "local_source_permission", "granted", 0L, TelemetryLogger.SOURCE_UI, null);
                    } catch (SecurityException | IllegalArgumentException ex) {
                        TelemetryLogger.logEvent("Login", "local_source_permission", "failed", 0L, TelemetryLogger.SOURCE_UI, ex.getMessage());
                        Log.w(TAG, "Failed to persist local source permission", ex);
                    }

                    if (localSourceViewModel == null) {
                        localSourceViewModel = new ViewModelProvider(requireActivity()).get(LocalSourceViewModel.class);
                    }
                    LocalSource source = LocalSourceUtil.buildLocalSource(requireContext(), uri);
                    if (source != null) {
                        Preferences.setLocalMusicEnabled(true);
                        localSourceViewModel.addSource(source);
                        TelemetryLogger.logEvent("Login", "local_source_added", "id=" + source.getId(), 0L, TelemetryLogger.SOURCE_UI, null);
                        Log.d(TAG, "local_source_added id=" + source.getId() + " path=" + source.getRelativePath());
                        if (!LocalMusicPermissions.hasReadPermission(requireContext())) {
                            pendingLocalSourceNav = true;
                            TelemetryLogger.logEvent("Login", "local_source_audio_permission_request", null, 0L, TelemetryLogger.SOURCE_UI, null);
                            audioPermissionLauncher.launch(LocalMusicPermissions.getReadPermissions());
                        } else if (activity != null) {
                            activity.goFromLogin();
                            TelemetryLogger.logEvent("Login", "local_source_navigate_home", "goFromLogin", 0L, TelemetryLogger.SOURCE_UI, null);
                        }
                    }
                    LocalMusicRepository.invalidateCache();
                }
        );
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.login_page_menu, menu);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();

        loginViewModel = new ViewModelProvider(requireActivity()).get(LoginViewModel.class);
        localSourceViewModel = new ViewModelProvider(requireActivity()).get(LocalSourceViewModel.class);
        bind = FragmentLoginBinding.inflate(inflater, container, false);
        View view = bind.getRoot();

        initAppBar();
        initServerListView();
        initSourceCards();

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        bind = null;
    }

    private void initAppBar() {
        activity.setSupportActionBar(bind.toolbar);
        bind.toolbar.setTitle(R.string.login_sources_title);
    }

    private void initServerListView() {
        bind.serverListRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        bind.serverListRecyclerView.setHasFixedSize(true);

        serverAdapter = new ServerAdapter(this);
        bind.serverListRecyclerView.setAdapter(serverAdapter);
        loginViewModel.getServerList().observe(getViewLifecycleOwner(), servers -> {
            if (!servers.isEmpty()) {
                if (bind != null) bind.noServerSourcesContainer.setVisibility(View.GONE);
                if (bind != null) bind.serverListRecyclerView.setVisibility(View.VISIBLE);
                serverAdapter.setItems(servers);
            } else {
                if (bind != null) bind.noServerSourcesContainer.setVisibility(View.VISIBLE);
                if (bind != null) bind.serverListRecyclerView.setVisibility(View.GONE);
            }
        });
    }

    private void initSourceCards() {
        bind.loginAddSubsonicButton.setOnClickListener(v -> {
            ServerSignupDialog dialog = new ServerSignupDialog();
            dialog.show(activity.getSupportFragmentManager(), null);
        });

        bind.loginAddJellyfinButton.setOnClickListener(v -> {
            JellyfinServerDialog dialog = new JellyfinServerDialog();
            dialog.show(activity.getSupportFragmentManager(), null);
        });

        bind.loginAddLocalButton.setOnClickListener(v -> {
            TelemetryLogger.logEvent("Login", "local_source_picker_launch", null, 0L, TelemetryLogger.SOURCE_UI, null);
            Log.d(TAG, "local_source_picker_launch");
            localFolderPicker.launch(null);
        });

        View.OnClickListener openGithub = v -> {
            String url = getString(R.string.settings_github_link);
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        };
        bind.loginSourceOtherCard.setOnClickListener(openGithub);
        bind.loginRequestSourceButton.setOnClickListener(openGithub);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        return false;
    }

    @Override
    public void onServerClick(Bundle bundle) {
        Server server = bundle.getParcelable("server_object");
        saveServerPreference(server.getServerId(), server.getAddress(), server.getLocalAddress(), server.getUsername(), server.getPassword(), server.isLowSecurity());

        SystemRepository systemRepository = new SystemRepository();
        systemRepository.checkUserCredential(new SystemCallback() {
            @Override
            public void onError(Exception exception) {
                Preferences.switchInUseServerAddress();
                resetServerPreference();
                Toast.makeText(requireContext(), exception.getMessage(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onSuccess(String password, String token, String salt) {
                triggerMetadataSync();
                activity.goFromLogin();
            }
        });
    }

    @Override
    public void onServerLongClick(Bundle bundle) {
        ServerSignupDialog dialog = new ServerSignupDialog();
        dialog.setArguments(bundle);
        dialog.show(activity.getSupportFragmentManager(), null);
    }

    private void saveServerPreference(String serverId, String server, String localAddress, String user, String password, boolean isLowSecurity) {
        Preferences.setServerId(serverId);
        Preferences.setServer(server);
        Preferences.setLocalAddress(localAddress);
        Preferences.setUser(user);
        Preferences.setPassword(password);
        Preferences.setLowSecurity(isLowSecurity);

        App.getSubsonicClientInstance(true);
    }

    private void resetServerPreference() {
        Preferences.setServerId(null);
        Preferences.setServer(null);
        Preferences.setUser(null);
        Preferences.setPassword(null);
        Preferences.setToken(null);
        Preferences.setSalt(null);
        Preferences.setLowSecurity(false);

        App.getSubsonicClientInstance(true);
    }

    private void triggerMetadataSync() {
        if (getContext() == null) return;
        final android.content.Context appContext = getContext().getApplicationContext();
        AppExecutors.io().execute(() -> MetadataSyncManager.runSyncNow(appContext, false));
    }
}
