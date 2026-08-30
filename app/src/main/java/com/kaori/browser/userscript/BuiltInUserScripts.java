package com.kaori.browser.userscript;

import android.content.Context;
import android.util.Log;

import com.kaori.browser.database.UserScript;
import com.kaori.browser.database.UserScriptsHelper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/** Registry and installer for Browser's bundled recommended userscripts. */
public final class BuiltInUserScripts {
    private static final String TAG = "BuiltInUserScripts";
    public static final String NAMESPACE = "com.kaori.browser.builtin";

    private static final List<Definition> DEFINITIONS = Arrays.asList(
            new Definition("CopyFreedom", "userscripts/copy_freedom.user.js"),
            new Definition("CleanURL", "userscripts/clean_url.user.js"),
            new Definition("AutoExpand", "userscripts/auto_expand.user.js"),
            new Definition("ReaderTweaks", "userscripts/reader_tweaks.user.js"),
            new Definition("SearchEnhancer", "userscripts/search_enhancer.user.js"),
            new Definition("SiteCleaner", "userscripts/site_cleaner.user.js")
    );

    private BuiltInUserScripts() {
    }

    public static void ensureInstalled(Context context, UserScriptsHelper helper) {
        List<UserScript> existing = helper.getAllScripts();
        int nextRank = helper.getMaxRank() + 1;

        for (Definition definition : DEFINITIONS) {
            String source;
            try {
                source = readAsset(context, definition.assetPath);
            } catch (IOException e) {
                Log.e(TAG, "Unable to load bundled userscript " + definition.name, e);
                continue;
            }

            UserScript installed = findInstalled(existing, definition.name);
            if (installed == null) {
                UserScript script = new UserScript(
                        -1,
                        source,
                        UserScript.getTypefromScript(source),
                        nextRank++,
                        false
                );
                int id = helper.addScript(script);
                script.setId(id);
                existing.add(script);
            } else if (!source.equals(installed.getScript())) {
                installed.setScript(source);
                installed.setType(UserScript.getTypefromScript(source));
                helper.updateScript(installed);
            }
        }
    }

    public static boolean isBuiltIn(UserScript script) {
        if (script == null || !NAMESPACE.equals(script.getNameSpace())) {
            return false;
        }
        String name = script.getName();
        for (Definition definition : DEFINITIONS) {
            if (definition.name.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static UserScript findInstalled(List<UserScript> scripts, String name) {
        for (UserScript script : scripts) {
            if (NAMESPACE.equals(script.getNameSpace()) && name.equals(script.getName())) {
                return script;
            }
        }
        return null;
    }

    private static String readAsset(Context context, String path) throws IOException {
        try (InputStream input = context.getAssets().open(path);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static final class Definition {
        final String name;
        final String assetPath;

        Definition(String name, String assetPath) {
            this.name = name;
            this.assetPath = assetPath;
        }
    }
}
