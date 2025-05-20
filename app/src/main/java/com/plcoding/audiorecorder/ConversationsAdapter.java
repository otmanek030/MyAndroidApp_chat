package com.plcoding.audiorecorder;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ConversationsAdapter extends RecyclerView.Adapter<ConversationsAdapter.ConversationViewHolder> {
    private List<ConversationItem> conversations = new ArrayList<>();
    private final ConversationClickListener listener;

    public interface ConversationClickListener {
        void onConversationClick(ConversationItem conversation);
    }

    public ConversationsAdapter(ConversationClickListener listener) {
        this.listener = listener;
    }

    public void setConversations(List<ConversationItem> conversations) {
        this.conversations = conversations;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ConversationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_conversation, parent, false);
        return new ConversationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ConversationViewHolder holder, int position) {
        ConversationItem conversation = conversations.get(position);

        holder.titleTextView.setText(conversation.getTitle());
        holder.typeTextView.setText(conversation.getType());
        holder.timeTextView.setText(formatDateTime(conversation.getTimestamp()));

        // Set icon based on type
        if (conversation.getType().contains("Voice")) {
            holder.typeIconView.setImageResource(R.drawable.ic_mic);
        } else {
            holder.typeIconView.setImageResource(R.drawable.ic_text);
        }

        // Show unread count if there are unread messages
        if (conversation.getUnreadCount() > 0) {
            holder.unreadCountView.setVisibility(View.VISIBLE);
            holder.unreadCountView.setText(String.valueOf(conversation.getUnreadCount()));
        } else {
            holder.unreadCountView.setVisibility(View.GONE);
        }

        // Set click listener
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onConversationClick(conversation);
            }
        });
    }

    @Override
    public int getItemCount() {
        return conversations.size();
    }

    private String formatDateTime(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    static class ConversationViewHolder extends RecyclerView.ViewHolder {
        TextView titleTextView;
        TextView typeTextView;
        TextView timeTextView;
        TextView unreadCountView;
        ImageView typeIconView;

        ConversationViewHolder(View itemView) {
            super(itemView);
            titleTextView = itemView.findViewById(R.id.conversation_title);
            typeTextView = itemView.findViewById(R.id.conversation_type);
            timeTextView = itemView.findViewById(R.id.conversation_time);
            unreadCountView = itemView.findViewById(R.id.unread_count);
            typeIconView = itemView.findViewById(R.id.type_icon);
        }
    }
}