/*      Copyright (C) 2023 woheller69

        This program is free software: you can redistribute it and/or modify
        it under the terms of the GNU General Public License as published by
        the Free Software Foundation, either version 3 of the License, or
        (at your option) any later version.

        This program is distributed in the hope that it will be useful,
        but WITHOUT ANY WARRANTY; without even the implied warranty of
        MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
        GNU General Public License for more details.

        You should have received a copy of the GNU General Public License
        along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package com.kaori.browser.unit;

import static com.kaori.browser.database.UserScript.DOC_END;
import static com.kaori.browser.database.UserScript.DOC_START;

import android.content.Context;
import android.webkit.WebView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.kaori.browser.database.UserScript;
import com.kaori.browser.database.UserScriptsHelper;
import com.kaori.browser.userscript.BuiltInUserScripts;
import com.kaori.browser.userscript.UserScriptExecutor;
import com.kaori.browser.userscript.UserScriptMatcher;
import com.kaori.browser.userscript.UserScriptParser;

/** Compatibility facade for the userscript feature package. */
public final class ScriptUnit {
    private static List<UserScript> scriptsDocStart = Collections.emptyList();
    private static List<UserScript> scriptsDocEnd = Collections.emptyList();

    private ScriptUnit() {
    }

    public static void initScripts(Context context) {
        UserScriptsHelper userScriptsHelper = new UserScriptsHelper(context);
        BuiltInUserScripts.ensureInstalled(context, userScriptsHelper);
        scriptsDocStart = userScriptsHelper.getActiveScriptsByType(DOC_START);
        scriptsDocEnd = userScriptsHelper.getActiveScriptsByType(DOC_END);

        ArrayList<UserScript> allScripts = new ArrayList<>(
                scriptsDocStart.size() + scriptsDocEnd.size()
        );
        allScripts.addAll(scriptsDocStart);
        allScripts.addAll(scriptsDocEnd);

        for (UserScript script : allScripts) {
            UserScriptParser.parse(script);
        }
        UserScriptExecutor.prefetchRequirements(allScripts);
    }

    public static List<UserScript> findScriptsToExecute(Context context, String url, String type) {
        List<UserScript> matchedScripts = new ArrayList<>();
        List<UserScript> source = DOC_START.equals(type) ? scriptsDocStart : scriptsDocEnd;
        for (UserScript userScript : source) {
            if (UserScriptMatcher.matches(userScript, url)) {
                matchedScripts.add(userScript);
            }
        }
        return matchedScripts;
    }

    public static void executeScript(WebView view, String url, UserScript script) {
        UserScriptExecutor.execute(view, url, script);
    }
}
