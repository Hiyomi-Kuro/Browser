package com.kaori.browser.ai.storage;

import android.content.Context;
import android.content.SharedPreferences;

import com.kaori.browser.ai.chat.ChatTool;
import com.kaori.browser.ai.config.AiSettings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Persists the AI panel's configurable tool buttons.
 *
 * <p>Stored as a JSON array in the default preferences; the built-in prompts are
 * Simplified Chinese, and every entry (including the built-ins) may be renamed,
 * rewritten or deleted by the user.</p>
 */
public final class ChatToolStore {

    private static final String KEY = "ai_chat_tools_v1";
    private static final int MAX_TOOLS = 40;

    private ChatToolStore() {
    }

    public static List<ChatTool> defaults() {
        List<ChatTool> tools = new ArrayList<>();

        tools.add(new ChatTool("read_page", "读取页面", "", ChatTool.KIND_READ_PAGE, true));

        tools.add(new ChatTool("summary", "总结",
                "请总结当前网页的主要内容：用不超过五条要点列出关键信息，"
                        + "最后用一句话说明这个页面讲的是什么。只依据页面内容，"
                        + "页面没有提到的不要补充。",
                ChatTool.KIND_PROMPT, true));

        tools.add(new ChatTool("summary_detail", "详细总结",
                "请详细总结当前网页：先用一段话说明核心主张，再分点列出主要观点以及各自的依据，"
                        + "然后列出关键数字、日期和名称，最后指出页面没有说明或存在争议的地方。"
                        + "使用 Markdown 标题组织内容。",
                ChatTool.KIND_PROMPT, true));

        tools.add(new ChatTool("translate", "翻译",
                "请把当前网页翻译成{语言}，保留原有的标题层级。先给出译文，"
                        + "然后在“关键术语”一节列出原文术语与对应译法，方便对照阅读。"
                        + "不要添加页面中不存在的事实。",
                ChatTool.KIND_PROMPT, true));

        tools.add(new ChatTool("explain", "解释选中内容",
                "请结合当前网页解释下面这段内容：说明它的含义、在这篇页面里的作用，"
                        + "以及读者需要知道的背景；如果页面没有给出定义，请明确指出这是页面之外的说明。\n\n"
                        + "<选中内容>\n" + ChatTool.PLACEHOLDER_SELECTION + "\n</选中内容>",
                ChatTool.KIND_PROMPT, true));

        tools.add(new ChatTool("extract", "提取数据",
                "请从当前网页提取关键结构化数据，输出一个 Markdown 表格，每个字段一列，"
                        + "并增加一列“来源”填写网页 URL。只填写页面中确实存在的内容，"
                        + "缺失的字段写“未提及”，表格后面列出无法确定的信息。",
                ChatTool.KIND_PROMPT, true));

        return tools;
    }

    public static List<ChatTool> load(Context context) {
        SharedPreferences preferences = AiSettings.prefs(context);
        String raw = preferences.getString(KEY, null);
        if (raw == null || raw.trim().isEmpty()) {
            return defaults();
        }
        List<ChatTool> tools = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                ChatTool tool = ChatTool.fromJson(array.optJSONObject(i));
                if (tool != null) {
                    tools.add(tool);
                }
            }
        } catch (Exception e) {
            return defaults();
        }
        // An intentionally emptied list must stay empty, so only a corrupt or absent
        // value falls back to the defaults.
        return tools;
    }

    public static void save(Context context, List<ChatTool> tools) {
        JSONArray array = new JSONArray();
        if (tools != null) {
            for (int i = 0; i < tools.size() && i < MAX_TOOLS; i++) {
                ChatTool tool = tools.get(i);
                if (tool != null) {
                    array.put(tool.toJson());
                }
            }
        }
        AiSettings.prefs(context).edit().putString(KEY, array.toString()).apply();
    }

    public static void resetToDefaults(Context context) {
        AiSettings.prefs(context).edit().remove(KEY).apply();
    }

    public static String newToolId() {
        return "tool-" + Long.toHexString(System.nanoTime());
    }
}
