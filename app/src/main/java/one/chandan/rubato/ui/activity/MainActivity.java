package one.chandan.rubato.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.splashscreen.SplashScreen;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import one.chandan.rubato.App;
import one.chandan.rubato.BuildConfig;
import one.chandan.rubato.R;
import one.chandan.rubato.broadcast.receiver.ConnectivityStatusBroadcastReceiver;
import one.chandan.rubato.databinding.ActivityMainBinding;
import one.chandan.rubato.github.utils.UpdateUtil;
import one.chandan.rubato.model.Download;
import one.chandan.rubato.repository.AlbumRepository;
import one.chandan.rubato.repository.DownloadRepository;
import one.chandan.rubato.service.MediaManager;
import one.chandan.rubato.ui.activity.base.BaseActivity;
import one.chandan.rubato.ui.dialog.ConnectionAlertDialog;
import one.chandan.rubato.ui.dialog.FirstLaunchDialog;
import one.chandan.rubato.ui.dialog.GithubTempoUpdateDialog;
import one.chandan.rubato.ui.dialog.ServerUnreachableDialog;
import one.chandan.rubato.ui.fragment.PlayerBottomSheetFragment;
import one.chandan.rubato.util.Constants;
import one.chandan.rubato.util.MetadataSyncManager;
import one.chandan.rubato.util.NetworkUtil;
import one.chandan.rubato.util.Preferences;
import one.chandan.rubato.util.ServerStatus;
import one.chandan.rubato.util.TelemetryLogger;
import one.chandan.rubato.viewmodel.MainViewModel;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.color.DynamicColors;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.Objects;
import java.util.concurrent.ExecutionException;

@UnstableApi
public class MainActivity extends BaseActivity {
    private static final String TAG = "MainActivityLogs";

    public ActivityMainBinding bind;
    private MainViewModel mainViewModel;

    private FragmentManager fragmentManager;
    private NavHostFragment navHostFragment;
    private BottomNavigationView bottomNavigationView;
    public NavController navController;
    private BottomSheetBehavior bottomSheetBehavior;
    private int lastDestinationId = -1;

    ConnectivityStatusBroadcastReceiver connectivityStatusBroadcastReceiver;

    private final NavController.OnDestinationChangedListener destinationListener =
            (controller, destination, arguments) -> {
                if (destination == null) return;
                int destId = destination.getId();
                if (destId == lastDestinationId) return;
                lastDestinationId = destId;
                String label = destination.getLabel() != null ? destination.getLabel().toString() : null;
                TelemetryLogger.logEvent(label, "nav_destination", "id=" + destId, 0L);
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        DynamicColors.applyToActivityIfAvailable(this);

        super.onCreate(savedInstanceState);

        bind = ActivityMainBinding.inflate(getLayoutInflater());
        View view = bind.getRoot();
        setContentView(view);

        mainViewModel = new ViewModelProvider(this).get(MainViewModel.class);

        connectivityStatusBroadcastReceiver = new ConnectivityStatusBroadcastReceiver(this);
        connectivityStatusReceiverManager(true);

        init();
        ServerStatus.getReachableLive().observe(this, reachable -> updateOfflineBanner());
        updateOfflineBanner();
        checkConnectionType();
        getOpenSubsonicExtensions();
        checkTempoUpdate();
        handleDownloadNotificationIntent(getIntent());
    }

    @Override
    protected void onStart() {
        super.onStart();
        initService();
        MetadataSyncManager.startIfNeeded(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        pingServer();
        updateOfflineBanner();
        TelemetryLogger.logEvent(getCurrentScreenLabel(), "lifecycle", "onResume", 0L);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleDownloadNotificationIntent(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (navController != null) {
            navController.removeOnDestinationChangedListener(destinationListener);
        }
        connectivityStatusReceiverManager(false);
        bind = null;
    }

    @Override
    public void onBackPressed() {
        if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED)
            collapseBottomSheetDelayed();
        else
            super.onBackPressed();
    }

    public void init() {
        fragmentManager = getSupportFragmentManager();

        initBottomSheet();
        initNavigation();

        if (Preferences.getPassword() != null || (Preferences.getToken() != null && Preferences.getSalt() != null)) {
            goFromLogin();
        } else {
            goToLogin();
        }

        maybeShowFirstLaunchDialog();
    }

    // BOTTOM SHEET/NAVIGATION
    private void initBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(findViewById(R.id.player_bottom_sheet));
        bottomSheetBehavior.addBottomSheetCallback(bottomSheetCallback);
        fragmentManager.beginTransaction().replace(R.id.player_bottom_sheet, new PlayerBottomSheetFragment(), "PlayerBottomSheet").commit();

        checkBottomSheetAfterStateChanged();
    }

    public void setBottomSheetInPeek(Boolean isVisible) {
        if (isVisible) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        } else {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        }
    }

    public void setBottomSheetVisibility(boolean visibility) {
        if (visibility) {
            findViewById(R.id.player_bottom_sheet).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.player_bottom_sheet).setVisibility(View.GONE);
        }
    }

    private void checkBottomSheetAfterStateChanged() {
        final Handler handler = new Handler();
        final Runnable runnable = () -> setBottomSheetInPeek(mainViewModel.isQueueLoaded());
        handler.postDelayed(runnable, 100);
    }

    public void collapseBottomSheetDelayed() {
        final Handler handler = new Handler();
        final Runnable runnable = () -> bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        handler.postDelayed(runnable, 100);
    }

    public void expandBottomSheet() {
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    public void setBottomSheetDraggableState(Boolean isDraggable) {
        bottomSheetBehavior.setDraggable(isDraggable);
    }

    private final BottomSheetBehavior.BottomSheetCallback bottomSheetCallback =
            new BottomSheetBehavior.BottomSheetCallback() {
                int navigationHeight;

                @Override
                public void onStateChanged(@NonNull View view, int state) {
                    PlayerBottomSheetFragment playerBottomSheetFragment = (PlayerBottomSheetFragment) getSupportFragmentManager().findFragmentByTag("PlayerBottomSheet");

                    switch (state) {
                        case BottomSheetBehavior.STATE_HIDDEN:
                            resetMusicSession();
                            break;
                        case BottomSheetBehavior.STATE_COLLAPSED:
                            if (playerBottomSheetFragment != null)
                                playerBottomSheetFragment.goBackToFirstPage();
                            break;
                        case BottomSheetBehavior.STATE_SETTLING:
                        case BottomSheetBehavior.STATE_EXPANDED:
                        case BottomSheetBehavior.STATE_DRAGGING:
                        case BottomSheetBehavior.STATE_HALF_EXPANDED:
                            break;
                    }
                }

                @Override
                public void onSlide(@NonNull View view, float slideOffset) {
                    animateBottomSheet(slideOffset);
                    animateBottomNavigation(slideOffset, navigationHeight);
                }
            };

    private void animateBottomSheet(float slideOffset) {
        PlayerBottomSheetFragment playerBottomSheetFragment = (PlayerBottomSheetFragment) getSupportFragmentManager().findFragmentByTag("PlayerBottomSheet");
        if (playerBottomSheetFragment != null) {
            float condensedSlideOffset = Math.max(0.0f, Math.min(0.2f, slideOffset - 0.2f)) / 0.2f;
            playerBottomSheetFragment.getPlayerHeader().setAlpha(1 - condensedSlideOffset);
            playerBottomSheetFragment.getPlayerHeader().setVisibility(condensedSlideOffset > 0.99 ? View.GONE : View.VISIBLE);
        }
    }

    private void animateBottomNavigation(float slideOffset, int navigationHeight) {
        if (slideOffset < 0) return;

        if (navigationHeight == 0) {
            navigationHeight = bind.bottomNavigation.getHeight();
        }

        float slideY = navigationHeight - navigationHeight * (1 - slideOffset);

        bind.bottomNavigation.setTranslationY(slideY);
    }

    private void initNavigation() {
        bottomNavigationView = findViewById(R.id.bottom_navigation);
        navHostFragment = (NavHostFragment) fragmentManager.findFragmentById(R.id.nav_host_fragment);
        navController = Objects.requireNonNull(navHostFragment).getNavController();

        /*
         * In questo modo intercetto il cambio schermata tramite navbar e se il bottom sheet è aperto,
         * lo chiudo
         */
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED && (
                    destination.getId() == R.id.homeFragment ||
                            destination.getId() == R.id.libraryFragment ||
                            destination.getId() == R.id.searchFragment ||
                            destination.getId() == R.id.downloadFragment)
            ) {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            }
        });

        NavigationUI.setupWithNavController(bottomNavigationView, navController);
        navController.addOnDestinationChangedListener(destinationListener);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        logInputEvent(ev);
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event != null && event.getAction() == KeyEvent.ACTION_DOWN) {
            String detail = "keyCode=" + event.getKeyCode() + " uptimeMs=" + SystemClock.elapsedRealtime();
            TelemetryLogger.logEvent(getCurrentScreenLabel(), "key_input", detail, 0L);
        }
        return super.dispatchKeyEvent(event);
    }

    private void logInputEvent(MotionEvent ev) {
        if (ev == null) return;
        int action = ev.getActionMasked();
        if (action != MotionEvent.ACTION_DOWN
                && action != MotionEvent.ACTION_UP
                && action != MotionEvent.ACTION_CANCEL) {
            return;
        }

        String detail = "action=" + actionToLabel(action)
                + " x=" + Math.round(ev.getRawX())
                + " y=" + Math.round(ev.getRawY())
                + " pointers=" + ev.getPointerCount()
                + " uptimeMs=" + SystemClock.elapsedRealtime();
        TelemetryLogger.logEvent(getCurrentScreenLabel(), "input", detail, 0L);
    }

    private String actionToLabel(int action) {
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                return "DOWN";
            case MotionEvent.ACTION_UP:
                return "UP";
            case MotionEvent.ACTION_CANCEL:
                return "CANCEL";
            default:
                return String.valueOf(action);
        }
    }

    private String getCurrentScreenLabel() {
        if (navController == null || navController.getCurrentDestination() == null) return null;
        if (navController.getCurrentDestination().getLabel() == null) return null;
        return navController.getCurrentDestination().getLabel().toString();
    }

    private void handleDownloadNotificationIntent(Intent intent) {
        if (intent == null) return;

        String downloadId = intent.getStringExtra(Constants.DOWNLOAD_NOTIFICATION_ID);
        if (downloadId == null || downloadId.isEmpty()) return;

        intent.removeExtra(Constants.DOWNLOAD_NOTIFICATION_ID);

        Download download = new DownloadRepository().getDownload(downloadId);
        if (download == null) return;

        if (download.getAlbumId() != null && !download.getAlbumId().isEmpty()) {
            openAlbumPage(download.getAlbumId(), download);
            return;
        }

        openDownloadPlayback(download);
    }

    private void openAlbumPage(String albumId, Download fallback) {
        AlbumRepository albumRepository = new AlbumRepository();
        albumRepository.getAlbum(albumId).observe(this, album -> {
            if (album != null) {
                Bundle bundle = new Bundle();
                bundle.putParcelable(Constants.ALBUM_OBJECT, album);
                navController.navigate(R.id.albumPageFragment, bundle);
            } else if (fallback != null) {
                openDownloadPlayback(fallback);
            }
        });
    }

    private void openDownloadPlayback(Download download) {
        if (download == null) return;
        MediaManager.startQueue(getMediaBrowserListenableFuture(), download);
        expandBottomSheet();
    }

    public void setBottomNavigationBarVisibility(boolean visibility) {
        if (visibility) {
            bottomNavigationView.setVisibility(View.VISIBLE);
        } else {
            bottomNavigationView.setVisibility(View.GONE);
        }
    }

    private void initService() {
        MediaManager.check(getMediaBrowserListenableFuture());

        getMediaBrowserListenableFuture().addListener(() -> {
            try {
                getMediaBrowserListenableFuture().get().addListener(new Player.Listener() {
                    @Override
                    public void onIsPlayingChanged(boolean isPlaying) {
                        if (isPlaying && bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_HIDDEN) {
                            setBottomSheetInPeek(true);
                        }
                    }
                });
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, MoreExecutors.directExecutor());
    }

    private void goToLogin() {
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        setBottomNavigationBarVisibility(false);
        setBottomSheetVisibility(false);

        if (Objects.requireNonNull(navController.getCurrentDestination()).getId() == R.id.landingFragment) {
            navController.navigate(R.id.action_landingFragment_to_loginFragment);
        } else if (Objects.requireNonNull(navController.getCurrentDestination()).getId() == R.id.settingsFragment) {
            navController.navigate(R.id.action_settingsFragment_to_loginFragment);
        } else if (Objects.requireNonNull(navController.getCurrentDestination()).getId() == R.id.homeFragment) {
            navController.navigate(R.id.action_homeFragment_to_loginFragment);
        }
    }

    private void goToHome() {
        bottomNavigationView.setVisibility(View.VISIBLE);

        if (Objects.requireNonNull(navController.getCurrentDestination()).getId() == R.id.landingFragment) {
            navController.navigate(R.id.action_landingFragment_to_homeFragment);
        } else if (Objects.requireNonNull(navController.getCurrentDestination()).getId() == R.id.loginFragment) {
            navController.navigate(R.id.action_loginFragment_to_homeFragment);
        }
    }

    public void goFromLogin() {
        setBottomSheetInPeek(mainViewModel.isQueueLoaded());
        goToHome();
    }

    private void maybeShowFirstLaunchDialog() {
        if (Preferences.isOnboardingShown()) return;
        if (fragmentManager.findFragmentByTag(FirstLaunchDialog.TAG) != null) return;

        bind.getRoot().post(() -> {
            if (isFinishing() || isDestroyed()) return;
            if (fragmentManager.findFragmentByTag(FirstLaunchDialog.TAG) != null) return;
            FirstLaunchDialog dialog = new FirstLaunchDialog();
            dialog.show(fragmentManager, FirstLaunchDialog.TAG);
        });
    }

    public void quit() {
        resetUserSession();
        resetMusicSession();
        resetViewModel();
        goToLogin();
    }

    private void resetUserSession() {
        Preferences.setServerId(null);
        Preferences.setSalt(null);
        Preferences.setToken(null);
        Preferences.setPassword(null);
        Preferences.setServer(null);
        Preferences.setLocalAddress(null);
        Preferences.setUser(null);

        // TODO Enter all settings to be reset
        Preferences.setOpenSubsonic(false);
        Preferences.setPlaybackSpeed(Constants.MEDIA_PLAYBACK_SPEED_100);
        Preferences.setSkipSilenceMode(false);
        Preferences.setDataSavingMode(false);
        Preferences.setStarredSyncEnabled(false);
    }

    private void resetMusicSession() {
        MediaManager.reset(getMediaBrowserListenableFuture());
    }

    private void hideMusicSession() {
        MediaManager.hide(getMediaBrowserListenableFuture());
    }

    private void resetViewModel() {
        this.getViewModelStore().clear();
    }

    // CONNECTION
    private void connectivityStatusReceiverManager(boolean isActive) {
        if (isActive) {
            IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
            registerReceiver(connectivityStatusBroadcastReceiver, filter);
        } else {
            unregisterReceiver(connectivityStatusBroadcastReceiver);
        }
    }

    private void pingServer() {
        String user = Preferences.getUser();
        String token = Preferences.getToken();
        String password = Preferences.getPassword();
        if (user == null || user.isEmpty()) return;
        if ((token == null || token.isEmpty()) && (password == null || password.isEmpty())) return;

        if (Preferences.isInUseServerAddressLocal()) {
            mainViewModel.ping().observe(this, subsonicResponse -> {
                if (subsonicResponse == null) {
                    Preferences.setServerSwitchableTimer();
                    Preferences.switchInUseServerAddress();
                    App.refreshSubsonicClient();
                    pingServer();
                } else {
                    Preferences.setOpenSubsonic(subsonicResponse.getOpenSubsonic() != null && subsonicResponse.getOpenSubsonic());
                }
            });
        } else {
            if (Preferences.isServerSwitchable()) {
                Preferences.setServerSwitchableTimer();
                Preferences.switchInUseServerAddress();
                App.refreshSubsonicClient();
                pingServer();
            } else {
                mainViewModel.ping().observe(this, subsonicResponse -> {
                    if (subsonicResponse == null) {
                        ServerStatus.markUnreachable();
                        if (Preferences.showServerUnreachableDialog()) {
                            ServerUnreachableDialog dialog = new ServerUnreachableDialog();
                            dialog.show(getSupportFragmentManager(), null);
                        }
                    } else {
                        ServerStatus.markReachable();
                        Preferences.setOpenSubsonic(subsonicResponse.getOpenSubsonic() != null && subsonicResponse.getOpenSubsonic());
                    }
                });
            }
        }
    }

    public void updateOfflineBanner() {
        if (bind == null) return;
        boolean hasInternet = NetworkUtil.hasInternet();
        boolean serverReachable = ServerStatus.isReachable();

        if (!hasInternet) {
            bind.offlineModeTextView.setText(R.string.activity_info_offline_mode);
            bind.offlineModeTextView.setVisibility(View.VISIBLE);
            return;
        }

        if (!serverReachable) {
            bind.offlineModeTextView.setText(R.string.activity_info_server_unreachable);
            bind.offlineModeTextView.setVisibility(View.VISIBLE);
            return;
        }

        bind.offlineModeTextView.setVisibility(View.GONE);
        MetadataSyncManager.startIfNeeded(this);
    }

    private void getOpenSubsonicExtensions() {
        if (Preferences.getToken() != null) {
            mainViewModel.getOpenSubsonicExtensions().observe(this, openSubsonicExtensions -> {
                if (openSubsonicExtensions != null) {
                    Preferences.setOpenSubsonicExtensions(openSubsonicExtensions);
                }
            });
        }
    }

    private void checkTempoUpdate() {
        if (BuildConfig.APPLICATION_ID != null
                && BuildConfig.APPLICATION_ID.contains("tempo")
                && Preferences.showTempoUpdateDialog()) {
            mainViewModel.checkTempoUpdate().observe(this, latestRelease -> {
                if (latestRelease != null && UpdateUtil.showUpdateDialog(latestRelease)) {
                    GithubTempoUpdateDialog dialog = new GithubTempoUpdateDialog(latestRelease);
                    dialog.show(getSupportFragmentManager(), null);
                }
            });
        }
    }

    private void checkConnectionType() {
        if (Preferences.isWifiOnly()) {
            ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();

            if (networkInfo != null && networkInfo.getType() != ConnectivityManager.TYPE_WIFI) {
                ConnectionAlertDialog dialog = new ConnectionAlertDialog();
                dialog.show(getSupportFragmentManager(), null);
            }
        }
    }
}
