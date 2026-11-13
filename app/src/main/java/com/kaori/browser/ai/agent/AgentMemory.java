package com.kaori.browser.ai.agent;

import com.kaori.browser.ai.agent.observation.BrowserObservation;
import com.kaori.browser.ai.research.SourceRecord;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Bounded working memory for one agent run. */
public final class AgentMemory {

    private static final int MAX_OBSERVATIONS = 3;

    private final List<BrowserObservation> observations = new ArrayList<>();
    private final List<AgentStep> steps = new ArrayList<>();
    private final Map<String, SourceRecord> sources = new LinkedHashMap<>();
    private final Set<String> visited = new LinkedHashSet<>();

    public void addObservation(BrowserObservation observation) {
        if (observation == null) {
            return;
        }
        observations.add(observation);
        while (observations.size() > MAX_OBSERVATIONS) {
            observations.remove(0);
        }
        if (!observation.url.isEmpty()) {
            visited.add(UrlTools.normalizeForDedupe(observation.url));
        }
    }

    public BrowserObservation latestObservation() {
        return observations.isEmpty() ? null : observations.get(observations.size() - 1);
    }

    public void addStep(AgentStep step) {
        if (step != null) {
            steps.add(step);
        }
    }

    public List<AgentStep> getSteps() {
        return Collections.unmodifiableList(steps);
    }

    public int stepCount() {
        return steps.size();
    }

    public boolean isVisited(String url) {
        return url != null && visited.contains(UrlTools.normalizeForDedupe(url));
    }

    public Set<String> visitedUrls() {
        return Collections.unmodifiableSet(visited);
    }

    /** Records a page the agent actually read so the report can never invent one. */
    public void recordSource(String url, String title, String excerpt, String status) {
        String key = UrlTools.normalizeForDedupe(url);
        if (key.isEmpty()) {
            return;
        }
        SourceRecord existing = sources.get(key);
        if (existing == null) {
            sources.put(key, new SourceRecord(url, title, excerpt, status));
            return;
        }
        if (excerpt != null && excerpt.length() > existing.excerpt.length()) {
            existing.excerpt = excerpt;
        }
        if (title != null && !title.isEmpty() && existing.title.isEmpty()) {
            sources.put(key, new SourceRecord(url, title, existing.excerpt, status));
        }
    }

    public Collection<SourceRecord> sources() {
        return Collections.unmodifiableCollection(sources.values());
    }

    public int sourceCount() {
        return sources.size();
    }

    /** Recent step history for the planner prompt, bounded by characters. */
    public String compactSteps(int maxChars) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < steps.size(); i++) {
            AgentStep step = steps.get(i);
            builder.append(step.describeForPrompt()).append('\n');
        }
        String text = builder.toString();
        int limit = maxChars <= 0 ? 4000 : maxChars;
        if (text.length() <= limit) {
            return text;
        }
        return "…\n" + text.substring(text.length() - limit);
    }

    public String compactSources(int maxChars) {
        StringBuilder builder = new StringBuilder();
        int index = 1;
        for (SourceRecord source : sources.values()) {
            builder.append(index++).append(". ").append(source.describe()).append('\n');
            if (!source.excerpt.isEmpty()) {
                String excerpt = source.excerpt;
                if (excerpt.length() > 400) {
                    excerpt = excerpt.substring(0, 400) + "…";
                }
                builder.append("   ").append(excerpt.replace('\n', ' ')).append('\n');
            }
        }
        String text = builder.toString();
        int limit = maxChars <= 0 ? 6000 : maxChars;
        return text.length() <= limit ? text : text.substring(0, limit) + "\n…";
    }
}
