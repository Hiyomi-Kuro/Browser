package com.kaori.browser.ai.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.kaori.browser.R;
import com.kaori.browser.ai.chat.ChatMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Renders the chat, agent steps, tool results and reports in one list. */
public final class AiMessageAdapter extends RecyclerView.Adapter<AiMessageAdapter.Holder> {

    private static final int COLOR_ERROR = 0xFFEF5350;
    private static final int COLOR_WARNING = 0xFFFFB74D;

    private final Context context;
    private final List<ChatMessage> items = new ArrayList<>();

    public AiMessageAdapter(Context context) {
        this.context = context;
    }

    public void add(ChatMessage message) {
        if (message == null) {
            return;
        }
        items.add(message);
        notifyItemInserted(items.size() - 1);
    }

    public boolean updateLast(ChatMessage message) {
        if (items.isEmpty()) {
            return false;
        }
        items.set(items.size() - 1, message);
        notifyItemChanged(items.size() - 1);
        return true;
    }

    /** Replaces the item at {@code index}; progress messages may be appended after it. */
    public boolean updateAt(int index, ChatMessage message) {
        if (index < 0 || index >= items.size()) {
            return false;
        }
        items.set(index, message);
        notifyItemChanged(index);
        return true;
    }

    public ChatMessage last() {
        return items.isEmpty() ? null : items.get(items.size() - 1);
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public List<ChatMessage> items() {
        return Collections.unmodifiableList(items);
    }

    public void clear() {
        items.clear();
        notifyDataSetChanged();
    }

    public String lastText() {
        for (int i = items.size() - 1; i >= 0; i--) {
            ChatMessage message = items.get(i);
            if (message.getKind() == ChatMessage.Kind.ASSISTANT
                    || message.getKind() == ChatMessage.Kind.REPORT) {
                return message.getText();
            }
        }
        return "";
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_ai_message, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        ChatMessage message = items.get(position);
        holder.header.setText(headerFor(message));
        holder.header.setTextColor(colorFor(message));
        String body = message.getText();
        holder.body.setText(body == null || body.isEmpty() ? "…" : body);
        holder.body.setTextIsSelectable(
                message.getKind() == ChatMessage.Kind.ASSISTANT
                        || message.getKind() == ChatMessage.Kind.REPORT
                        || message.getKind() == ChatMessage.Kind.TOOL);
    }

    private String headerFor(ChatMessage message) {
        switch (message.getKind()) {
            case USER:
                return context.getString(R.string.ai_label_you);
            case ASSISTANT:
                return tagged(context.getString(R.string.ai_label_ai), message.getTag());
            case PAGE:
                return tagged(context.getString(R.string.ai_label_page), message.getTag());
            case STEP:
                return stepLabel(message.getTag());
            case TOOL:
                return tagged(context.getString(R.string.ai_label_tool), message.getTag());
            case ERROR:
                return context.getString(R.string.ai_label_error);
            case REPORT:
                return context.getString(R.string.ai_label_report);
            case CONFIRM:
                return context.getString(R.string.ai_label_confirm);
            case SYSTEM:
            default:
                return context.getString(R.string.ai_label_ai);
        }
    }

    private String stepLabel(String tag) {
        try {
            return context.getString(R.string.ai_label_step, Integer.parseInt(tag.trim()));
        } catch (Exception e) {
            return context.getString(R.string.ai_label_step, 0);
        }
    }

    private static String tagged(String base, String tag) {
        return tag == null || tag.trim().isEmpty() ? base : base + " · " + tag;
    }

    private int colorFor(ChatMessage message) {
        switch (message.getKind()) {
            case ERROR:
                return COLOR_ERROR;
            case STEP:
            case CONFIRM:
                return COLOR_WARNING;
            default:
                return context.getColor(R.color.primaryColor);
        }
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView header;
        final TextView body;

        Holder(View itemView) {
            super(itemView);
            header = itemView.findViewById(R.id.ai_item_header);
            body = itemView.findViewById(R.id.ai_item_body);
        }
    }
}
