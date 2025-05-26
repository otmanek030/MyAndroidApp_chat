package com.plcoding.audiorecorder.ui.theme;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.plcoding.audiorecorder.R;
import com.plcoding.audiorecorder.data.Recording;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RecordingAdapter extends RecyclerView.Adapter<RecordingAdapter.RecordingViewHolder> {
    private List<Recording> recordings = new ArrayList<>();
    private boolean isPlaying = false;
    private Long currentlyPlayingId = null;
    private final OnPlayClickListener onPlayClick;
    private final OnStopClickListener onStopClick;
    private final OnDeleteClickListener onDeleteClick;

    public interface OnPlayClickListener {
        void onPlayClick(Recording recording);
    }

    public interface OnStopClickListener {
        void onStopClick(Recording recording);
    }

    public interface OnDeleteClickListener {
        void onDeleteClick(Recording recording);
    }

    public RecordingAdapter(
            OnPlayClickListener onPlayClick,
            OnStopClickListener onStopClick,
            OnDeleteClickListener onDeleteClick
    ) {
        this.onPlayClick = onPlayClick;
        this.onStopClick = onStopClick;
        this.onDeleteClick = onDeleteClick;
    }

    public void setRecordings(List<Recording> recordings) {
        this.recordings = recordings;
        notifyDataSetChanged();
    }

    public void setIsPlaying(boolean isPlaying) {
        this.isPlaying = isPlaying;
        notifyDataSetChanged();
    }

    public void setCurrentlyPlayingId(Long currentlyPlayingId) {
        this.currentlyPlayingId = currentlyPlayingId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RecordingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recording, parent, false);
        return new RecordingViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecordingViewHolder holder, int position) {
        Recording recording = recordings.get(position);
        boolean isCurrentlyPlaying = isPlaying && recording.getId() == currentlyPlayingId;

        holder.titleTextView.setText(recording.getTitle());
        holder.dateTextView.setText(formatDateTime(recording.getCreatedAt()));

        // Set type icon and background
        if (recording.isTextRecording()) {
            holder.typeIcon.setImageResource(R.drawable.ic_text);
            holder.durationTextView.setText("Text Note");
            holder.playButton.setImageResource(R.drawable.ic_text);
            // Change background color for text items
            View iconBackground = ((ViewGroup) holder.typeIcon.getParent()).getChildAt(0);
            if (iconBackground != null) {
                iconBackground.setBackgroundTintList(
                        ContextCompat.getColorStateList(holder.itemView.getContext(), R.color.text_color)
                );
            }
        } else {
            holder.typeIcon.setImageResource(R.drawable.ic_mic);
            holder.durationTextView.setText(formatDuration(recording.getDuration()));
            holder.playButton.setImageResource(
                    isCurrentlyPlaying ? R.drawable.ic_pause : R.drawable.ic_play
            );
            // Change background color for voice items
            View iconBackground = ((ViewGroup) holder.typeIcon.getParent()).getChildAt(0);
            if (iconBackground != null) {
                iconBackground.setBackgroundTintList(
                        ContextCompat.getColorStateList(holder.itemView.getContext(), R.color.voice_color)
                );
            }
        }

        holder.playButton.setOnClickListener(v -> {
            if (recording.isTextRecording()) {
                onPlayClick.onPlayClick(recording);
            } else {
                if (isCurrentlyPlaying) {
                    onStopClick.onStopClick(recording);
                } else {
                    onPlayClick.onPlayClick(recording);
                }
            }
        });

        holder.deleteButton.setOnClickListener(v -> onDeleteClick.onDeleteClick(recording));
    }

    @Override
    public int getItemCount() {
        return recordings.size();
    }

    private String formatDateTime(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    private String formatDuration(long durationMs) {
        long totalSeconds = durationMs / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    static class RecordingViewHolder extends RecyclerView.ViewHolder {
        TextView titleTextView;
        TextView dateTextView;
        TextView durationTextView;
        ImageView typeIcon;
        ImageButton playButton;
        ImageButton deleteButton;

        @SuppressLint("WrongViewCast")
        RecordingViewHolder(View itemView) {
            super(itemView);
            titleTextView = itemView.findViewById(R.id.title_text_view);
            dateTextView = itemView.findViewById(R.id.date_text_view);
            durationTextView = itemView.findViewById(R.id.duration_text_view);
            typeIcon = itemView.findViewById(R.id.type_icon);
            playButton = itemView.findViewById(R.id.play_button);
            deleteButton = itemView.findViewById(R.id.delete_button);
        }
    }
}