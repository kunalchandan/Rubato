package one.chandan.rubato.ui.fragment.bottomsheetdialog;

import android.content.ComponentName;
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

import one.chandan.rubato.R;
import one.chandan.rubato.glide.CustomGlideRequest;
import one.chandan.rubato.repository.ArtistRepository;
import one.chandan.rubato.service.MediaManager;
import one.chandan.rubato.service.MediaService;
import one.chandan.rubato.subsonic.models.ArtistID3;
import one.chandan.rubato.subsonic.models.Child;
import one.chandan.rubato.ui.activity.MainActivity;
import one.chandan.rubato.util.Constants;
import one.chandan.rubato.util.MusicUtil;
import one.chandan.rubato.util.OfflinePolicy;
import one.chandan.rubato.viewmodel.ArtistBottomSheetViewModel;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;

@UnstableApi
public class ArtistBottomSheetDialog extends BottomSheetDialogFragment implements View.OnClickListener {
    private static final String TAG = "AlbumBottomSheetDialog";

    private ArtistBottomSheetViewModel artistBottomSheetViewModel;
    private ArtistID3 artist;

    private ListenableFuture<MediaBrowser> mediaBrowserListenableFuture;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_artist_dialog, container, false);

        artist = this.requireArguments().getParcelable(Constants.ARTIST_OBJECT);

        artistBottomSheetViewModel = new ViewModelProvider(requireActivity()).get(ArtistBottomSheetViewModel.class);
        artistBottomSheetViewModel.setArtist(artist);

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

    // TODO Utilizzare il viewmodel come tramite ed evitare le chiamate dirette
    private void init(View view) {
        ImageView coverArtist = view.findViewById(R.id.artist_cover_image_view);
        CustomGlideRequest.Builder
                .from(requireContext(), artistBottomSheetViewModel.getArtist().getCoverArtId(), CustomGlideRequest.ResourceType.Artist)
                .build()
                .into(coverArtist);

        TextView nameArtist = view.findViewById(R.id.song_title_text_view);
        nameArtist.setText(artistBottomSheetViewModel.getArtist().getName());
        nameArtist.setSelected(true);

        ToggleButton favoriteToggle = view.findViewById(R.id.button_favorite);
        favoriteToggle.setChecked(artistBottomSheetViewModel.getArtist().getStarred() != null);
        favoriteToggle.setOnClickListener(v -> {
            artistBottomSheetViewModel.setFavorite();
        });

        TextView playRadio = view.findViewById(R.id.play_radio_text_view);
        playRadio.setOnClickListener(v -> {
            ArtistRepository artistRepository = new ArtistRepository();

            artistRepository.getInstantMix(artist, 20).observe(getViewLifecycleOwner(), songs -> {
                MusicUtil.ratingFilter(songs);

                if (!songs.isEmpty()) {
                    MediaManager.startQueue(mediaBrowserListenableFuture, songs, 0);
                    ((MainActivity) requireActivity()).setBottomSheetInPeek(true);
                }

                dismissBottomSheet();
            });
        });
        setActionEnabled(playRadio, OfflinePolicy.canPlayRadio());

        TextView playRandom = view.findViewById(R.id.play_random_text_view);
        playRandom.setOnClickListener(v -> {
            ArtistRepository artistRepository = new ArtistRepository();
            artistRepository.getRandomSong(artist, 50).observe(getViewLifecycleOwner(), songs -> {
                MusicUtil.ratingFilter(songs);

                List<Child> playable = OfflinePolicy.filterPlayable(requireContext(), songs);
                if (!playable.isEmpty()) {
                    MediaManager.startQueue(mediaBrowserListenableFuture, playable, 0);
                    ((MainActivity) requireActivity()).setBottomSheetInPeek(true);

                    dismissBottomSheet();
                } else {
                    Toast.makeText(requireContext(), getString(R.string.artist_error_retrieving_tracks), Toast.LENGTH_SHORT).show();
                }

                dismissBottomSheet();
            });
        });
        setActionEnabled(playRandom, OfflinePolicy.canPlayRandom());
    }

    @Override
    public void onClick(View v) {
        dismissBottomSheet();
    }

    private void dismissBottomSheet() {
        dismiss();
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
}
