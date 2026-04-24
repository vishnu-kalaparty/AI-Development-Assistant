package com.aidevassistant.dto;

import java.util.ArrayList;
import java.util.List;

public class AgentRunResponse {
    private String status;
    private String summary;
    private List<AgentStepDTO> steps = new ArrayList<>();
    private List<String> filesWritten = new ArrayList<>();
    private List<String> sandboxFiles = new ArrayList<>();

    public AgentRunResponse() {}

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<AgentStepDTO> getSteps() {
        return steps;
    }

    public void setSteps(List<AgentStepDTO> steps) {
        this.steps = steps;
    }

    public List<String> getFilesWritten() {
        return filesWritten;
    }

    public void setFilesWritten(List<String> filesWritten) {
        this.filesWritten = filesWritten;
    }

    public List<String> getSandboxFiles() {
        return sandboxFiles;
    }

    public void setSandboxFiles(List<String> sandboxFiles) {
        this.sandboxFiles = sandboxFiles;
    }
}
