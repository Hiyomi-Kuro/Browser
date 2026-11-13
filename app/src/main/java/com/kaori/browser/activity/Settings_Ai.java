package com.kaori.browser.activity;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;

import com.kaori.browser.R;
import com.kaori.browser.Utils;
import com.kaori.browser.fragment.Fragment_AiSettings;
import com.kaori.browser.unit.HelperUnit;

import java.util.Objects;

/** Hosts the AI configuration screen. */
public class Settings_Ai extends AppCompatActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        HelperUnit.initTheme(this);
        setContentView(R.layout.activity_settings);
        Utils.setStatusBarAppearance(this);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.ai_settings_title);
        }

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.content_frame, new Fragment_AiSettings())
                .commit();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (menuItem.getItemId() == android.R.id.home) {
            finish();
        }
        return true;
    }
}
