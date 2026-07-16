package com.swpuagent.agent;

public class AgentStreamCancelledException extends RuntimeException {

    public AgentStreamCancelledException() {
        super("Agent stream cancelled");
    }
}
