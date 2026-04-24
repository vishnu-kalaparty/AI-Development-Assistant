package com.aidevassistant.dto;

public class AgentRunRequest {
    private String task;
    private String engineerPersonaPath;
    private String reviewerPersonaPath;
    private boolean includeReviewer = true;

    public AgentRunRequest() {}

    public String getTask() {
        return task;
    }

    public void setTask(String task) {
        this.task = task;
    }

    public String getEngineerPersonaPath() {
        return engineerPersonaPath;
    }

    public void setEngineerPersonaPath(String engineerPersonaPath) {
        this.engineerPersonaPath = engineerPersonaPath;
    }

    public String getReviewerPersonaPath() {
        return reviewerPersonaPath;
    }

    public void setReviewerPersonaPath(String reviewerPersonaPath) {
        this.reviewerPersonaPath = reviewerPersonaPath;
    }

    public boolean isIncludeReviewer() {
        return includeReviewer;
    }

    public void setIncludeReviewer(boolean includeReviewer) {
        this.includeReviewer = includeReviewer;
    }
}
