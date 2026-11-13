package com.kaori.browser.ai.agent;

/** Lifecycle of an agent run. */
public enum AgentStatus {
    IDLE,
    RUNNING,
    WAITING_CONFIRMATION,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED,
    STEP_LIMIT,
    TIMEOUT,
    DENIED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED
                || this == STEP_LIMIT || this == TIMEOUT || this == DENIED;
    }

    public boolean isActive() {
        return this == RUNNING || this == WAITING_CONFIRMATION || this == PAUSED;
    }
}
