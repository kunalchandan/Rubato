package one.chandan.rubato.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import one.chandan.rubato.R;
import one.chandan.rubato.util.MetadataSyncLogEntry;
import one.chandan.rubato.util.MetadataSyncManager;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MetadataSyncLogAdapter extends RecyclerView.Adapter<MetadataSyncLogAdapter.ViewHolder> {
    private final List<MetadataSyncLogEntry> items = new ArrayList<>();

    public void setItems(List<MetadataSyncLogEntry> entries) {
        items.clear();
        if (entries != null) {
            items.addAll(entries);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_metadata_sync_log, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MetadataSyncLogEntry entry = items.get(position);
        Context context = holder.itemView.getContext();
        holder.message.setText(entry.getMessage());
        String stageLabel = resolveStageLabel(context, entry.getStage());
        String statusLabel = entry.isCompleted()
                ? context.getString(R.string.metadata_sync_log_status_done)
                : context.getString(R.string.metadata_sync_log_status_active);
        String timeLabel = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(new Date(entry.getTimestamp()));
        String meta = stageLabel + " • " + statusLabel + " • " + timeLabel;
        holder.meta.setText(meta);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String resolveStageLabel(Context context, String stage) {
        if (stage == null) {
            return context.getString(R.string.metadata_sync_stage_preparing);
        }
        switch (stage) {
            case MetadataSyncManager.STAGE_PLAYLISTS:
                return context.getString(R.string.metadata_sync_stage_playlists);
            case MetadataSyncManager.STAGE_JELLYFIN:
                return context.getString(R.string.metadata_sync_stage_jellyfin);
            case MetadataSyncManager.STAGE_ARTISTS:
                return context.getString(R.string.metadata_sync_stage_artists);
            case MetadataSyncManager.STAGE_ARTIST_DETAILS:
                return context.getString(R.string.metadata_sync_stage_artist_details);
            case MetadataSyncManager.STAGE_GENRES:
                return context.getString(R.string.metadata_sync_stage_genres);
            case MetadataSyncManager.STAGE_ALBUMS:
                return context.getString(R.string.metadata_sync_stage_albums);
            case MetadataSyncManager.STAGE_ALBUM_DETAILS:
                return context.getString(R.string.metadata_sync_stage_album_details);
            case MetadataSyncManager.STAGE_SONGS:
                return context.getString(R.string.metadata_sync_stage_songs);
            case MetadataSyncManager.STAGE_COVER_ART:
                return context.getString(R.string.metadata_sync_stage_cover_art);
            case MetadataSyncManager.STAGE_LYRICS:
                return context.getString(R.string.metadata_sync_stage_lyrics);
            case MetadataSyncManager.STAGE_PREPARING:
            default:
                return context.getString(R.string.metadata_sync_stage_preparing);
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView message;
        final TextView meta;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            message = itemView.findViewById(R.id.metadata_sync_log_message);
            meta = itemView.findViewById(R.id.metadata_sync_log_meta);
        }
    }
}
