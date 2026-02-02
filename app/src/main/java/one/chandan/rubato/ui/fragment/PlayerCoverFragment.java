package one.chandan.rubato.ui.fragment;

import android.content.ComponentName;
import android.os.Bundle;
import android.os.Handler;
import android.transition.Fade;
import android.transition.Transition;
import android.transition.TransitionManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaBrowser;
import androidx.media3.session.SessionToken;

import one.chandan.rubato.R;
import one.chandan.rubato.databinding.InnerFragmentPlayerCoverBinding;
import one.chandan.rubato.glide.CustomGlideRequest;
import one.chandan.rubato.model.Download;
import one.chandan.rubato.service.MediaManager;
import one.chandan.rubato.service.MediaService;
import one.chandan.rubato.service.VisualizerManager;
import one.chandan.rubato.ui.dialog.PlaylistChooserDialog;
import one.chandan.rubato.ui.dialog.VisualizerSettingsDialog;
import one.chandan.rubato.util.Constants;
import one.chandan.rubato.util.DownloadUtil;
import one.chandan.rubato.util.MappingUtil;
import one.chandan.rubato.util.Preferences;
import one.chandan.rubato.viewmodel.PlayerBottomSheetViewModel;
import com.google.android.material.snackbar.Snackbar;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import androidx.media3.common.C;

@UnstableApi
public class PlayerCoverFragment extends Fragment {
    private PlayerBottomSheetViewModel playerBottomSheetViewModel;
    private InnerFragmentPlayerCoverBinding bind;
    private ListenableFuture<MediaBrowser> mediaBrowserListenableFuture;
    private VisualizerManager visualizerManager;
    private int audioSessionId = C.AUDIO_SESSION_ID_UNSET;
    private boolean isPlaying = false;
    private boolean isMusic = true;
    private CoverMode coverMode = CoverMode.COVER;

    private final Handler handler = new Handler();

    private enum CoverMode {
        COVER,
        VISUALIZER
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        bind = InnerFragmentPlayerCoverBinding.inflate(inflater, container, false);
        View view = bind.getRoot();

        playerBottomSheetViewModel = new ViewModelProvider(requireActivity()).get(PlayerBottomSheetViewModel.class);

        initOverlay();
        initInnerButton();
        initVisualizer();

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        initializeBrowser();
        bindMediaController();
        toggleOverlayVisibility(false);
    }

    @Override
    public void onStop() {
        releaseBrowser();
        if (visualizerManager != null) {
            visualizerManager.release();
        }
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        bind = null;
    }

    private void initTapButtonHideTransition() {
        bind.nowPlayingTapButton.setVisibility(View.VISIBLE);

        handler.removeCallbacksAndMessages(null);

        final Runnable runnable = () -> {
            if (bind != null) bind.nowPlayingTapButton.setVisibility(View.GONE);
        };

        handler.postDelayed(runnable, 10000);
    }

    private void initOverlay() {
        bind.nowPlayingSongCoverImageView.setOnClickListener(view -> toggleOverlayVisibility(true));
        bind.nowPlayingSongCoverButtonGroup.setOnClickListener(view -> toggleOverlayVisibility(false));
        bind.nowPlayingTapButton.setOnClickListener(view -> toggleOverlayVisibility(true));
    }

    private void toggleOverlayVisibility(boolean isVisible) {
        Transition transition = new Fade();
        transition.setDuration(200);
        transition.addTarget(bind.nowPlayingSongCoverButtonGroup);

        TransitionManager.beginDelayedTransition(bind.getRoot(), transition);
        bind.nowPlayingSongCoverButtonGroup.setVisibility(isVisible ? View.VISIBLE : View.GONE);
        bind.nowPlayingTapButton.setVisibility(isVisible ? View.GONE : View.VISIBLE);

        bind.innerButtonBottomRight.setVisibility(Preferences.isSyncronizationEnabled() ? View.VISIBLE : View.GONE);
        bind.innerButtonBottomRightAlternative.setVisibility(Preferences.isSyncronizationEnabled() ? View.GONE : View.VISIBLE);
        bind.nowPlayingVisualizerSettingsButton.setVisibility(
                isVisible ? View.GONE : (shouldShowVisualizerSettings() ? View.VISIBLE : View.GONE)
        );

        if (!isVisible) initTapButtonHideTransition();
    }

    private void initInnerButton() {
        playerBottomSheetViewModel.getLiveMedia().observe(getViewLifecycleOwner(), song -> {
            if (song != null && bind != null) {
                bind.innerButtonTopLeft.setOnClickListener(view -> {
                    DownloadUtil.getDownloadTracker(requireContext()).download(
                            MappingUtil.mapDownload(song),
                            new Download(song)
                    );
                });

                bind.innerButtonTopRight.setOnClickListener(view -> {
                            Bundle bundle = new Bundle();
                            bundle.putParcelable(Constants.TRACK_OBJECT, song);

                            PlaylistChooserDialog dialog = new PlaylistChooserDialog();
                            dialog.setArguments(bundle);
                            dialog.show(requireActivity().getSupportFragmentManager(), null);
                        }
                );

                bind.innerButtonBottomLeft.setOnClickListener(view -> {
                    playerBottomSheetViewModel.getMediaInstantMix(getViewLifecycleOwner(), song).observe(getViewLifecycleOwner(), media -> {
                        MediaManager.enqueue(mediaBrowserListenableFuture, media, true);
                    });
                });

                bind.innerButtonBottomRight.setOnClickListener(view -> {
                    if (playerBottomSheetViewModel.savePlayQueue()) {
                        Snackbar.make(requireView(), "Salvato", Snackbar.LENGTH_LONG).show();
                    }
                });

                bind.innerButtonBottomRightAlternative.setOnClickListener(view -> {
                    if (getActivity() != null) {
                        PlayerBottomSheetFragment playerBottomSheetFragment = (PlayerBottomSheetFragment) requireActivity().getSupportFragmentManager().findFragmentByTag("PlayerBottomSheet");
                        if (playerBottomSheetFragment != null) {
                            playerBottomSheetFragment.goToLyricsPage();
                        }
                    }
                });
            }
        });
    }

    private void initVisualizer() {
        visualizerManager = new VisualizerManager();
        visualizerManager.setListener(new VisualizerManager.Listener() {
            @Override
            public void onWaveformData(byte[] waveform, int samplingRate) {
                if (bind != null) {
                    bind.nowPlayingVisualizerView.setWaveform(waveform);
                }
            }

            @Override
            public void onError(Exception exception) {
                if (getView() != null) {
                    Snackbar.make(getView(), getString(R.string.visualizer_error_init), Snackbar.LENGTH_LONG).show();
                }
            }
        });

        bind.innerButtonVisualizer.setOnClickListener(view -> toggleVisualizerMode());
        bind.nowPlayingVisualizerSettingsButton.setOnClickListener(view -> openVisualizerSettings());

        getParentFragmentManager().setFragmentResultListener(
                VisualizerSettingsDialog.RESULT_KEY,
                this,
                (requestKey, result) -> applyVisualizerPreferences()
        );

        applyVisualizerPreferences();
        updateVisualizerUi();
    }

    private void initializeBrowser() {
        mediaBrowserListenableFuture = new MediaBrowser.Builder(requireContext(), new SessionToken(requireContext(), new ComponentName(requireContext(), MediaService.class))).buildAsync();
    }

    private void releaseBrowser() {
        MediaBrowser.releaseFuture(mediaBrowserListenableFuture);
    }

    private void bindMediaController() {
        mediaBrowserListenableFuture.addListener(() -> {
            try {
                MediaBrowser mediaBrowser = mediaBrowserListenableFuture.get();
                setMediaBrowserListener(mediaBrowser);
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        }, MoreExecutors.directExecutor());
    }

    private void setMediaBrowserListener(MediaBrowser mediaBrowser) {
        setCover(mediaBrowser.getMediaMetadata());
        updateMediaType(mediaBrowser.getMediaMetadata());
        isPlaying = mediaBrowser.isPlaying();
        updateVisualizerState();

        mediaBrowser.addListener(new Player.Listener() {
            @Override
            public void onMediaMetadataChanged(@NonNull MediaMetadata mediaMetadata) {
                setCover(mediaMetadata);
                updateMediaType(mediaMetadata);
                toggleOverlayVisibility(false);
            }

            @Override
            public void onAudioSessionIdChanged(int audioSessionId) {
                updateAudioSessionId(audioSessionId);
            }

            @Override
            public void onIsPlayingChanged(boolean isPlayingNow) {
                isPlaying = isPlayingNow;
                updateVisualizerState();
            }
        });
    }

    private void setCover(MediaMetadata mediaMetadata) {
        CustomGlideRequest.Builder
                .from(requireContext(), mediaMetadata.extras != null ? mediaMetadata.extras.getString("coverArtId") : null, CustomGlideRequest.ResourceType.NowPlaying)
                .build()
                .into(bind.nowPlayingSongCoverImageView);
    }

    private void toggleVisualizerMode() {
        if (!Preferences.isVisualizerEnabled()) {
            if (getView() != null) {
                Snackbar.make(getView(), getString(R.string.visualizer_disabled_message), Snackbar.LENGTH_LONG).show();
            }
            return;
        }
        boolean canUseSession = audioSessionId != C.AUDIO_SESSION_ID_UNSET && audioSessionId != 0;
        if (!canUseSession && coverMode != CoverMode.VISUALIZER) {
            if (getView() != null) {
                Snackbar.make(getView(), getString(R.string.visualizer_unavailable_session), Snackbar.LENGTH_SHORT).show();
            }
            return;
        }
        if (coverMode == CoverMode.VISUALIZER) {
            coverMode = CoverMode.COVER;
        } else {
            coverMode = CoverMode.VISUALIZER;
        }
        updateVisualizerUi();
        toggleOverlayVisibility(false);
    }

    private void openVisualizerSettings() {
        VisualizerSettingsDialog dialog = new VisualizerSettingsDialog();
        dialog.show(getParentFragmentManager(), "VisualizerSettingsDialog");
    }

    private void applyVisualizerPreferences() {
        if (bind == null) return;
        int barCount = Preferences.getVisualizerBarCount();
        float opacity = Preferences.getVisualizerOpacity();
        float smoothing = Preferences.getVisualizerSmoothing();
        float scale = Preferences.getVisualizerScale();
        int fps = Preferences.getVisualizerFps();
        int colorMode = "gradient".equals(Preferences.getVisualizerColorMode())
                ? one.chandan.rubato.ui.view.VisualizerView.COLOR_MODE_GRADIENT
                : one.chandan.rubato.ui.view.VisualizerView.COLOR_MODE_ACCENT;
        String modePreference = Preferences.getVisualizerMode();
        int mode = one.chandan.rubato.ui.view.VisualizerView.MODE_BARS;
        if ("line".equals(modePreference)) {
            mode = one.chandan.rubato.ui.view.VisualizerView.MODE_LINE;
        } else if ("dots".equals(modePreference)) {
            mode = one.chandan.rubato.ui.view.VisualizerView.MODE_DOTS;
        }
        boolean peakCaps = Preferences.isVisualizerPeakCapsEnabled();

        bind.nowPlayingVisualizerView.setConfig(barCount, opacity, smoothing, scale, fps, colorMode, peakCaps, mode);

        visualizerManager.setTargetFps(fps);
        visualizerManager.setCaptureSize(barCount >= 64 ? 512 : 256);
        updateVisualizerUi();
    }

    private void updateMediaType(MediaMetadata mediaMetadata) {
        if (mediaMetadata.extras != null) {
            isMusic = Constants.MEDIA_TYPE_MUSIC.equals(mediaMetadata.extras.getString("type", Constants.MEDIA_TYPE_MUSIC));
        } else {
            isMusic = true;
        }
        updateVisualizerUi();
    }

    private void updateAudioSessionId(int audioSessionId) {
        this.audioSessionId = audioSessionId;
        if (visualizerManager != null) {
            visualizerManager.setAudioSessionId(audioSessionId);
        }
        updateVisualizerState();
    }

    private void updateVisualizerUi() {
        if (bind == null) return;
        if (!Preferences.isVisualizerEnabled()) {
            coverMode = CoverMode.COVER;
        }
        boolean showVisualizer = coverMode == CoverMode.VISUALIZER;
        bind.nowPlayingVisualizerView.setVisibility(showVisualizer ? View.VISIBLE : View.GONE);
        bind.nowPlayingVisualizerSettingsButton.setVisibility(shouldShowVisualizerSettings() ? View.VISIBLE : View.GONE);
        bind.innerButtonVisualizer.setVisibility(isMusic && Preferences.isVisualizerEnabled() ? View.VISIBLE : View.GONE);

        if (!isMusic) {
            coverMode = CoverMode.COVER;
            bind.nowPlayingVisualizerView.setVisibility(View.GONE);
            bind.nowPlayingVisualizerSettingsButton.setVisibility(View.GONE);
        }
        updateVisualizerState();
    }

    private void updateVisualizerState() {
        if (bind == null || visualizerManager == null) return;
        boolean canUseSession = audioSessionId != C.AUDIO_SESSION_ID_UNSET && audioSessionId != 0;
        boolean shouldEnable = coverMode == CoverMode.VISUALIZER
                && Preferences.isVisualizerEnabled()
                && isPlaying
                && isMusic
                && canUseSession;

        visualizerManager.setEnabled(shouldEnable);
        bind.nowPlayingVisualizerView.setActive(shouldEnable);
    }

    private boolean shouldShowVisualizerSettings() {
        return coverMode == CoverMode.VISUALIZER && Preferences.isVisualizerEnabled();
    }
}
