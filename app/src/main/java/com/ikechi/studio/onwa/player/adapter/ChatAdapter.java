package com.ikechi.studio.onwa.player.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.ikechi.studio.onwa.player.R;
import com.ikechi.studio.onwa.player.models.ChatMessage;
import com.ikechi.studio.onwa.widgets.recyclerview.v1.adapter.RecyclingAdapter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Adapter for the in-session chat list.
 *
 * Visual treatment
 * ────────────────
 * • My messages (isLocal)  — right-aligned, gold sender colour.
 * • Other messages          — left-aligned, teal sender colour.
 * • Private (@mention) msgs — purple sender colour + "[Private]" badge
 *   on BOTH the sender's and recipient's side regardless of direction.
 */
public class ChatAdapter extends RecyclingAdapter {

    private static final int COLOR_SENDER_LOCAL   = 0xFFFFD97D;  // gold
    private static final int COLOR_SENDER_REMOTE  = 0xFF00BCD4;  // teal
    private static final int COLOR_SENDER_PRIVATE = 0xFFCE93D8;  // purple
    private static final int COLOR_BADGE_PRIVATE  = 0x44CE93D8;  // translucent purple

    private final List<ChatMessage> mMessages = new ArrayList<ChatMessage>();
    private final SimpleDateFormat  mSdf = new SimpleDateFormat("HH:mm", Locale.getDefault());

    // ── Data mutations ────────────────────────────────────────────────────────

    public void setMessages(List<ChatMessage> messages) {
        mMessages.clear();
        if (messages != null) mMessages.addAll(messages);
        notifyDataSetChanged();
    }

    public void addMessage(ChatMessage msg) {
        mMessages.add(msg);
        notifyItemInserted(mMessages.size() - 1);
    }

    public int getItemCount() { return mMessages.size(); }

    // ── RecyclingAdapter ──────────────────────────────────────────────────────

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
			.inflate(R.layout.item_chat_message, parent, false);
        return new ChatViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        ((ChatViewHolder) holder).bind(mMessages.get(position));
    }

    // ── ViewHolder ────────────────────────────────────────────────────────────

    private class ChatViewHolder extends RecyclingAdapter.ViewHolder {

        private final TextView tvSender;
        private final TextView tvMessage;
        private final TextView tvTime;
        private final TextView tvPrivateBadge;

        ChatViewHolder(View v) {
            super(v);
            tvSender       = (TextView) v.findViewById(R.id.tv_sender);
            tvMessage      = (TextView) v.findViewById(R.id.tv_message);
            tvTime         = (TextView) v.findViewById(R.id.tv_time);
            tvPrivateBadge = (TextView) v.findViewById(R.id.tv_private_badge);
        }

        void bind(ChatMessage msg) {
            // Sender label: "Real Name (username)"
            String senderLabel = msg.getRealName() + "  (" + msg.getUsername() + ")";
            tvSender.setText(senderLabel);
            tvMessage.setText(msg.getMessage());
            tvTime.setText(mSdf.format(new Date(msg.getTimestamp())));

            // Colour and alignment based on origin + privacy
            if (msg.isPrivate()) {
                tvSender.setTextColor(COLOR_SENDER_PRIVATE);
                getItemView().setBackgroundColor(COLOR_BADGE_PRIVATE);
                if (tvPrivateBadge != null) {
                    tvPrivateBadge.setVisibility(View.VISIBLE);
                    String target = msg.getTargetUsername();
                    tvPrivateBadge.setText(
						msg.isLocal()
						? "\u25b6 Private to @" + target
						: "\u25c4 Private from @" + msg.getUsername());
                }
            } else {
                int senderColor = msg.isLocal() ? COLOR_SENDER_LOCAL : COLOR_SENDER_REMOTE;
                tvSender.setTextColor(senderColor);
                getItemView().setBackgroundColor(Color.TRANSPARENT);
                if (tvPrivateBadge != null) tvPrivateBadge.setVisibility(View.GONE);
            }
        }
    }
}

