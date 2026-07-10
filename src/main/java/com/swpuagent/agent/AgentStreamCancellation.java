package com.swpuagent.agent;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class AgentStreamCancellation {

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicReference<Runnable> cancelAction = new AtomicReference<>();

    public boolean isCancelled() {
        return cancelled.get();
    }

    public void bind(Runnable action) {
        cancelAction.set(action);
        if (cancelled.get()) {
            runCancelAction();
        }
    }

    public void cancel() {
        if (cancelled.compareAndSet(false, true)) {
            runCancelAction();
        }
    }

    private void runCancelAction() {
        Runnable action = cancelAction.getAndSet(null);
        if (action != null) {
            action.run();
        }
    }
}
