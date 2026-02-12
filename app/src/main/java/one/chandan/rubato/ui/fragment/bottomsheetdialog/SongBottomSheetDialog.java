package one.chandan.rubato.ui.fragment.bottomsheetdialog;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaBrowser;
import androidx.media3.session.SessionToken;
import androidx.navigation.fragment.NavHostFragment;

import one.chandan.rubato.R;
import one.chandan.rubato.glide.CustomGlideRequest;
import one.chandan.rubato.model.Download;
import one.chandan.rubato.repository.LocalMusicRepository;
import one.chandan.rubato.service.MediaManager;
import one.chandan.rubato.service.MediaService;
import one.chandan.rubato.subsonic.models.Child;
import one.chandan.rubato.ui.activity.MainActivity;
import one.chandan.rubato.ui.dialog.PlaylistChooserDialog;
import one.chandan.rubato.ui.dialog.RatingDialog;
import one.chandan.rubato.util.Constants;
import one.chandan.rubato.util.DownloadUtil;
import one.chandan.rubato.util.MappingUtil;
import one.chandan.rubato.util.MusicUtil;
import one.chandan.rubato.util.OfflinePolicy;
import one.chandan.rubato.util.Preferences;
import one.chandan.rubato.viewmodel.HomeViewModel;
import one.chandan.rubato.viewmodel.SongBottomSheetViewModel;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.common.util.concurrent.ListenableFuture;

@UnstableApi
public class SongBottomSheetDialog extends BottomSheetDialogFragment implements View.OnClickListener {
    private HomeViewModel homeViewModel;
    private SongBottomSheetViewModel songBottomSheetViewModel;
    private Child song;

    private ListenableFuture<MediaBrowser> mediaBrowserListenableFuture;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_song_dialog, container, false);

        song = requireArguments().getParcelable(Constants.TRACK_OBJECT);

        homeViewModel = new ViewModelProvider(requireActivity()).get(HomeViewModel.class);
        songBottomSheetViewModel = new ViewModelProvider(requireActivity()).get(SongBottomSheetViewModel.class);
        songBottomSheetViewModel.setSong(song);

        init(view);

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();

        initializeMediaBrowser();
    }

    @Override
    public void onStop() {
        releaseMediaBrowser();
        super.onStop();
    }

    private void init(View view) {
        boolean isPlayable = OfflinePolicy.canPlay(requireContext(), song);
        boolean isDownloaded = LocalMusicRepository.isLocalSong(song)
                || DownloadUtil.getDownloadTracker(requireContext()).isDownloaded(song.getId());

        ImageView coverSong = view.findViewById(R.id.song_cover_image_view);
        CustomGlideRequest.Builder
                .from(requireContext(), songBottomSheetViewModel.getSong().getCoverArtId(), CustomGlideRequest.ResourceType.Song)
                .build()
                .into(coverSong);

        TextView titleSong = view.findViewById(R.id.song_title_text_view);
        titleSong.setText(songBottomSheetViewModel.getSong().getTitle());

        titleSong.setSelected(true);

        TextView artistSong = view.findViewById(R.id.song_artist_text_view);
        artistSong.setText(songBottomSheetViewModel.getSong().getArtist());

        ToggleButton favoriteToggle = view.findViewById(R.id.button_favorite);
        favoriteToggle.setChecked(songBottomSheetViewModel.getSong().getStarred() != null);
        favoriteToggle.setOnClickListener(v -> {
            songBottomSheetViewModel.setFavorite(requireContext());
        });
        favoriteToggle.setOnLongClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putParcelable(Constants.TRACK_OBJECT, song);

            RatingDialog dialog = new RatingDialog();
            dialog.setArguments(bundle);
            dialog.show(requireActivity().getSupportFragmentManager(), null);

            dismissBottomSheet();
            return true;
        });

        TextView playRadio = view.findViewById(R.id.play_radio_text_view);
        playRadio.setOnClickListener(v -> {
            MediaManager.startQueue(mediaBrowserListenableFuture, song);
            ((MainActivity) requireActivity()).setBottomSheetInPeek(true);

            songBottomSheetViewModel.getInstantMix(getViewLifecycleOwner(), song).observe(getViewLifecycleOwner(), songs -> {
                MusicUtil.ratingFilter(songs);

                if (songs == null) {
                    dismissBottomSheet();
                    return;
                }

                if (!songs.isEmpty()) {
                    MediaManager.enqueue(mediaBrowserListenableFuture, songs, true);
                    dismissBottomSheet();
                }
            });
        });
        setActionEnabled(playRadio, OfflinePolicy.canPlayRadio());

        TextView playNext = view.findViewById(R.id.play_next_text_view);
        playNext.setOnClickListener(v -> {
            MediaManager.enqueue(mediaBrowserListenableFuture, song, true);
            ((MainActivity) requireActivity()).setBottomSheetInPeek(true);
            dismissBottomSheet();
        });
        setActionEnabled(playNext, isPlayable);

        TextView addToQueue = view.findViewById(R.id.add_to_queue_text_view);
        addToQueue.setOnClickListener(v -> {
            MediaManager.enqueue(mediaBrowserListenableFuture, song, false);
            ((MainActivity) requireActivity()).setBottomSheetInPeek(true);
            dismissBottomSheet();
        });
        setActionEnabled(addToQueue, isPlayable);

        TextView rate = view.findViewById(R.id.rate_text_view);
        rate.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putParcelable(Constants.TRACK_OBJECT, song);

            RatingDialog dialog = new RatingDialog();
            dialog.setArguments(bundle);
            dialog.show(requireActivity().getSupportFragmentManager(), null);

            dismissBottomSheet();
        });
        setActionEnabled(rate, OfflinePolicy.canRate());

        TextView download = view.findViewById(R.id.download_text_view);
        download.setOnClickListener(v -> {
            DownloadUtil.getDownloadTracker(requireContext()).download(
                    MappingUtil.mapDownload(song),
                    new Download(song)
            );
            dismissBottomSheet();
        });

        TextView remove = view.findViewById(R.id.remove_text_view);
        remove.setOnClickListener(v -> {
            DownloadUtil.getDownloadTracker(requireContext()).remove(
                    MappingUtil.mapDownload(song),
                    new Download(song)
            );
            dismissBottomSheet();
        });

        initDownloadUI(download, remove, isDownloaded);

        TextView addToPlaylist = view.findViewById(R.id.add_to_playlist_text_view);
        addToPlaylist.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putParcelable(Constants.TRACK_OBJECT, song);

            PlaylistChooserDialog dialog = new PlaylistChooserDialog();
            dialog.setArguments(bundle);
            dialog.show(requireActivity().getSupportFragmentManager(), null);

            dismissBottomSheet();
        });
        setActionEnabled(addToPlaylist, OfflinePolicy.canAddToPlaylist());

        TextView goToAlbum = view.findViewById(R.id.go_to_album_text_view);
        goToAlbum.setOnClickListener(v -> songBottomSheetViewModel.getAlbum().observe(getViewLifecycleOwner(), album -> {
            if (album != null) {
                Bundle bundle = new Bundle();
                bundle.putParcelable(Constants.ALBUM_OBJECT, album);
                NavHostFragment.findNavController(this).navigate(R.id.albumPageFragment, bundle);
            } else
                Toast.makeText(requireContext(), getString(R.string.song_bottom_sheet_error_retrieving_album), Toast.LENGTH_SHORT).show();

            dismissBottomSheet();
        }));

        goToAlbum.setVisibility(songBottomSheetViewModel.getSong().getAlbumId() != null ? View.VISIBLE : View.GONE);

        TextView goToArtist = view.findViewById(R.id.go_to_artist_text_view);
        goToArtist.setOnClickListener(v -> songBottomSheetViewModel.getArtist().observe(getViewLifecycleOwner(), artist -> {
            if (artist != null) {
                Bundle bundle = new Bundle();
                bundle.putParcelable(Constants.ARTIST_OBJECT, artist);
                NavHostFragment.findNavController(this).navigate(R.id.artistPageFragment, bundle);
            } else
                Toast.makeText(requireContext(), getString(R.string.song_bottom_sheet_error_retrieving_artist), Toast.LENGTH_SHORT).show();

            dismissBottomSheet();
        }));

        goToArtist.setVisibility(songBottomSheetViewModel.getSong().getArtistId() != null ? View.VISIBLE : View.GONE);

        TextView share = view.findViewById(R.id.share_text_view);
        share.setOnClickListener(v -> songBottomSheetViewModel.shareTrack().observe(getViewLifecycleOwner(), sharedTrack -> {
            if (sharedTrack != null) {
                ClipboardManager clipboardManager = (ClipboardManager) requireActivity().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clipData = ClipData.newPlainText(getString(R.string.app_name), sharedTrack.getUrl());
                clipboardManager.setPrimaryClip(clipData);
                refreshShares();
                dismissBottomSheet();
            } else {
                Toast.makeText(requireContext(), getString(R.string.share_unsupported_error), Toast.LENGTH_SHORT).show();
                dismissBottomSheet();
            }
        }));

        share.setVisibility(Preferences.isSharingEnabled() ? View.VISIBLE : View.GONE);
        if (share.getVisibility() == View.VISIBLE) {
            setActionEnabled(share, OfflinePolicy.canShare());
        }
    }

    @Override
    public void onClick(View v) {
        dismissBottomSheet();
    }

    private void dismissBottomSheet() {
        dismiss();
    }

    private void initDownloadUI(TextView download, TextView remove, boolean isDownloaded) {
        if (LocalMusicRepository.isLocalSong(song)) {
            download.setVisibility(View.GONE);
            remove.setVisibility(View.GONE);
            return;
        }
        if (isDownloaded) {
            remove.setVisibility(View.VISIBLE);
            setActionEnabled(remove, true);
        } else {
            download.setVisibility(View.VISIBLE);
            remove.setVisibility(View.GONE);
            setActionEnabled(download, OfflinePolicy.canDownload(song));
        }
    }

    private void setActionEnabled(View view, boolean enabled) {
        if (view == null) return;
        view.setEnabled(enabled);
        view.setAlpha(enabled ? 1f : 0.4f);
    }

    private void initializeMediaBrowser() {
        mediaBrowserListenableFuture = new MediaBrowser.Builder(requireContext(), new SessionToken(requireContext(), new ComponentName(requireContext(), MediaService.class))).buildAsync();
    }

    private void releaseMediaBrowser() {
        MediaBrowser.releaseFuture(mediaBrowserListenableFuture);
    }

    private void refreshShares() {
        homeViewModel.refreshShares(requireActivity());
    }
}
