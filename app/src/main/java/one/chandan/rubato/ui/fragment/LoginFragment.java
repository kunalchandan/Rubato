package one.chandan.rubato.ui.fragment;

import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Rect;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import android.widget.FrameLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
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
import one.chandan.rubato.ui.view.CoachmarkOverlayView;
import one.chandan.rubato.util.LocalSourceUtil;
import one.chandan.rubato.util.Preferences;
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

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        localFolderPicker = registerForActivityResult(
                new ActivityResultContracts.OpenDocumentTree(),
                uri -> {
                    if (uri == null) return;
                    int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION;
                    try {
                        requireContext().getContentResolver().takePersistableUriPermission(uri, flags);
                    } catch (SecurityException ignored) {
                    }

                    if (localSourceViewModel == null) {
                        localSourceViewModel = new ViewModelProvider(requireActivity()).get(LocalSourceViewModel.class);
                    }
                    LocalSource source = LocalSourceUtil.buildLocalSource(requireContext(), uri);
                    localSourceViewModel.addSource(source);
                    LocalMusicRepository.invalidateCache();
                }
        );
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.login_page_menu, menu);
        if (bind != null) {
            bind.toolbar.post(this::maybeShowAddServerCoachmark);
        }
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

        bind.appBarLayout.addOnOffsetChangedListener((appBarLayout, verticalOffset) -> {
            if ((bind.serverInfoSector.getHeight() + verticalOffset) < (2 * ViewCompat.getMinimumHeight(bind.toolbar))) {
                bind.toolbar.setTitle(R.string.login_title);
            } else {
                bind.toolbar.setTitle(R.string.empty_string);
            }
        });
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
                if (bind != null) {
                    bind.appBarLayout.setVisibility(View.VISIBLE);
                    bind.toolbar.setTitle(R.string.login_title);
                }
                serverAdapter.setItems(servers);
            } else {
                if (bind != null) bind.noServerSourcesContainer.setVisibility(View.VISIBLE);
                if (bind != null) bind.serverListRecyclerView.setVisibility(View.GONE);
                if (bind != null) {
                    bind.appBarLayout.setVisibility(View.GONE);
                    bind.toolbar.setTitle(R.string.empty_string);
                }
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

        bind.loginAddLocalButton.setOnClickListener(v -> localFolderPicker.launch(null));

        View.OnClickListener openGithub = v -> {
            String url = getString(R.string.settings_github_link);
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        };
        bind.loginSourceOtherCard.setOnClickListener(openGithub);
        bind.loginRequestSourceButton.setOnClickListener(openGithub);
    }

    private void maybeShowAddServerCoachmark() {
        if (bind == null || activity == null) return;
        if (!Preferences.isCoachmarkAddServerPending()) return;

        View addButtonView = bind.toolbar.findViewById(R.id.action_add);
        if (addButtonView == null || addButtonView.getWidth() == 0) {
            bind.toolbar.postDelayed(this::maybeShowAddServerCoachmark, 120);
            return;
        }

        FrameLayout container = activity.findViewById(R.id.coachmark_container);
        if (container == null) return;

        int[] targetLoc = new int[2];
        int[] containerLoc = new int[2];
        addButtonView.getLocationInWindow(targetLoc);
        container.getLocationInWindow(containerLoc);

        Rect rect = new Rect(
                targetLoc[0] - containerLoc[0],
                targetLoc[1] - containerLoc[1],
                targetLoc[0] - containerLoc[0] + addButtonView.getWidth(),
                targetLoc[1] - containerLoc[1] + addButtonView.getHeight()
        );

        container.removeAllViews();
        CoachmarkOverlayView overlay = new CoachmarkOverlayView(requireContext());
        overlay.setClickable(true);
        overlay.setLabel(getString(R.string.onboarding_server_hint));
        overlay.setTargetRect(rect);
        overlay.setOnClickListener(v -> {
            container.removeAllViews();
            container.setVisibility(View.GONE);
            Preferences.setCoachmarkAddServerPending(false);
        });

        container.addView(overlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        container.setVisibility(View.VISIBLE);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_add) {
            ServerSignupDialog dialog = new ServerSignupDialog();
            dialog.show(activity.getSupportFragmentManager(), null);
            return true;
        }

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
}
