package com.kaori.browser.activity;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.kaori.browser.R;
import com.kaori.browser.Utils;
import com.kaori.browser.ai.chat.ChatTool;
import com.kaori.browser.ai.storage.ChatToolStore;
import com.kaori.browser.unit.HelperUnit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Lets the user rename, rewrite, add and delete the AI panel's tool buttons. */
public class Settings_AiTools extends AppCompatActivity {

    private static final int MENU_ADD = 1;

    private final List<ChatTool> tools = new ArrayList<>();
    private ToolAdapter adapter;
    private ListView listView;
    private TextView emptyView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        HelperUnit.initTheme(this);
        setContentView(R.layout.activity_ai_tools);
        Utils.setStatusBarAppearance(this);

        Toolbar toolbar = findViewById(R.id.tool_toolbar);
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(R.string.ai_tools_title);

        listView = findViewById(R.id.ai_tools_list);
        emptyView = findViewById(R.id.ai_tools_empty);
        Button restore = findViewById(R.id.ai_tools_restore);

        tools.addAll(ChatToolStore.load(this));
        adapter = new ToolAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> showEditor(position));
        restore.setOnClickListener(v -> confirmRestore());
        updateEmptyState();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem add = menu.add(Menu.NONE, MENU_ADD, Menu.NONE, R.string.ai_tool_add);
        add.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        if (item.getItemId() == MENU_ADD) {
            showEditor(-1);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showEditor(final int position) {
        final ChatTool existing = position >= 0 && position < tools.size()
                ? tools.get(position) : null;

        View content = LayoutInflater.from(this).inflate(R.layout.dialog_ai_tool, null);
        final TextInputEditText nameInput = content.findViewById(R.id.ai_tool_name_input);
        final TextInputEditText promptInput = content.findViewById(R.id.ai_tool_prompt_input);
        if (existing != null) {
            nameInput.setText(existing.getName());
            promptInput.setText(existing.getPrompt());
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(existing == null ? R.string.ai_tool_add : R.string.ai_tool_edit)
                .setView(content)
                .setPositiveButton(R.string.app_ok, null)
                .setNegativeButton(R.string.app_cancel, null);
        if (existing != null) {
            builder.setNeutralButton(R.string.ai_tool_delete,
                    (dialog, which) -> confirmDelete(existing, position));
        }

        final AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String name = textOf(nameInput);
                    if (name.isEmpty()) {
                        Toast.makeText(this, R.string.ai_tool_name_required,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String prompt = textOf(promptInput);
                    if (existing == null) {
                        tools.add(new ChatTool(ChatToolStore.newToolId(), name, prompt,
                                ChatTool.KIND_PROMPT, false));
                    } else {
                        tools.set(position, existing.withNameAndPrompt(name, prompt));
                    }
                    persist(R.string.ai_tool_saved);
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private void confirmDelete(final ChatTool tool, final int position) {
        new MaterialAlertDialogBuilder(this)
                .setMessage(getString(R.string.ai_tool_delete_confirm, tool.getName()))
                .setPositiveButton(R.string.app_ok, (dialog, which) -> {
                    if (position >= 0 && position < tools.size()) {
                        tools.remove(position);
                        persist(R.string.ai_tool_deleted);
                    }
                })
                .setNegativeButton(R.string.app_cancel, null)
                .show();
    }

    private void confirmRestore() {
        new MaterialAlertDialogBuilder(this)
                .setMessage(R.string.ai_tool_restore_confirm)
                .setPositiveButton(R.string.app_ok, (dialog, which) -> {
                    ChatToolStore.resetToDefaults(this);
                    tools.clear();
                    tools.addAll(ChatToolStore.load(this));
                    adapter.notifyDataSetChanged();
                    updateEmptyState();
                    Toast.makeText(this, R.string.ai_tool_restored, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.app_cancel, null)
                .show();
    }

    private void persist(int toastRes) {
        ChatToolStore.save(this, tools);
        adapter.notifyDataSetChanged();
        updateEmptyState();
        Toast.makeText(this, toastRes, Toast.LENGTH_SHORT).show();
    }

    private void updateEmptyState() {
        boolean empty = tools.isEmpty();
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        listView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private static String textOf(EditText input) {
        return input == null || input.getText() == null
                ? "" : input.getText().toString().trim();
    }

    private final class ToolAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return tools.size();
        }

        @Override
        public Object getItem(int position) {
            return tools.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView != null ? convertView
                    : LayoutInflater.from(Settings_AiTools.this)
                    .inflate(R.layout.item_ai_tool, parent, false);
            ChatTool tool = tools.get(position);
            TextView name = view.findViewById(R.id.ai_tool_item_name);
            TextView prompt = view.findViewById(R.id.ai_tool_item_prompt);
            name.setText(tool.getName());
            String text = tool.getPrompt();
            if (TextUtils.isEmpty(text)) {
                prompt.setVisibility(View.GONE);
            } else {
                prompt.setVisibility(View.VISIBLE);
                prompt.setText(text);
            }
            return view;
        }
    }
}
