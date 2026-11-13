package com.kaori.browser.ai.research;

import com.kaori.browser.ai.agent.AgentStatus;
import com.kaori.browser.ai.agent.UrlTools;
import com.kaori.browser.ai.model.SessionIds;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A research request plus the report and sources it produced. */
public final class ResearchTask {

    private final String id;
    private final String topic;
    private final ResearchOptions options;
    private final long createdAt;
    private long finishedAt;
    private AgentStatus status = AgentStatus.IDLE;
    private String report = "";
    private String errorMessage = "";
    private final Map<String, SourceRecord> sources = new LinkedHashMap<>();

    public ResearchTask(String topic, ResearchOptions options) {
        this.id = SessionIds.newSessionId();
        this.topic = topic == null ? "" : topic.trim();
        this.options = options == null ? ResearchOptions.defaults() : options;
        this.createdAt = System.currentTimeMillis();
    }

    private ResearchTask(String id, String topic, ResearchOptions options, long createdAt,
                         long finishedAt, AgentStatus status, String report,
                         String errorMessage, Map<String, SourceRecord> sources) {
        this.id = id;
        this.topic = topic;
        this.options = options;
        this.createdAt = createdAt;
        this.finishedAt = finishedAt;
        this.status = status;
        this.report = report;
        this.errorMessage = errorMessage;
        if (sources != null) {
            this.sources.putAll(sources);
        }
    }

    public String getId() {
        return id;
    }

    public String getTopic() {
        return topic;
    }

    public ResearchOptions getOptions() {
        return options;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getFinishedAt() {
        return finishedAt;
    }

    public AgentStatus getStatus() {
        return status;
    }

    public void setStatus(AgentStatus status) {
        this.status = status == null ? AgentStatus.IDLE : status;
        if (this.status.isTerminal()) {
            this.finishedAt = System.currentTimeMillis();
        }
    }

    public String getReport() {
        return report;
    }

    public void setReport(String report) {
        this.report = report == null ? "" : report;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage == null ? "" : errorMessage;
    }

    public List<SourceRecord> getSources() {
        return Collections.unmodifiableList(new ArrayList<>(sources.values()));
    }

    public void addSource(SourceRecord record) {
        if (record == null) {
            return;
        }
        String key = UrlTools.normalizeForDedupe(record.url);
        if (!key.isEmpty() && !sources.containsKey(key)) {
            sources.put(key, record);
        }
    }

    public static ResearchTask fromPersisted(String id, String topic, ResearchOptions options,
                                             long createdAt, long finishedAt,
                                             AgentStatus status, String report,
                                             String errorMessage,
                                             List<SourceRecord> sources) {
        Map<String, SourceRecord> map = new LinkedHashMap<>();
        if (sources != null) {
            for (SourceRecord record : sources) {
                String key = UrlTools.normalizeForDedupe(record.url);
                if (!key.isEmpty()) {
                    map.put(key, record);
                }
            }
        }
        return new ResearchTask(id, topic, options, createdAt, finishedAt, status, report,
                errorMessage, map);
    }
}
