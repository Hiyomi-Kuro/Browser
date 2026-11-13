package com.kaori.browser.ai.api;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Cooperative cancellation for an AI request.
 *
 * <p>{@link #cancel()} also runs a registered abort hook so a blocking socket read is
 * interrupted instead of waiting for its read timeout.</p>
 */
public final class CancelToken {

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicReference<Runnable> abortHook = new AtomicReference<>(null);

    public void cancel() {
        cancelled.set(true);
        Runnable hook = abortHook.getAndSet(null);
        if (hook != null) {
            try {
                hook.run();
            } catch (RuntimeException ignored) {
                // Aborting a connection must never crash the caller.
            }
        }
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public void throwIfCancelled() throws AiException {
        if (cancelled.get()) {
            throw new AiException(AiException.Kind.CANCELLED, 0, "Request cancelled by user");
        }
    }

    public void setAbortHook(Runnable hook) {
        abortHook.set(hook);
        if (cancelled.get()) {
            Runnable current = abortHook.getAndSet(null);
            if (current != null) {
                try {
                    current.run();
                } catch (RuntimeException ignored) {
                }
            }
        }
    }

    public void clearAbortHook() {
        abortHook.set(null);
    }
}
