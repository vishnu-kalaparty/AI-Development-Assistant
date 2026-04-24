package com.aidevassistant.agent;

import java.util.ArrayList;
import java.util.List;

public class AgentRun {
    private final String status;
    private final String summary;
    private final List<AgentStep> steps;
    private final List<String> filesWritten;

    public AgentRun(String status, String summary, List<AgentStep> steps, List<String> filesWritten) {
        this.status = status;
        this.summary = summary;
        this.steps = steps != null ? steps : new ArrayList<>();
        this.filesWritten = filesWritten != null ? filesWritten : new ArrayList<>();
    }

    public static AgentRun completed(String summary, List<AgentStep> steps, List<String> filesWritten) {
        return new AgentRun("completed", summary, steps, filesWritten);
    }

    public static AgentRun exhausted(String summary, List<AgentStep> steps, List<String> filesWritten) {
        return new AgentRun("exhausted", summary, steps, filesWritten);
    }

    public static AgentRun error(String message) {
        return new AgentRun("error", message, List.of(), List.of());
    }

    public String getStatus() {
        return status;
    }

    public String getSummary() {
        return summary;
    }

    public List<AgentStep> getSteps() {
        return steps;
    }

    public List<String> getFilesWritten() {
        return filesWritten;
    }
}
