package com.kaori.browser.fragment;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.kaori.browser.R;
import com.kaori.browser.ai.AiExecutors;
import com.kaori.browser.ai.api.AiClient;
import com.kaori.browser.ai.api.AiClientFactory;
import com.kaori.browser.ai.api.AiException;
import com.kaori.browser.ai.api.CancelToken;
import com.kaori.browser.ai.chat.AiUsageStats;
import com.kaori.browser.ai.config.AiSettings;
import com.kaori.browser.ai.model.ChatResponse;
import com.kaori.browser.ai.security.ApiKeyStore;

import java.util.List;

/**
 * AI configuration: provider, encrypted API key, model discovery, limits, permission mode,
 * usage statistics and a Keystore self check.
 */
public class Fragment_AiSettings extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private boolean busy;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preference_ai, rootKey);

        ListPreference provider = findPreference(AiSettings.KEY_PROVIDER);
        if (provider != null) {
            provider.setOnPreferenceChangeListener((preference, value) -> {
                if (AiSettings.PROVIDER_OPENCODE.equals(String.valueOf(value))) {
                    AiSettings.prefs(requireContext()).edit()
                            .putString(AiSettings.KEY_BASE_URL,
                                    AiSettings.OPENCODE_BASE_URL)
                            .apply();
                }
                return true;
            });
        }

        EditTextPreference apiKey = findPreference("ai_api_key_input");
        if (apiKey != null) {
            apiKey.setText("");
            apiKey.setOnPreferenceChangeListener((preference, value) -> {
                String entered = value == null ? "" : String.valueOf(value).trim();
                if (entered.isEmpty()) {
                    return false;
                }
                boolean saved = ApiKeyStore.save(requireContext(), entered);
                // Never persist or display the raw key.
                ((EditTextPreference) preference).setText("");
                updateKeySummary();
                if (saved) {
                    toast(R.string.ai_key_saved);
                } else {
                    showKeyError(ApiKeyStore.getLastError());
                }
                return false;
            });
        }

        Preference removeKey = findPreference("ai_api_key_remove");
        if (removeKey != null) {
            removeKey.setOnPreferenceClickListener(preference -> {
                ApiKeyStore.clear(requireContext());
                toast(R.string.ai_key_cleared);
                updateKeySummary();
                return true;
            });
        }

        Preference keystoreCheck = findPreference("ai_keystore_check");
        if (keystoreCheck != null) {
            keystoreCheck.setOnPreferenceClickListener(preference -> {
                runKeystoreCheck();
                return true;
            });
        }

        Preference fetchModels = findPreference("ai_fetch_models");
        if (fetchModels != null) {
            fetchModels.setOnPreferenceClickListener(preference -> {
                fetchModels();
                return true;
            });
        }

        Preference testConnection = findPreference("ai_test_connection");
        if (testConnection != null) {
            testConnection.setOnPreferenceClickListener(preference -> {
                testConnection();
                return true;
            });
        }

        Preference chatTools = findPreference("ai_chat_tools");
        if (chatTools != null) {
            chatTools.setOnPreferenceClickListener(preference -> {
                startActivity(new android.content.Intent(requireContext(),
                        com.kaori.browser.activity.Settings_AiTools.class));
                return true;
            });
        }

        Preference resetStats = findPreference("ai_reset_stats");
        if (resetStats != null) {
            resetStats.setOnPreferenceClickListener(preference -> {
                AiUsageStats.reset(requireContext());
                updateStats();
                return true;
            });
        }

        updateKeySummary();
        updateStats();
    }

    // ---------------------------------------------------------------- API key

    private void updateKeySummary() {
        Preference apiKey = findPreference("ai_api_key_input");
        if (apiKey == null) {
            return;
        }
        // Decrypting proves the key is actually usable, not merely present.
        boolean usable = ApiKeyStore.hasUsableKey(requireContext());
        apiKey.setSummary(usable ? R.string.ai_api_key_set : R.string.ai_api_key_missing);
    }

    private void showKeyError(String reason) {
        if (!isAdded()) {
            return;
        }
        StringBuilder message = new StringBuilder(getString(R.string.ai_key_failed));
        if (reason != null && !reason.trim().isEmpty()) {
            message.append('\n').append(reason.trim());
        }
        message.append("\n\n").append(getString(R.string.ai_key_saved_hint));
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.ai_key_failed)
                .setMessage(message.toString())
                .setPositiveButton(R.string.app_ok, null)
                .show();
    }

    private void runKeystoreCheck() {
        if (busy) {
            return;
        }
        busy = true;
        toast(R.string.ai_testing);
        AiExecutors.background().execute(() -> {
            final String failure = ApiKeyStore.selfTest();
            AiExecutors.main().execute(() -> {
                busy = false;
                if (!isAdded()) {
                    return;
                }
                if (failure == null) {
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.ai_keystore_check)
                            .setMessage(R.string.ai_keystore_ok)
                            .setPositiveButton(R.string.app_ok, null)
                            .show();
                } else {
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.ai_keystore_failed)
                            .setMessage(failure + "\n\n" + getString(R.string.ai_key_saved_hint))
                            .setPositiveButton(R.string.app_ok, null)
                            .show();
                }
                updateKeySummary();
            });
        });
    }

    // ---------------------------------------------------------------- models / connection

    private void fetchModels() {
        if (busy) {
            return;
        }
        busy = true;
        toast(R.string.ai_fetching_models);
        final CancelToken token = new CancelToken();
        AiExecutors.background().execute(() -> {
            List<String> models = null;
            String error = null;
            try {
                AiClient client = AiClientFactory.createForTest(requireContext());
                models = client.listModels(token);
            } catch (AiException e) {
                error = describe(e);
            } catch (RuntimeException e) {
                error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }
            final List<String> result = models;
            final String failure = error;
            AiExecutors.main().execute(() -> {
                busy = false;
                if (failure != null) {
                    showError(failure);
                    return;
                }
                if (result == null || result.isEmpty()) {
                    toast(R.string.ai_error_empty);
                    return;
                }
                showModelChooser(result);
            });
        });
    }

    private void showModelChooser(List<String> models) {
        if (!isAdded()) {
            return;
        }
        CharSequence[] labels = models.toArray(new CharSequence[0]);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.ai_models_fetched, models.size()))
                .setItems(labels, (dialog, which) -> {
                    String model = models.get(which);
                    AiSettings.prefs(requireContext()).edit()
                            .putString(AiSettings.KEY_MODEL, model)
                            .apply();
                    EditTextPreference preference = findPreference(AiSettings.KEY_MODEL);
                    if (preference != null) {
                        preference.setText(model);
                    }
                    toast(getString(R.string.ai_model_selected, model));
                })
                .show();
    }

    private void testConnection() {
        if (busy) {
            return;
        }
        busy = true;
        toast(R.string.ai_testing);
        final CancelToken token = new CancelToken();
        AiExecutors.background().execute(() -> {
            String success = null;
            String error = null;
            try {
                AiClient client = AiClientFactory.createForTest(requireContext());
                ChatResponse response = client.testConnection(token);
                success = response.getModelName().isEmpty()
                        ? response.getContent()
                        : response.getModelName();
            } catch (AiException e) {
                error = describe(e);
            } catch (RuntimeException e) {
                error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }
            final String ok = success;
            final String failure = error;
            AiExecutors.main().execute(() -> {
                busy = false;
                if (failure != null) {
                    showError(failure);
                    return;
                }
                if (isAdded()) {
                    toast(getString(R.string.ai_test_ok, ok == null ? "OK" : ok));
                }
            });
        });
    }

    private void showError(String message) {
        if (!isAdded()) {
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.ai_error_generic)
                .setMessage(message)
                .setPositiveButton(R.string.app_ok, null)
                .show();
    }

    private static String describe(AiException e) {
        String detail = e.getDetail() == null ? "" : e.getDetail();
        return e.getKind().name() + (e.getHttpStatus() > 0 ? " (" + e.getHttpStatus() + ")" : "")
                + (detail.isEmpty() ? "" : ": " + detail);
    }

    // ---------------------------------------------------------------- stats

    private void updateStats() {
        Preference stats = findPreference("ai_stats_value");
        if (stats == null) {
            return;
        }
        stats.setSummary(getString(R.string.ai_stats_value,
                AiUsageStats.requests(requireContext()),
                AiUsageStats.promptTokens(requireContext()),
                AiUsageStats.completionTokens(requireContext())));
    }

    private void toast(int res) {
        if (isAdded()) {
            Toast.makeText(requireContext(), res, Toast.LENGTH_SHORT).show();
        }
    }

    private void toast(String text) {
        if (isAdded()) {
            Toast.makeText(requireContext(), text, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
        if ("ai_api_key_input".equals(key)) {
            updateKeySummary();
        }
        Preference preference = findPreference(key);
        if (preference instanceof ListPreference) {
            preference.setSummary(((ListPreference) preference).getEntry());
        }
        if (key != null && key.startsWith("ai_stats")) {
            updateStats();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
        updateKeySummary();
        updateStats();
    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }
}
