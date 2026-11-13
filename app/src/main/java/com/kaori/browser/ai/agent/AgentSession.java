package com.kaori.browser.ai.agent;

import com.kaori.browser.ai.model.SessionIds;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** State of one agent task, including its logs, memory and any tabs it owns. */
public final class AgentSession {

    private final String id;
    private final String task;
    private final long createdAt;
    private long finishedAt;
    private AgentStatus status = AgentStatus.IDLE;
    private String resultSummary = "";
    private String errorMessage = "";
    private int maxSteps = 20;
    private long deadlineMs;
    private boolean research;

    private final AgentMemory memory = new AgentMemory();
    private final AgentTaskLogger logger = new AgentTaskLogger();
    private final List<Integer> ownedTabs = new ArrayList<>();

    public AgentSession(String task) {
        this.id = SessionIds.newSessionId();
        this.task = task == null ? "" : task;
        this.createdAt = System.currentTimeMillis();
    }

    public String getId() {
        return id;
    }

    public String getTask() {
        return task;
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

    public String getResultSummary() {
        return resultSummary;
    }

    public void setResultSummary(String resultSummary) {
        this.resultSummary = resultSummary == null ? "" : resultSummary;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage == null ? "" : errorMessage;
    }

    public int getMaxSteps() {
        return maxSteps;
    }

    public void setMaxSteps(int maxSteps) {
        this.maxSteps = Math.max(0, maxSteps);
    }

    /** @return true when the run has no step cap (0 means unlimited). */
    public boolean isUnlimitedSteps() {
        return maxSteps <= 0;
    }

    public long getDeadlineMs() {
        return deadlineMs;
    }

    public void setDeadlineMs(long deadlineMs) {
        this.deadlineMs = deadlineMs;
    }

    public boolean isDeadlineExceeded() {
        return deadlineMs > 0 && System.currentTimeMillis() > deadlineMs;
    }

    public boolean isResearch() {
        return research;
    }

    public void setResearch(boolean research) {
        this.research = research;
    }

    public AgentMemory getMemory() {
        return memory;
    }

    public AgentTaskLogger getLogger() {
        return logger;
    }

    public void addOwnedTab(int index) {
        if (!ownedTabs.contains(index)) {
            ownedTabs.add(index);
        }
    }

    public List<Integer> getOwnedTabs() {
        return Collections.unmodifiableList(ownedTabs);
    }

    public void clearOwnedTabs() {
        ownedTabs.clear();
    }

    public String describeStatus() {
        return status.name() + " · " + memory.stepCount() + "/" + maxSteps + " steps";
    }
}
