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
import one.chandan.rubato.util.MusicUtil;
import one.chandan.rubato.util.Preferences;
import one.chandan.rubato.util.AudioSessionStore;
import one.chandan.rubato.viewmodel.PlayerBottomSheetViewModel;
import com.google.android.material.snackbar.Snackbar;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import androidx.media3.common.C;

@UnstableApi
public class PlayerCoverFragment extends Fragment {
    private enum CoverMode {
        COVER,
        VISUALIZER
    }
    private PlayerBottomSheetViewModel playerBottomSheetViewModel;
    private InnerFragmentPlayerCoverBinding bind;
    private ListenableFuture<MediaBrowser> mediaBrowserListenableFuture;
    private final OverlayController overlayController = new OverlayController();
    private final VisualizerController visualizerController = new VisualizerController();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        bind = InnerFragmentPlayerCoverBinding.inflate(inflater, container, false);
        View view = bind.getRoot();

        playerBottomSheetViewModel = new ViewModelProvider(requireActivity()).get(PlayerBottomSheetViewModel.class);

        overlayController.init();
        initInnerButton();
        visualizerController.init();

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        initializeBrowser();
        bindMediaController();
        overlayController.showOverlay(false);
    }

    @Override
    public void onStop() {
        releaseBrowser();
        overlayController.clear();
        visualizerController.release();
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        overlayController.clear();
        super.onDestroyView();
        bind = null;
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
                        if (media == null) {
                            return;
                        }
                        if (media.isEmpty()) {
                            if (getView() != null) {
                                Snackbar.make(getView(), R.string.instant_mix_empty_snackbar, Snackbar.LENGTH_LONG).show();
                            }
                            return;
                        }
                        MusicUtil.ratingFilter(media);
                        String currentId = song.getId();
                        if (currentId != null) {
                            media.removeIf(item -> item != null && currentId.equals(item.getId()));
                        }
                        if (media.isEmpty()) {
                            if (getView() != null) {
                                Snackbar.make(getView(), R.string.instant_mix_empty_snackbar, Snackbar.LENGTH_LONG).show();
                            }
                            return;
                        }
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
        visualizerController.updateMediaType(mediaBrowser.getMediaMetadata());
        visualizerController.setPlaying(mediaBrowser.isPlaying());
        mediaBrowser.addListener(new Player.Listener() {
            @Override
            public void onMediaMetadataChanged(@NonNull MediaMetadata mediaMetadata) {
                setCover(mediaMetadata);
                visualizerController.updateMediaType(mediaMetadata);
                overlayController.showOverlay(false);
            }

            @Override
            public void onAudioSessionIdChanged(int audioSessionId) {
                visualizerController.updateAudioSessionId(audioSessionId);
            }

            @Override
            public void onIsPlayingChanged(boolean isPlayingNow) {
                visualizerController.setPlaying(isPlayingNow);
            }
        });
    }

    private void setCover(MediaMetadata mediaMetadata) {
        CustomGlideRequest.Builder
                .from(requireContext(), mediaMetadata.extras != null ? mediaMetadata.extras.getString("coverArtId") : null, CustomGlideRequest.ResourceType.NowPlaying)
                .build()
                .into(bind.nowPlayingSongCoverImageView);
    }

    private class OverlayController {
        private final Handler handler = new Handler();
        private Runnable tapButtonHideRunnable;
        private Runnable subtitleRevealRunnable;

        void init() {
            bind.nowPlayingSongCoverImageView.setOnClickListener(view -> showOverlay(true));
            bind.nowPlayingSongCoverButtonGroup.setOnClickListener(view -> showOverlay(false));
            bind.nowPlayingTapButton.setOnClickListener(view -> showOverlay(true));
        }

        void showOverlay(boolean isVisible) {
            if (bind == null) return;
            handler.removeCallbacksAndMessages(null);

            Transition transition = new Fade();
            transition.setDuration(200);
            transition.addTarget(bind.nowPlayingSongCoverButtonGroup);

            TransitionManager.beginDelayedTransition(bind.getRoot(), transition);
            bind.nowPlayingSongCoverButtonGroup.setVisibility(isVisible ? View.VISIBLE : View.GONE);
            bind.nowPlayingTapButton.setVisibility(isVisible ? View.GONE : View.VISIBLE);

            bind.innerButtonBottomRight.setVisibility(Preferences.isSyncronizationEnabled() ? View.VISIBLE : View.GONE);
            bind.innerButtonBottomRightAlternative.setVisibility(Preferences.isSyncronizationEnabled() ? View.GONE : View.VISIBLE);
            bind.innerLabelLyrics.setVisibility(View.GONE);
            bind.innerLabelTopLeft.setVisibility(View.GONE);
            bind.innerLabelTopRight.setVisibility(View.GONE);
            bind.innerLabelVisualizer.setVisibility(View.GONE);
            visualizerController.updateSettingsButtonVisibility(isVisible);

            if (!isVisible) {
                scheduleTapButtonHide();
                cancelSubtitleReveal();
            } else {
                scheduleSubtitleReveal();
            }
        }

        void clear() {
            handler.removeCallbacksAndMessages(null);
            tapButtonHideRunnable = null;
            subtitleRevealRunnable = null;
        }

        private void scheduleTapButtonHide() {
            if (bind == null) return;
            bind.nowPlayingTapButton.setVisibility(View.VISIBLE);
            tapButtonHideRunnable = () -> {
                if (bind != null) bind.nowPlayingTapButton.setVisibility(View.GONE);
            };
            handler.postDelayed(tapButtonHideRunnable, 10000);
        }

        private void scheduleSubtitleReveal() {
            if (bind == null) return;
            subtitleRevealRunnable = () -> {
                if (bind == null) return;
                if (bind.nowPlayingSongCoverButtonGroup.getVisibility() != View.VISIBLE) return;
                bind.innerLabelTopLeft.setVisibility(View.VISIBLE);
                bind.innerLabelTopRight.setVisibility(View.VISIBLE);
                bind.innerLabelVisualizer.setVisibility(View.VISIBLE);
                if (bind.innerLabelSongRadio != null) {
                    bind.innerLabelSongRadio.setVisibility(View.VISIBLE);
                }
                if (bind.innerButtonBottomRightAlternative.getVisibility() == View.VISIBLE) {
                    bind.innerLabelLyrics.setVisibility(View.VISIBLE);
                }
            };
            handler.postDelayed(subtitleRevealRunnable, 1000);
        }

        private void cancelSubtitleReveal() {
            if (subtitleRevealRunnable != null) {
                handler.removeCallbacks(subtitleRevealRunnable);
                subtitleRevealRunnable = null;
            }
            if (bind != null) {
                bind.innerLabelLyrics.setVisibility(View.GONE);
                bind.innerLabelTopLeft.setVisibility(View.GONE);
                bind.innerLabelTopRight.setVisibility(View.GONE);
                bind.innerLabelVisualizer.setVisibility(View.GONE);
                if (bind.innerLabelSongRadio != null) {
                    bind.innerLabelSongRadio.setVisibility(View.GONE);
                }
            }
        }
    }

    private class VisualizerController {
        private final VisualizerManager visualizerManager = new VisualizerManager();
        private int audioSessionId = C.AUDIO_SESSION_ID_UNSET;
        private boolean isPlaying = false;
        private boolean isMusic = true;
        private CoverMode coverMode = CoverMode.COVER;

        void init() {
            visualizerManager.setListener(new VisualizerManager.Listener() {
                @Override
                public void onWaveformData(byte[] waveform, int samplingRate) {
                    if (bind != null) {
                        bind.nowPlayingVisualizerView.setWaveform(waveform);
                    }
                }

                @Override
                public void onFftData(byte[] fft, int samplingRate) {
                    if (bind != null) {
                        bind.nowPlayingVisualizerView.setFft(fft, samplingRate);
                    }
                }

                @Override
                public void onError(Exception exception) {
                    if (getView() != null) {
                        int message = exception instanceof SecurityException
                                ? R.string.visualizer_permission_required
                                : R.string.visualizer_error_init;
                        Snackbar.make(getView(), getString(message), Snackbar.LENGTH_LONG).show();
                    }
                }
            });

            bind.innerButtonVisualizer.setOnClickListener(view -> toggleVisualizerMode());
            bind.nowPlayingVisualizerSettingsButton.setOnClickListener(view -> openVisualizerSettings());

            AudioSessionStore.getAudioSessionId().observe(getViewLifecycleOwner(), sessionId -> {
                int resolved = sessionId != null ? sessionId : C.AUDIO_SESSION_ID_UNSET;
                updateAudioSessionId(resolved);
            });

            getParentFragmentManager().setFragmentResultListener(
                    VisualizerSettingsDialog.RESULT_KEY,
                    PlayerCoverFragment.this,
                    (requestKey, result) -> applyVisualizerPreferences()
            );

            applyVisualizerPreferences();
            updateVisualizerUi();
        }

        void release() {
            visualizerManager.release();
        }

        void updateMediaType(MediaMetadata mediaMetadata) {
            if (mediaMetadata.extras != null) {
                String type = mediaMetadata.extras.getString("type", Constants.MEDIA_TYPE_MUSIC);
                isMusic = Constants.MEDIA_TYPE_MUSIC.equals(type) || Constants.MEDIA_TYPE_LOCAL.equals(type);
            } else {
                isMusic = true;
            }
            updateVisualizerUi();
        }

        void updateAudioSessionId(int audioSessionId) {
            this.audioSessionId = audioSessionId;
            visualizerManager.setAudioSessionId(audioSessionId);
            updateVisualizerState();
        }

        void setPlaying(boolean isPlaying) {
            this.isPlaying = isPlaying;
            updateVisualizerState();
        }

        void updateSettingsButtonVisibility(boolean overlayVisible) {
            if (bind == null) return;
            bind.nowPlayingVisualizerSettingsButton.setVisibility(
                    overlayVisible ? View.GONE : (shouldShowVisualizerSettings() ? View.VISIBLE : View.GONE)
            );
        }

        private void toggleVisualizerMode() {
            if (!Preferences.isVisualizerEnabled()) {
                if (getView() != null) {
                    Snackbar.make(getView(), getString(R.string.visualizer_disabled_message), Snackbar.LENGTH_LONG).show();
                }
                return;
            }
            coverMode = coverMode == CoverMode.VISUALIZER ? CoverMode.COVER : CoverMode.VISUALIZER;
            updateVisualizerUi();
            overlayController.showOverlay(false);
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
            int captureSize = barCount >= 64 ? 1024 : (barCount >= 48 ? 512 : 256);
            visualizerManager.setCaptureSize(captureSize);
            updateVisualizerUi();
        }

        private void updateVisualizerUi() {
            if (bind == null) return;
            if (!Preferences.isVisualizerEnabled()) {
                coverMode = CoverMode.COVER;
            }
            boolean showVisualizer = coverMode == CoverMode.VISUALIZER;
            bind.nowPlayingVisualizerView.setVisibility(showVisualizer ? View.VISIBLE : View.GONE);
            updateSettingsButtonVisibility(isOverlayVisible());
            bind.innerButtonVisualizer.setVisibility(isMusic && Preferences.isVisualizerEnabled() ? View.VISIBLE : View.GONE);

            if (!isMusic) {
                coverMode = CoverMode.COVER;
                bind.nowPlayingVisualizerView.setVisibility(View.GONE);
                bind.nowPlayingVisualizerSettingsButton.setVisibility(View.GONE);
            }
            updateVisualizerState();
        }

        private void updateVisualizerState() {
            if (bind == null) return;
            boolean canUseSession = audioSessionId != C.AUDIO_SESSION_ID_UNSET && audioSessionId != 0;
            boolean shouldEnableCapture = coverMode == CoverMode.VISUALIZER
                    && Preferences.isVisualizerEnabled()
                    && isPlaying
                    && isMusic
                    && canUseSession;

            visualizerManager.setEnabled(shouldEnableCapture);
            boolean shouldRender = coverMode == CoverMode.VISUALIZER
                    && Preferences.isVisualizerEnabled()
                    && isMusic;
            bind.nowPlayingVisualizerView.setActive(shouldRender);
        }

        private boolean shouldShowVisualizerSettings() {
            return coverMode == CoverMode.VISUALIZER && Preferences.isVisualizerEnabled();
        }

        private boolean isOverlayVisible() {
            return bind != null && bind.nowPlayingSongCoverButtonGroup.getVisibility() == View.VISIBLE;
        }

    }

}
