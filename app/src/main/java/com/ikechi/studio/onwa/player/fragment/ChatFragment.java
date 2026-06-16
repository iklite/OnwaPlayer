package com.ikechi.studio.onwa.player.fragment;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.ikechi.studio.onwa.player.R;
import com.ikechi.studio.onwa.player.adapter.ChatAdapter;
import com.ikechi.studio.onwa.player.models.ChatMessage;
import com.ikechi.studio.onwa.player.models.User;
import com.ikechi.studio.onwa.player.utils.IkBeautifulDialog;
import com.ikechi.studio.onwa.widgets.recyclerview.v1.widget.RecyclingView;

import java.util.List;

/**
 * ChatFragment — live session chat with @mention support and members strip.
 *
 * Features added over the original:
 * ──────────────────────────────────
 * • A horizontally-scrollable members strip at the top shows who is online.
 *   Tapping a member chip auto-inserts "@username " into the input field.
 *
 * • When the input starts with "@username ", the message is routed as a
 *   private DM (displayed with a purple private badge for sender + recipient).
 *
 * • The hint text updates to remind the user about @mention syntax.
 */
public class ChatFragment extends Fragment {

    // ── Views ─────────────────────────────────────────────────────────────────
    private RecyclingView mRecyclerView;
    private EditText      mEditText;
    private Button        mBtnSend, mBtnClearChatHistory;
    private LinearLayout  mMembersStrip;      // horizontal chip container
    private TextView      mTvHint;            // @mention hint label

    // ── Adapters ──────────────────────────────────────────────────────────────
    private ChatAdapter mAdapter;

    // ── Callback ──────────────────────────────────────────────────────────────
    public interface ChatListener {
        /**
         * Called when the user taps Send.
         *
         * @param text  raw text including any leading "@username " prefix.
         *              The caller is responsible for parsing the @mention.
         */
        void onSendMessage(String text);
    }
    private ChatListener mListener;

    public void setChatListener(ChatListener listener) { mListener = listener; }

    // =========================================================================
    // Fragment lifecycle
    // =========================================================================

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chat, container, false);

        mRecyclerView = (RecyclingView) view.findViewById(R.id.chat_list);
        mEditText     = (EditText)      view.findViewById(R.id.chat_input);
        mBtnSend      = (Button)        view.findViewById(R.id.chat_send);
        mBtnClearChatHistory = (Button) view.findViewById(R.id.chat_clear_history);
        mMembersStrip = (LinearLayout)  view.findViewById(R.id.members_strip);
        mTvHint       = (TextView)      view.findViewById(R.id.tv_chat_hint);

        mRecyclerView.setOrientation(RecyclingView.VERTICAL);
        mRecyclerView.setReverseLayout(false);
        mRecyclerView.setSnapToPosition(false);

        mAdapter = new ChatAdapter();
        mRecyclerView.setAdapter(mAdapter);

        // Hint label visibility — show/hide based on typing
        mEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                boolean show = (s == null || s.length() == 0);
                if (mTvHint != null) {
                    mTvHint.setVisibility(show ? View.VISIBLE : View.GONE);
                }
            }
        });

        mBtnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = mEditText.getText().toString().trim();
                if (!text.isEmpty() && mListener != null) {
                    mListener.onSendMessage(text);
                    mEditText.setText("");
                }
            }
        });

        mBtnClearChatHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new IkBeautifulDialog(getActivity())
                    .setMessage("Clear all chat history for this user?")
                    .setPositiveButton("Yes", new IkBeautifulDialog.OnPositiveClickListener() {
                        @Override
                        public void onClick() {
                            // Assuming parent fragment is SharePlaybackFragment
                            Fragment parent = getParentFragment();
                            if (parent instanceof com.ikechi.studio.onwa.player.fragment.SharePlaybackFragment) {
                                ((com.ikechi.studio.onwa.player.fragment.SharePlaybackFragment) parent)
                                    .clearChatHistory();
                            }
                        }
                    })
                    .setNegativeButton("No", null)
                    .show();
            }
        });

        return view;
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /** Append a single message and scroll to bottom. */
    public void addMessage(ChatMessage msg) {
        if (mAdapter == null) return;
        mAdapter.addMessage(msg);
        mRecyclerView.smoothScrollToPosition(mAdapter.getItemCount() - 1);
    }

    /** Replace the full message list (e.g. after loading history from DB). */
    public void setMessages(List<ChatMessage> messages) {
        if (mAdapter == null) return;
        mAdapter.setMessages(messages);
        if (messages != null && !messages.isEmpty()) {
            mRecyclerView.smoothScrollToPosition(messages.size() - 1);
        }
    }

    /**
     * Refreshes the horizontally-scrollable members chip strip.
     * Each chip shows the member's real name; tapping auto-inserts @username.
     */
    public void setMembers(List<User> users) {
        if (mMembersStrip == null) return;
        mMembersStrip.removeAllViews();
        if (users == null || users.isEmpty()) {
            mMembersStrip.setVisibility(View.GONE);
            return;
        }
        mMembersStrip.setVisibility(View.VISIBLE);
        for (User user : users) {
            TextView chip = makeChip(user);
            mMembersStrip.addView(chip);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Creates a styled member "chip" TextView.
     * Tapping it inserts "@username " into the message input.
     */
    private TextView makeChip(final User user) {
        TextView chip = new TextView(getActivity());
        chip.setText(user.getRealName());
        chip.setTextColor(0xFF00BCD4);  // teal
        chip.setTextSize(12f);
        chip.setBackgroundColor(0x1A00BCD4);

        // Padding
        int pad = dpToPx(8);
        int padV = dpToPx(4);
        chip.setPadding(pad, padV, pad, padV);

        // Margins between chips
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dpToPx(6), 0);
        chip.setLayoutParams(lp);

        chip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mEditText == null) return;
                String current  = mEditText.getText().toString();
                String mention  = "@" + user.getUsername() + " ";
                // Replace existing @mention prefix if already present.
                if (current.startsWith("@")) {
                    int spaceIdx = current.indexOf(' ');
                    current = (spaceIdx >= 0) ? current.substring(spaceIdx + 1) : "";
                }
                mEditText.setText(mention + current);
                mEditText.setSelection(mEditText.getText().length());
            }
        });

        return chip;
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}