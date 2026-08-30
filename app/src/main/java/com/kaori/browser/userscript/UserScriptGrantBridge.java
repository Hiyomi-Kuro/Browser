package com.kaori.browser.userscript;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import android.webkit.JavascriptInterface;

import com.kaori.browser.database.UserScript;
import com.kaori.browser.view.NinjaWebView;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Native bridge for explicitly granted userscript capabilities. */
public final class UserScriptGrantBridge {
    private static final String TAG = "UserScript";
    private static final String PREFS_NAME = "userscript_values";
    private static final String KEY_SEPARATOR = "\u001f";

    private final Context context;
    private final NinjaWebView webView;
    private final SharedPreferences values;
    private final Map<Integer, String> tokenByScriptId = new ConcurrentHashMap<>();
    private final Map<String, String> storageByToken = new ConcurrentHashMap<>();

    public UserScriptGrantBridge(Context context, NinjaWebView webView) {
        this.context = context;
        this.webView = webView;
        this.values = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String registerScript(UserScript script) {
        int scriptId = script.getId();
        String token = tokenByScriptId.get(scriptId);
        if (token != null) {
            return token;
        }

        String newToken = UUID.randomUUID().toString();
        String existing = tokenByScriptId.putIfAbsent(scriptId, newToken);
        token = existing != null ? existing : newToken;
        storageByToken.putIfAbsent(token, "script_" + scriptId);
        return token;
    }

    @JavascriptInterface
    public String getValue(String token, String key) {
        String storage = storageForToken(token);
        if (storage == null || key == null) {
            return null;
        }
        return values.getString(prefKey(storage, key), null);
    }

    @JavascriptInterface
    public void setValue(String token, String key, String jsonValue) {
        String storage = storageForToken(token);
        if (storage == null || key == null || jsonValue == null) {
            return;
        }
        values.edit().putString(prefKey(storage, key), jsonValue).apply();
    }

    @JavascriptInterface
    public void deleteValue(String token, String key) {
        String storage = storageForToken(token);
        if (storage == null || key == null) {
            return;
        }
        values.edit().remove(prefKey(storage, key)).apply();
    }

    @JavascriptInterface
    public String listValues(String token) {
        String storage = storageForToken(token);
        if (storage == null) {
            return "[]";
        }

        String prefix = storage + KEY_SEPARATOR;
        ArrayList<String> keys = new ArrayList<>();
        for (String key : values.getAll().keySet()) {
            if (key.startsWith(prefix)) {
                keys.add(key.substring(prefix.length()));
            }
        }
        return new JSONArray(keys).toString();
    }

    @JavascriptInterface
    public void openInTab(String token, String url, boolean active) {
        if (storageForToken(token) == null || url == null) {
            return;
        }
        Uri uri = Uri.parse(url);
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            return;
        }

        webView.post(() -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.setPackage(context.getPackageName());
            if (!(context instanceof Activity)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(intent);
        });
    }

    @JavascriptInterface
    public void setClipboard(String token, String text) {
        if (storageForToken(token) == null || text == null) {
            return;
        }
        webView.post(() -> {
            ClipboardManager clipboard =
                    (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("userscript", text));
        });
    }

    @JavascriptInterface
    public void log(String token, String scriptName, String message) {
        if (storageForToken(token) == null) {
            return;
        }
        Log.i(TAG, scriptName + ": " + message);
    }

    private String storageForToken(String token) {
        return token == null ? null : storageByToken.get(token);
    }

    private static String prefKey(String storage, String key) {
        return storage + KEY_SEPARATOR + key;
    }
}
