package com.kaori.browser.ai.storage;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Persists agent and research task records as JSON files in app-private storage.
 *
 * <p>Uses the project's existing "no database" approach instead of adding Room. Only
 * non-secret state is stored: never an API key, cookie or password.</p>
 */
public final class AgentTaskStore {

    private static final String DIRECTORY = "ai_tasks";
    private static final int MAX_TASKS = 60;

    public static final String KIND_AGENT = "agent";
    public static final String KIND_RESEARCH = "research";

    public static final class TaskRecord {
        public String id = "";
        public String kind = KIND_AGENT;
        public String task = "";
        public String status = "IDLE";
        public long createdAt;
        public long finishedAt;
        public String summary = "";
        public String error = "";
        public String report = "";
        public int steps;
        public int maxSteps;
        public JSONArray log = new JSONArray();
        public JSONArray sources = new JSONArray();
        public JSONObject options;

        public String title() {
            String value = task == null ? "" : task.trim();
            if (value.isEmpty()) {
                return "(untitled task)";
            }
            return value.length() <= 70 ? value : value.substring(0, 70) + "…";
        }

        public JSONObject toJson() {
            JSONObject object = new JSONObject();
            try {
                object.put("schemaVersion", 1);
                object.put("id", id);
                object.put("kind", kind);
                object.put("task", task);
                object.put("status", status);
                object.put("createdAt", createdAt);
                object.put("finishedAt", finishedAt);
                object.put("summary", summary);
                object.put("error", error);
                object.put("report", report);
                object.put("steps", steps);
                object.put("maxSteps", maxSteps);
                object.put("log", log == null ? new JSONArray() : log);
                object.put("sources", sources == null ? new JSONArray() : sources);
                if (options != null) {
                    object.put("options", options);
                }
            } catch (Exception ignored) {
            }
            return object;
        }

        public static TaskRecord fromJson(JSONObject object) {
            TaskRecord record = new TaskRecord();
            if (object == null) {
                return record;
            }
            record.id = object.optString("id", "");
            record.kind = object.optString("kind", KIND_AGENT);
            record.task = object.optString("task", "");
            record.status = object.optString("status", "IDLE");
            record.createdAt = object.optLong("createdAt", 0);
            record.finishedAt = object.optLong("finishedAt", 0);
            record.summary = object.optString("summary", "");
            record.error = object.optString("error", "");
            record.report = object.optString("report", "");
            record.steps = object.optInt("steps", 0);
            record.maxSteps = object.optInt("maxSteps", 0);
            JSONArray log = object.optJSONArray("log");
            record.log = log == null ? new JSONArray() : log;
            JSONArray sources = object.optJSONArray("sources");
            record.sources = sources == null ? new JSONArray() : sources;
            record.options = object.optJSONObject("options");
            return record;
        }
    }

    private AgentTaskStore() {
    }

    private static File directory(Context context) {
        File dir = new File(context.getApplicationContext().getFilesDir(), DIRECTORY);
        if (!dir.exists() && !dir.mkdirs()) {
            return dir;
        }
        return dir;
    }

    public static void save(Context context, TaskRecord record) {
        if (record == null || record.id == null || record.id.isEmpty()) {
            return;
        }
        File target = new File(directory(context), record.id + ".json");
        try (FileOutputStream output = new FileOutputStream(target)) {
            output.write(record.toJson().toString(2).getBytes(StandardCharsets.UTF_8));
            output.flush();
        } catch (Exception ignored) {
            // A failed write must not crash the agent.
        }
        prune(context);
    }

    public static TaskRecord load(Context context, String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        File file = new File(directory(context), id + ".json");
        if (!file.exists()) {
            return null;
        }
        String json = read(file);
        if (json == null) {
            return null;
        }
        try {
            return TaskRecord.fromJson(new JSONObject(json));
        } catch (Exception e) {
            return null;
        }
    }

    public static List<TaskRecord> list(Context context) {
        File[] files = directory(context).listFiles();
        List<TaskRecord> records = new ArrayList<>();
        if (files == null) {
            return records;
        }
        for (File file : files) {
            if (!file.getName().endsWith(".json")) {
                continue;
            }
            String json = read(file);
            if (json == null) {
                continue;
            }
            try {
                records.add(TaskRecord.fromJson(new JSONObject(json)));
            } catch (Exception ignored) {
                // Skip a corrupt record instead of failing the whole list.
            }
        }
        Collections.sort(records, new Comparator<TaskRecord>() {
            @Override
            public int compare(TaskRecord left, TaskRecord right) {
                return Long.compare(right.createdAt, left.createdAt);
            }
        });
        return records;
    }

    public static void delete(Context context, String id) {
        if (id == null || id.isEmpty()) {
            return;
        }
        File file = new File(directory(context), id + ".json");
        if (file.exists() && !file.delete()) {
            // Ignore: the record simply stays on disk.
        }
    }

    private static void prune(Context context) {
        List<TaskRecord> records = list(context);
        if (records.size() <= MAX_TASKS) {
            return;
        }
        for (int i = MAX_TASKS; i < records.size(); i++) {
            delete(context, records.get(i).id);
        }
    }

    private static String read(File file) {
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[(int) Math.min(file.length(), 4 * 1024 * 1024)];
            int total = 0;
            int read;
            while (total < buffer.length
                    && (read = input.read(buffer, total, buffer.length - total)) > 0) {
                total += read;
            }
            return new String(buffer, 0, total, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}
