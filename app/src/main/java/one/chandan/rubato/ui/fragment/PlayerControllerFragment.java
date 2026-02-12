package one.chandan.rubato.ui.fragment;

import android.content.ComponentName;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.util.RepeatModeUtil;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaBrowser;
import androidx.media3.session.SessionToken;
import androidx.navigation.fragment.NavHostFragment;
import androidx.viewpager2.widget.ViewPager2;

import one.chandan.rubato.R;
import one.chandan.rubato.databinding.InnerFragmentPlayerControllerBinding;
import one.chandan.rubato.service.MediaService;
import one.chandan.rubato.ui.activity.MainActivity;
import one.chandan.rubato.ui.dialog.RatingDialog;
import one.chandan.rubato.ui.dialog.TrackInfoDialog;
import one.chandan.rubato.ui.fragment.pager.PlayerControllerHorizontalPager;
import one.chandan.rubato.repository.LocalMusicRepository;
import one.chandan.rubato.subsonic.models.Child;
import one.chandan.rubato.util.Constants;
import one.chandan.rubato.util.MusicUtil;
import one.chandan.rubato.util.OfflinePolicy;
import one.chandan.rubato.util.Preferences;
import one.chandan.rubato.util.SearchIndexUtil;
import one.chandan.rubato.viewmodel.PlayerBottomSheetViewModel;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.elevation.SurfaceColors;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.Date;
import java.util.Objects;

@UnstableApi
public class PlayerControllerFragment extends Fragment {
    private static final String TAG = "PlayerCoverFragment";

    private InnerFragmentPlayerControllerBinding bind;
    private ViewPager2 playerMediaCoverViewPager;
    private ToggleButton buttonFavorite;
    private TextView playerMediaTitleLabel;
    private TextView playerArtistNameLabel;
    private Button playbackSpeedButton;
    private ToggleButton skipSilenceToggleButton;
    private Chip playerMediaExtension;
    private TextView playerMediaBitrate;
    private ConstraintLayout playerQuickActionView;
    private ImageButton playerOpenQueueButton;
    private ImageButton playerTrackInfo;

    private MainActivity activity;
    private PlayerBottomSheetViewModel playerBottomSheetViewModel;
    private ListenableFuture<MediaBrowser> mediaBrowserListenableFuture;
    private Child currentMedia;
    private Child fallbackMedia;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();

        bind = InnerFragmentPlayerControllerBinding.inflate(inflater, container, false);
        View view = bind.getRoot();

        playerBottomSheetViewModel = new ViewModelProvider(requireActivity()).get(PlayerBottomSheetViewModel.class);

        init();
        initQuickActionView();
        initCoverLyricsSlideView();
        initMediaListenable();
        initMediaLabelButton();
        initArtistLabelButton();

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        initializeBrowser();
        bindMediaController();
    }

    @Override
    public void onStop() {
        releaseBrowser();
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        bind = null;
    }

    private void init() {
        playerMediaCoverViewPager = bind.getRoot().findViewById(R.id.player_media_cover_view_pager);
        buttonFavorite = bind.getRoot().findViewById(R.id.button_favorite);
        playerMediaTitleLabel = bind.getRoot().findViewById(R.id.player_media_title_label);
        playerArtistNameLabel = bind.getRoot().findViewById(R.id.player_artist_name_label);
        playbackSpeedButton = bind.getRoot().findViewById(R.id.player_playback_speed_button);
        skipSilenceToggleButton = bind.getRoot().findViewById(R.id.player_skip_silence_toggle_button);
        playerMediaExtension = bind.getRoot().findViewById(R.id.player_media_extension);
        playerMediaBitrate = bind.getRoot().findViewById(R.id.player_media_bitrate);
        playerQuickActionView = bind.getRoot().findViewById(R.id.player_quick_action_view);
        playerOpenQueueButton = bind.getRoot().findViewById(R.id.player_open_queue_button);
        playerTrackInfo = bind.getRoot().findViewById(R.id.player_info_track);
    }

    private void initQuickActionView() {
        playerQuickActionView.setBackgroundColor(SurfaceColors.getColorForElevation(requireContext(), 8));

        playerOpenQueueButton.setOnClickListener(view -> {
            PlayerBottomSheetFragment playerBottomSheetFragment = (PlayerBottomSheetFragment) requireActivity().getSupportFragmentManager().findFragmentByTag("PlayerBottomSheet");
            if (playerBottomSheetFragment != null) {
                playerBottomSheetFragment.goToQueuePage();
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

                bind.nowPlayingMediaControllerView.setPlayer(mediaBrowser);

                setMediaControllerListener(mediaBrowser);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, MoreExecutors.directExecutor());
    }

    private void setMediaControllerListener(MediaBrowser mediaBrowser) {
        setMediaControllerUI(mediaBrowser);
        setMetadata(mediaBrowser.getMediaMetadata());
        setMediaInfo(mediaBrowser.getMediaMetadata());

        mediaBrowser.addListener(new Player.Listener() {
            @Override
            public void onMediaMetadataChanged(@NonNull MediaMetadata mediaMetadata) {
                setMediaControllerUI(mediaBrowser);
                setMetadata(mediaMetadata);
                setMediaInfo(mediaMetadata);
            }
        });
    }

    private void setMetadata(MediaMetadata mediaMetadata) {
        updateFallbackMedia(mediaMetadata);
        playerMediaTitleLabel.setText(String.valueOf(mediaMetadata.title));
        playerArtistNameLabel.setText(String.valueOf(mediaMetadata.artist));

        playerMediaTitleLabel.setSelected(true);
        playerArtistNameLabel.setSelected(true);

        playerMediaTitleLabel.setVisibility(mediaMetadata.title != null && !Objects.equals(mediaMetadata.title, "") ? View.VISIBLE : View.GONE);
        playerArtistNameLabel.setVisibility(mediaMetadata.artist != null && !Objects.equals(mediaMetadata.artist, "") ? View.VISIBLE : View.GONE);
        syncFavoriteState();
    }

    private void setMediaInfo(MediaMetadata mediaMetadata) {
        if (mediaMetadata.extras != null) {
            String extension = mediaMetadata.extras.getString("suffix", "Unknown format");
            String bitrate = mediaMetadata.extras.getInt("bitrate", 0) != 0 ? mediaMetadata.extras.getInt("bitrate", 0) + "kbps" : "Original";

            playerMediaExtension.setText(extension);

            if (bitrate.equals("Original")) {
                playerMediaBitrate.setVisibility(View.GONE);
            } else {
                playerMediaBitrate.setVisibility(View.VISIBLE);
                playerMediaBitrate.setText(bitrate);
            }
        }

        boolean isTranscodingExtension = !MusicUtil.getTranscodingFormatPreference().equals("raw");
        boolean isTranscodingBitrate = !MusicUtil.getBitratePreference().equals("0");

        if (isTranscodingExtension || isTranscodingBitrate) {
            playerMediaExtension.setText("Transcoding");
            playerMediaBitrate.setText("requested");
        }

        playerTrackInfo.setOnClickListener(view -> {
            TrackInfoDialog dialog = new TrackInfoDialog(mediaMetadata);
            dialog.show(activity.getSupportFragmentManager(), null);
        });
    }

    private void setMediaControllerUI(MediaBrowser mediaBrowser) {
        initPlaybackSpeedButton(mediaBrowser);

        if (mediaBrowser.getMediaMetadata().extras != null) {
            switch (mediaBrowser.getMediaMetadata().extras.getString("type", Constants.MEDIA_TYPE_MUSIC)) {
                case Constants.MEDIA_TYPE_PODCAST:
                    bind.getRoot().setShowShuffleButton(false);
                    bind.getRoot().setShowRewindButton(true);
                    bind.getRoot().setShowPreviousButton(false);
                    bind.getRoot().setShowNextButton(false);
                    bind.getRoot().setShowFastForwardButton(true);
                    bind.getRoot().setRepeatToggleModes(RepeatModeUtil.REPEAT_TOGGLE_MODE_NONE);
                    bind.getRoot().findViewById(R.id.player_playback_speed_button).setVisibility(View.VISIBLE);
                    bind.getRoot().findViewById(R.id.player_skip_silence_toggle_button).setVisibility(View.VISIBLE);
                    bind.getRoot().findViewById(R.id.button_favorite).setVisibility(View.GONE);
                    setPlaybackParameters(mediaBrowser);
                    break;
                case Constants.MEDIA_TYPE_RADIO:
                    bind.getRoot().setShowShuffleButton(false);
                    bind.getRoot().setShowRewindButton(false);
                    bind.getRoot().setShowPreviousButton(false);
                    bind.getRoot().setShowNextButton(false);
                    bind.getRoot().setShowFastForwardButton(false);
                    bind.getRoot().setRepeatToggleModes(RepeatModeUtil.REPEAT_TOGGLE_MODE_NONE);
                    bind.getRoot().findViewById(R.id.player_playback_speed_button).setVisibility(View.GONE);
                    bind.getRoot().findViewById(R.id.player_skip_silence_toggle_button).setVisibility(View.GONE);
                    bind.getRoot().findViewById(R.id.button_favorite).setVisibility(View.GONE);
                    setPlaybackParameters(mediaBrowser);
                    break;
                case Constants.MEDIA_TYPE_MUSIC:
                default:
                    bind.getRoot().setShowShuffleButton(true);
                    bind.getRoot().setShowRewindButton(false);
                    bind.getRoot().setShowPreviousButton(true);
                    bind.getRoot().setShowNextButton(true);
                    bind.getRoot().setShowFastForwardButton(false);
                    bind.getRoot().setRepeatToggleModes(RepeatModeUtil.REPEAT_TOGGLE_MODE_ALL | RepeatModeUtil.REPEAT_TOGGLE_MODE_ONE);
                    bind.getRoot().findViewById(R.id.player_playback_speed_button).setVisibility(View.GONE);
                    bind.getRoot().findViewById(R.id.player_skip_silence_toggle_button).setVisibility(View.GONE);
                    bind.getRoot().findViewById(R.id.button_favorite).setVisibility(View.VISIBLE);
                    resetPlaybackParameters(mediaBrowser);
                    break;
            }
        }
    }

    private void initCoverLyricsSlideView() {
        playerMediaCoverViewPager.setOrientation(ViewPager2.ORIENTATION_HORIZONTAL);
        playerMediaCoverViewPager.setAdapter(new PlayerControllerHorizontalPager(this));

        playerMediaCoverViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);

                PlayerBottomSheetFragment playerBottomSheetFragment = (PlayerBottomSheetFragment) requireActivity().getSupportFragmentManager().findFragmentByTag("PlayerBottomSheet");

                if (position == 0) {
                    activity.setBottomSheetDraggableState(true);

                    if (playerBottomSheetFragment != null) {
                        playerBottomSheetFragment.setPlayerControllerVerticalPagerDraggableState(true);
                    }
                } else if (position == 1) {
                    activity.setBottomSheetDraggableState(false);

                    if (playerBottomSheetFragment != null) {
                        playerBottomSheetFragment.setPlayerControllerVerticalPagerDraggableState(false);
                    }
                }
            }
        });
    }

    private void initMediaListenable() {
        buttonFavorite.setOnClickListener(v -> handleFavoriteClick());
        buttonFavorite.setOnLongClickListener(v -> handleFavoriteLongClick());

        playerBottomSheetViewModel.getLiveMedia().observe(getViewLifecycleOwner(), media -> {
            currentMedia = media;
            if (media != null) {
                buttonFavorite.setChecked(media.getStarred() != null);
                if (getActivity() != null) {
                    playerBottomSheetViewModel.refreshMediaInfo(requireActivity(), media);
                }
            } else {
                syncFavoriteState();
            }
        });
    }

    private void handleFavoriteClick() {
        Child media = resolveFavoriteTarget();
        if (media == null) {
            showFavoriteUnavailable();
            buttonFavorite.setChecked(false);
            return;
        }
        if (!isFavoriteSupported(media)) {
            showFavoriteUnsupported();
            buttonFavorite.setChecked(media.getStarred() != null);
            return;
        }
        playerBottomSheetViewModel.setFavorite(requireContext(), media);
        buttonFavorite.setChecked(media.getStarred() != null);
    }

    private boolean handleFavoriteLongClick() {
        Child media = resolveFavoriteTarget();
        if (media == null) {
            showFavoriteUnavailable();
            return true;
        }
        if (!isFavoriteSupported(media)) {
            showFavoriteUnsupported();
            return true;
        }
        if (!OfflinePolicy.canRate()) {
            Snackbar.make(bind.getRoot(), getString(R.string.queue_add_next_unavailable_offline), Snackbar.LENGTH_SHORT).show();
            return true;
        }
        Bundle bundle = new Bundle();
        bundle.putParcelable(Constants.TRACK_OBJECT, media);

        RatingDialog dialog = new RatingDialog();
        dialog.setArguments(bundle);
        dialog.show(requireActivity().getSupportFragmentManager(), null);
        return true;
    }

    private Child resolveFavoriteTarget() {
        if (currentMedia != null) {
            return currentMedia;
        }
        return fallbackMedia;
    }

    private Child buildChildFromExtras(Bundle extras) {
        String id = extras.getString("id");
        if (id == null || id.trim().isEmpty()) {
            return null;
        }
        Child child = new Child(id);
        child.setParentId(extras.getString("parentId"));
        child.setDir(extras.getBoolean("isDir"));
        child.setTitle(extras.getString("title"));
        child.setAlbum(extras.getString("album"));
        child.setArtist(extras.getString("artist"));
        child.setTrack(extras.getInt("track"));
        child.setYear(extras.getInt("year"));
        child.setGenre(extras.getString("genre"));
        child.setCoverArtId(extras.getString("coverArtId"));
        child.setSize(extras.getLong("size"));
        child.setContentType(extras.getString("contentType"));
        child.setSuffix(extras.getString("suffix"));
        child.setTranscodedContentType(extras.getString("transcodedContentType"));
        child.setTranscodedSuffix(extras.getString("transcodedSuffix"));
        child.setDuration(extras.getInt("duration"));
        child.setBitrate(extras.getInt("bitrate"));
        child.setPath(extras.getString("path"));
        child.setVideo(extras.getBoolean("isVideo"));
        child.setUserRating(extras.getInt("userRating"));
        child.setAverageRating(extras.getDouble("averageRating"));
        child.setPlayCount(extras.getLong("playCount"));
        child.setDiscNumber(extras.getInt("discNumber"));
        child.setAlbumId(extras.getString("albumId"));
        child.setArtistId(extras.getString("artistId"));
        child.setType(extras.getString("type"));
        child.setBookmarkPosition(extras.getLong("bookmarkPosition"));
        child.setOriginalWidth(extras.getInt("originalWidth"));
        child.setOriginalHeight(extras.getInt("originalHeight"));
        long starred = extras.getLong("starred");
        if (starred > 0) {
            child.setStarred(new Date(starred));
        }
        return child;
    }

    private void updateFallbackMedia(MediaMetadata mediaMetadata) {
        Bundle extras = mediaMetadata != null ? mediaMetadata.extras : null;
        String metadataId = extras != null ? extras.getString("id") : null;
        if (currentMedia != null && (metadataId == null || !metadataId.equals(currentMedia.getId()))) {
            currentMedia = null;
        }
        if (extras == null) {
            fallbackMedia = null;
            return;
        }
        fallbackMedia = buildChildFromExtras(extras);
    }

    private boolean isFavoriteSupported(Child media) {
        if (media == null) return false;
        String id = media.getId();
        if (id == null || id.trim().isEmpty()) return false;
        if (SearchIndexUtil.isJellyfinTagged(id)) return false;
        if (LocalMusicRepository.isLocalId(id)) return false;
        return !Constants.MEDIA_TYPE_LOCAL.equals(media.getType());
    }

    private void syncFavoriteState() {
        if (currentMedia != null) {
            buttonFavorite.setChecked(currentMedia.getStarred() != null);
            return;
        }
        if (fallbackMedia != null) {
            buttonFavorite.setChecked(fallbackMedia.getStarred() != null);
            return;
        }
        buttonFavorite.setChecked(false);
    }

    private void showFavoriteUnavailable() {
        Snackbar.make(bind.getRoot(), getString(R.string.favorite_unavailable), Snackbar.LENGTH_SHORT).show();
    }

    private void showFavoriteUnsupported() {
        Snackbar.make(bind.getRoot(), getString(R.string.favorite_unsupported_source), Snackbar.LENGTH_SHORT).show();
    }

    private void initMediaLabelButton() {
        playerBottomSheetViewModel.getLiveAlbum().observe(getViewLifecycleOwner(), album -> {
            if (album != null) {
                playerMediaTitleLabel.setOnClickListener(view -> {
                    Bundle bundle = new Bundle();
                    bundle.putParcelable(Constants.ALBUM_OBJECT, album);
                    NavHostFragment.findNavController(this).navigate(R.id.albumPageFragment, bundle);
                    activity.collapseBottomSheetDelayed();
                });
            }
        });
    }

    private void initArtistLabelButton() {
        playerBottomSheetViewModel.getLiveArtist().observe(getViewLifecycleOwner(), artist -> {
            if (artist != null) {
                playerArtistNameLabel.setOnClickListener(view -> {
                    Bundle bundle = new Bundle();
                    bundle.putParcelable(Constants.ARTIST_OBJECT, artist);
                    NavHostFragment.findNavController(this).navigate(R.id.artistPageFragment, bundle);
                    activity.collapseBottomSheetDelayed();
                });
            }
        });
    }

    private void initPlaybackSpeedButton(MediaBrowser mediaBrowser) {
        playbackSpeedButton.setOnClickListener(view -> {
            float currentSpeed = Preferences.getPlaybackSpeed();

            if (currentSpeed == Constants.MEDIA_PLAYBACK_SPEED_080) {
                mediaBrowser.setPlaybackParameters(new PlaybackParameters(Constants.MEDIA_PLAYBACK_SPEED_100));
                playbackSpeedButton.setText(getString(R.string.player_playback_speed, Constants.MEDIA_PLAYBACK_SPEED_100));
                Preferences.setPlaybackSpeed(Constants.MEDIA_PLAYBACK_SPEED_100);
            } else if (currentSpeed == Constants.MEDIA_PLAYBACK_SPEED_100) {
                mediaBrowser.setPlaybackParameters(new PlaybackParameters(Constants.MEDIA_PLAYBACK_SPEED_125));
                playbackSpeedButton.setText(getString(R.string.player_playback_speed, Constants.MEDIA_PLAYBACK_SPEED_125));
                Preferences.setPlaybackSpeed(Constants.MEDIA_PLAYBACK_SPEED_125);
            } else if (currentSpeed == Constants.MEDIA_PLAYBACK_SPEED_125) {
                mediaBrowser.setPlaybackParameters(new PlaybackParameters(Constants.MEDIA_PLAYBACK_SPEED_150));
                playbackSpeedButton.setText(getString(R.string.player_playback_speed, Constants.MEDIA_PLAYBACK_SPEED_150));
                Preferences.setPlaybackSpeed(Constants.MEDIA_PLAYBACK_SPEED_150);
            } else if (currentSpeed == Constants.MEDIA_PLAYBACK_SPEED_150) {
                mediaBrowser.setPlaybackParameters(new PlaybackParameters(Constants.MEDIA_PLAYBACK_SPEED_175));
                playbackSpeedButton.setText(getString(R.string.player_playback_speed, Constants.MEDIA_PLAYBACK_SPEED_175));
                Preferences.setPlaybackSpeed(Constants.MEDIA_PLAYBACK_SPEED_175);
            } else if (currentSpeed == Constants.MEDIA_PLAYBACK_SPEED_175) {
                mediaBrowser.setPlaybackParameters(new PlaybackParameters(Constants.MEDIA_PLAYBACK_SPEED_200));
                playbackSpeedButton.setText(getString(R.string.player_playback_speed, Constants.MEDIA_PLAYBACK_SPEED_200));
                Preferences.setPlaybackSpeed(Constants.MEDIA_PLAYBACK_SPEED_200);
            } else if (currentSpeed == Constants.MEDIA_PLAYBACK_SPEED_200) {
                mediaBrowser.setPlaybackParameters(new PlaybackParameters(Constants.MEDIA_PLAYBACK_SPEED_080));
                playbackSpeedButton.setText(getString(R.string.player_playback_speed, Constants.MEDIA_PLAYBACK_SPEED_080));
                Preferences.setPlaybackSpeed(Constants.MEDIA_PLAYBACK_SPEED_080);
            }
        });

        skipSilenceToggleButton.setOnClickListener(view -> {
            Preferences.setSkipSilenceMode(!skipSilenceToggleButton.isChecked());
        });
    }

    public void goToControllerPage() {
        playerMediaCoverViewPager.setCurrentItem(0, false);
    }

    public void goToLyricsPage() {
        playerMediaCoverViewPager.setCurrentItem(1, true);
    }

    private void setPlaybackParameters(MediaBrowser mediaBrowser) {
        Button playbackSpeedButton = bind.getRoot().findViewById(R.id.player_playback_speed_button);
        float currentSpeed = Preferences.getPlaybackSpeed();
        boolean skipSilence = Preferences.isSkipSilenceMode();

        mediaBrowser.setPlaybackParameters(new PlaybackParameters(currentSpeed));
        playbackSpeedButton.setText(getString(R.string.player_playback_speed, currentSpeed));

        // TODO Skippare il silenzio
        skipSilenceToggleButton.setChecked(skipSilence);
    }

    private void resetPlaybackParameters(MediaBrowser mediaBrowser) {
        mediaBrowser.setPlaybackParameters(new PlaybackParameters(Constants.MEDIA_PLAYBACK_SPEED_100));
        // TODO Resettare lo skip del silenzio
    }
}
