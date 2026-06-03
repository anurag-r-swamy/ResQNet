package com.example.myapplication;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_SENT = 1;
    private static final int TYPE_RECEIVED = 2;
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm, dd MMM", Locale.getDefault());

    private List<Message> messages;

    public MessageAdapter(List<Message> messages) {
        this.messages = messages;
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).isMe() ? TYPE_SENT : TYPE_RECEIVED;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_SENT) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message_sent, parent, false);
            return new SentViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message_received, parent, false);
            return new ReceivedViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Message message = messages.get(position);
        String timeStr = dateFormat.format(new Date(message.getTimestamp()));
        
        if (holder instanceof SentViewHolder) {
            SentViewHolder vh = (SentViewHolder) holder;
            vh.textMessage.setText(message.getText());
            vh.textTimestamp.setText(timeStr);
        } else {
            ReceivedViewHolder vh = (ReceivedViewHolder) holder;
            vh.textSender.setText(message.getSenderId());
            vh.textMessage.setText(message.getText());
            vh.textTimestamp.setText(timeStr);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    public void addMessage(Message message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    static class SentViewHolder extends RecyclerView.ViewHolder {
        TextView textMessage, textTimestamp;
        SentViewHolder(View itemView) {
            super(itemView);
            textMessage = itemView.findViewById(R.id.text_message);
            textTimestamp = itemView.findViewById(R.id.text_timestamp);
        }
    }

    static class ReceivedViewHolder extends RecyclerView.ViewHolder {
        TextView textSender, textMessage, textTimestamp;
        ReceivedViewHolder(View itemView) {
            super(itemView);
            textSender = itemView.findViewById(R.id.text_sender);
            textMessage = itemView.findViewById(R.id.text_message);
            textTimestamp = itemView.findViewById(R.id.text_timestamp);
        }
    }
}
