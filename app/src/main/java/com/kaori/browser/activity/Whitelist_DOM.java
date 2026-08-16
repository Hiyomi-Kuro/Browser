package com.kaori.browser.activity;

import android.os.Bundle;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;
import java.util.Objects;

import com.kaori.browser.R;
import com.kaori.browser.Utils;
import com.kaori.browser.browser.DOM;
import com.kaori.browser.database.RecordAction;
import com.kaori.browser.unit.BrowserUnit;
import com.kaori.browser.unit.HelperUnit;
import com.kaori.browser.unit.RecordUnit;
import com.kaori.browser.view.WhitelistAdapter;
import com.kaori.browser.view.NinjaToast;

public class Whitelist_DOM extends AppCompatActivity {

    private WhitelistAdapter adapter;
    private List<String> list;
    private DOM DOM;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        HelperUnit.initTheme(this);
        setContentView(R.layout.activity_settings_whitelist);
        Utils.setStatusBarAppearance(this);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

        DOM = new DOM(Whitelist_DOM.this);

        RecordAction action = new RecordAction(this);
        action.open(false);
        list = action.listDomains(RecordUnit.TABLE_DOM);
        action.close();

        ListView listView = findViewById(R.id.whitelist);
        listView.setEmptyView(findViewById(R.id.whitelist_empty));

        //noinspection NullableProblems
        adapter = new WhitelistAdapter(this, list){
            @Override
            public View getView (final int position, View convertView, @NonNull ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                ImageButton whitelist_item_cancel = v.findViewById(R.id.whitelist_item_cancel);
                whitelist_item_cancel.setOnClickListener(v1 -> {
                    DOM.removeDomain(list.get(position));
                    list.remove(position);
                    notifyDataSetChanged();
                    NinjaToast.show(Whitelist_DOM.this, R.string.toast_delete_successful);
                });
                return v;
            }
        };
        listView.setAdapter(adapter);
        adapter.notifyDataSetChanged();

        Button button = findViewById(R.id.whitelist_add);
        button.setOnClickListener(v -> {
            EditText editText = findViewById(R.id.whitelist_edit);
            String domain = editText.getText().toString().trim();
            if (domain.isEmpty()) {
                NinjaToast.show(Whitelist_DOM.this, R.string.toast_input_empty);
            } else if (!BrowserUnit.isURL(domain)) {
                NinjaToast.show(Whitelist_DOM.this, R.string.toast_invalid_domain);
            } else {
                RecordAction action1 = new RecordAction(Whitelist_DOM.this);
                action1.open(true);
                if (action1.checkDomain(domain, RecordUnit.TABLE_DOM)) {
                    NinjaToast.show(Whitelist_DOM.this, R.string.toast_domain_already_exists);
                } else {
                    DOM.addDomain(domain.trim());
                    list.add(0, domain.trim());
                    adapter.notifyDataSetChanged();
                    NinjaToast.show(Whitelist_DOM.this, R.string.toast_add_whitelist_successful);
                }
                action1.close();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_whitelist, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {

        if (menuItem.getItemId() == android.R.id.home) {
            finish();
        } else if (menuItem.getItemId() == R.id.menu_clear) {
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
            builder.setMessage(R.string.hint_database);
            builder.setPositiveButton(R.string.app_ok, (dialog, whichButton) -> {
                DOM DOM = new DOM(Whitelist_DOM.this);
                DOM.clearDomains();
                list.clear();
                adapter.notifyDataSetChanged();
            });
            builder.setNegativeButton(R.string.app_cancel, (dialog, whichButton) -> dialog.cancel());
            AlertDialog dialog = builder.create();
            dialog.show();
            Objects.requireNonNull(dialog.getWindow()).setGravity(Gravity.BOTTOM);
        }
        return true;
    }
}