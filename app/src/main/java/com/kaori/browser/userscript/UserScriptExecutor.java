package com.kaori.browser.userscript;

import android.os.Looper;
import android.util.Log;
import android.webkit.WebView;

import com.kaori.browser.database.UserScript;
import com.kaori.browser.view.NinjaToast;
import com.kaori.browser.view.NinjaWebView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Loads @require dependencies and injects userscripts with their granted APIs. */
public final class UserScriptExecutor {
    private static final String TAG = "UserScript";
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 15_000;
    private static final int MAX_REQUIRE_BYTES = 2 * 1024 * 1024;
    private static final ExecutorService REQUIRE_EXECUTOR = Executors.newFixedThreadPool(2);
    private static final Map<String, String> REQUIRE_CACHE = new ConcurrentHashMap<>();

    private static final List<String> SUPPORTED_GRANTS = Arrays.asList(
            "GM_addStyle", "GM.addStyle",
            "GM_getValue", "GM.getValue",
            "GM_setValue", "GM.setValue",
            "GM_deleteValue", "GM.deleteValue",
            "GM_listValues", "GM.listValues",
            "GM_log", "GM.log",
            "GM_openInTab", "GM.openInTab",
            "GM_setClipboard", "GM.setClipboard",
            "GM_info", "GM.info",
            "unsafeWindow",
            "none"
    );

    private UserScriptExecutor() {
    }

    public static void prefetchRequirements(List<UserScript> scripts) {
        for (UserScript script : scripts) {
            for (String url : script.getRequireUrls()) {
                REQUIRE_EXECUTOR.execute(() -> {
                    try {
                        loadRequirement(url);
                    } catch (IOException e) {
                        Log.w(TAG, "Unable to prefetch @require " + url, e);
                    }
                });
            }
        }
    }

    public static void execute(WebView view, String targetUrl, UserScript script) {
        String token = null;
        if (hasGrantedApis(script)) {
            if (!(view instanceof NinjaWebView)) {
                Log.w(TAG, "Granted userscript requires NinjaWebView");
                return;
            }
            token = ((NinjaWebView) view).getUserScriptGrantBridge().registerScript(script);
        }

        String grantToken = token;
        if (script.getRequireUrls().isEmpty()) {
            injectIfCurrent(view, targetUrl, buildExecutableScript(script, grantToken, ""));
            return;
        }

        String cachedDependencies = getCachedDependencies(script);
        if (cachedDependencies != null) {
            injectIfCurrent(
                    view,
                    targetUrl,
                    buildExecutableScript(script, grantToken, cachedDependencies)
            );
            return;
        }

        REQUIRE_EXECUTOR.execute(() -> {
            StringBuilder dependencies = new StringBuilder();
            try {
                for (String requireUrl : script.getRequireUrls()) {
                    appendDependency(
                            dependencies,
                            requireUrl,
                            loadRequirement(requireUrl)
                    );
                }
            } catch (IOException e) {
                Log.w(TAG, "@require failed for " + script.getName(), e);
                view.post(() -> NinjaToast.show(
                        view.getContext(),
                        "@require: " + e.getMessage()
                ));
                return;
            }

            injectIfCurrent(
                    view,
                    targetUrl,
                    buildExecutableScript(script, grantToken, dependencies.toString())
            );
        });
    }

    private static void injectIfCurrent(WebView view, String targetUrl, String executable) {
        Runnable injection = () -> {
            if (!Objects.equals(targetUrl, view.getUrl())) {
                return;
            }
            view.evaluateJavascript(executable, null);
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            injection.run();
        } else {
            view.post(injection);
        }
    }

    private static String getCachedDependencies(UserScript script) {
        StringBuilder dependencies = new StringBuilder();
        for (String requireUrl : script.getRequireUrls()) {
            String source = REQUIRE_CACHE.get(requireUrl);
            if (source == null) {
                return null;
            }
            appendDependency(dependencies, requireUrl, source);
        }
        return dependencies.toString();
    }

    private static void appendDependency(StringBuilder dependencies, String url, String source) {
        dependencies.append("\n/* @require ")
                .append(url.replace("*/", "* /"))
                .append(" */\n")
                .append(source)
                .append("\n");
    }

    private static String loadRequirement(String rawUrl) throws IOException {
        String cached = REQUIRE_CACHE.get(rawUrl);
        if (cached != null) {
            return cached;
        }

        URL url = new URL(rawUrl);
        String protocol = url.getProtocol().toLowerCase(Locale.ROOT);
        if (!"http".equals(protocol) && !"https".equals(protocol)) {
            throw new IOException("unsupported URL: " + rawUrl);
        }

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", "*/*");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) UserScript/2.0");

        try {
            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                throw new IOException("HTTP " + responseCode + " for " + rawUrl);
            }
            int declaredLength = connection.getContentLength();
            if (declaredLength > MAX_REQUIRE_BYTES) {
                throw new IOException("dependency too large: " + rawUrl);
            }

            byte[] bytes = readLimited(connection.getInputStream());
            String source = new String(bytes, StandardCharsets.UTF_8);
            String existing = REQUIRE_CACHE.putIfAbsent(rawUrl, source);
            return existing != null ? existing : source;
        } finally {
            connection.disconnect();
        }
    }

    private static byte[] readLimited(InputStream inputStream) throws IOException {
        try (InputStream input = inputStream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_REQUIRE_BYTES) {
                    throw new IOException("dependency exceeds 2 MiB limit");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static String buildExecutableScript(UserScript script, String token, String dependencies) {
        if (!hasGrantedApis(script)) {
            return dependencies + "\n" + script.getScript() + sourceUrl(script);
        }

        StringBuilder js = new StringBuilder();
        js.append("(function(){\n'use strict';\n");
        js.append("const __US_BRIDGE=window.UserScriptBridge;\n");
        js.append("const __US_TOKEN=").append(JSONObject.quote(token)).append(";\n");
        js.append("const __US_NAME=").append(JSONObject.quote(script.getName())).append(";\n");
        js.append("const __US_PARSE=function(raw,fallback){if(raw===null||typeof raw==='undefined')return fallback;try{return JSON.parse(raw);}catch(e){return fallback;}};\n");
        js.append("const __US_ENCODE=function(value){const raw=JSON.stringify(value);if(typeof raw==='undefined')throw new TypeError('Unsupported userscript value');return raw;};\n");

        appendLegacyGrants(js, script);
        appendModernGrants(js, script);
        appendUnsupportedGrantWarnings(js, script);

        js.append("\n").append(dependencies).append("\n");
        js.append(script.getScript()).append("\n");
        js.append("}).call(window);").append(sourceUrl(script));
        return js.toString();
    }

    private static void appendLegacyGrants(StringBuilder js, UserScript script) {
        if (hasGrant(script, "unsafeWindow")) {
            js.append("const unsafeWindow=window;\n");
        }
        if (hasGrant(script, "GM_getValue")) {
            js.append("const GM_getValue=(key,defaultValue)=>__US_PARSE(__US_BRIDGE.getValue(__US_TOKEN,String(key)),defaultValue);\n");
        }
        if (hasGrant(script, "GM_setValue")) {
            js.append("const GM_setValue=(key,value)=>__US_BRIDGE.setValue(__US_TOKEN,String(key),__US_ENCODE(value));\n");
        }
        if (hasGrant(script, "GM_deleteValue")) {
            js.append("const GM_deleteValue=(key)=>__US_BRIDGE.deleteValue(__US_TOKEN,String(key));\n");
        }
        if (hasGrant(script, "GM_listValues")) {
            js.append("const GM_listValues=()=>__US_PARSE(__US_BRIDGE.listValues(__US_TOKEN),[]);\n");
        }
        if (hasGrant(script, "GM_addStyle")) {
            js.append("const GM_addStyle=(css)=>{const style=document.createElement('style');style.textContent=String(css);const target=document.head||document.documentElement;if(target){target.appendChild(style);}else{document.addEventListener('DOMContentLoaded',()=>{const root=document.head||document.documentElement;if(root)root.appendChild(style);},{once:true});}return style;};\n");
        }
        if (hasGrant(script, "GM_log")) {
            js.append("const GM_log=(...args)=>{console.log(...args);__US_BRIDGE.log(__US_TOKEN,__US_NAME,args.map(String).join(' '));};\n");
        }
        if (hasGrant(script, "GM_openInTab")) {
            js.append("const GM_openInTab=(url,options)=>{const active=!(options&&options.active===false);__US_BRIDGE.openInTab(__US_TOKEN,String(url),active);return null;};\n");
        }
        if (hasGrant(script, "GM_setClipboard")) {
            js.append("const GM_setClipboard=(text)=>__US_BRIDGE.setClipboard(__US_TOKEN,String(text));\n");
        }
        if (hasGrant(script, "GM_info")) {
            js.append("const GM_info={script:{name:__US_NAME,namespace:")
                    .append(JSONObject.quote(script.getNameSpace()))
                    .append("},version:'2.0',scriptHandler:'Kaori Browser'};\n");
        }
    }

    private static void appendModernGrants(StringBuilder js, UserScript script) {
        boolean modern = false;
        for (String grant : script.getGrants()) {
            if (grant.startsWith("GM.")) {
                modern = true;
                break;
            }
        }
        if (!modern) {
            return;
        }

        js.append("const GM={};\n");
        if (hasGrant(script, "GM.getValue")) {
            js.append("GM.getValue=async(key,defaultValue)=>__US_PARSE(__US_BRIDGE.getValue(__US_TOKEN,String(key)),defaultValue);\n");
        }
        if (hasGrant(script, "GM.setValue")) {
            js.append("GM.setValue=async(key,value)=>__US_BRIDGE.setValue(__US_TOKEN,String(key),__US_ENCODE(value));\n");
        }
        if (hasGrant(script, "GM.deleteValue")) {
            js.append("GM.deleteValue=async(key)=>__US_BRIDGE.deleteValue(__US_TOKEN,String(key));\n");
        }
        if (hasGrant(script, "GM.listValues")) {
            js.append("GM.listValues=async()=>__US_PARSE(__US_BRIDGE.listValues(__US_TOKEN),[]);\n");
        }
        if (hasGrant(script, "GM.addStyle")) {
            js.append("GM.addStyle=async(css)=>{const style=document.createElement('style');style.textContent=String(css);const target=document.head||document.documentElement;if(target){target.appendChild(style);}else{document.addEventListener('DOMContentLoaded',()=>{const root=document.head||document.documentElement;if(root)root.appendChild(style);},{once:true});}return style;};\n");
        }
        if (hasGrant(script, "GM.log")) {
            js.append("GM.log=(...args)=>{console.log(...args);__US_BRIDGE.log(__US_TOKEN,__US_NAME,args.map(String).join(' '));};\n");
        }
        if (hasGrant(script, "GM.openInTab")) {
            js.append("GM.openInTab=async(url,options)=>{const active=!(options&&options.active===false);__US_BRIDGE.openInTab(__US_TOKEN,String(url),active);return null;};\n");
        }
        if (hasGrant(script, "GM.setClipboard")) {
            js.append("GM.setClipboard=async(text)=>__US_BRIDGE.setClipboard(__US_TOKEN,String(text));\n");
        }
        if (hasGrant(script, "GM.info")) {
            js.append("GM.info={script:{name:__US_NAME,namespace:")
                    .append(JSONObject.quote(script.getNameSpace()))
                    .append("},version:'2.0',scriptHandler:'Kaori Browser'};\n");
        }
    }

    private static void appendUnsupportedGrantWarnings(StringBuilder js, UserScript script) {
        for (String grant : script.getGrants()) {
            if (!SUPPORTED_GRANTS.contains(grant)) {
                js.append("console.warn('[UserScript 2.0] Unsupported @grant: '+")
                        .append(JSONObject.quote(grant))
                        .append(");\n");
            }
        }
    }

    private static boolean hasGrantedApis(UserScript script) {
        for (String grant : script.getGrants()) {
            if (!"none".equalsIgnoreCase(grant)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasGrant(UserScript script, String grant) {
        return script.getGrants().contains(grant);
    }

    private static String sourceUrl(UserScript script) {
        return "\n//# sourceURL=userscript-" + script.getId() + ".user.js";
    }
}
