package com.aidevassistant.dto;

import java.util.ArrayList;
import java.util.List;

public class WorkflowStartRequest {
    private String task;
    private String engineerPersonaPath;
    private String reviewerPersonaPath;
    private String architectPersonaPath;
    private String analystPersonaPath;
    private boolean includeReviewer = true;
    private String parentFolder;
    /** Optional target sandbox folder (relative path). Overrides auto runs/{id} when set. */
    private String folderName;
    /** When set, overrides agent.sdlc.use-api for this run (true = Gemini/LangGraph). */
    private Boolean useApi;
    /** When true, run synchronously to completion (agent-style runs). */
    private Boolean blocking;

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

    public String getArchitectPersonaPath() {
        return architectPersonaPath;
    }

    public void setArchitectPersonaPath(String architectPersonaPath) {
        this.architectPersonaPath = architectPersonaPath;
    }

    public String getAnalystPersonaPath() {
        return analystPersonaPath;
    }

    public void setAnalystPersonaPath(String analystPersonaPath) {
        this.analystPersonaPath = analystPersonaPath;
    }

    public boolean isIncludeReviewer() {
        return includeReviewer;
    }

    public void setIncludeReviewer(boolean includeReviewer) {
        this.includeReviewer = includeReviewer;
    }

    public String getParentFolder() {
        return parentFolder;
    }

    public void setParentFolder(String parentFolder) {
        this.parentFolder = parentFolder;
    }

    public String getFolderName() {
        return folderName;
    }

    public void setFolderName(String folderName) {
        this.folderName = folderName;
    }

    public Boolean getUseApi() {
        return useApi;
    }

    public void setUseApi(Boolean useApi) {
        this.useApi = useApi;
    }

    public Boolean getBlocking() {
        return blocking;
    }

    public void setBlocking(Boolean blocking) {
        this.blocking = blocking;
    }
}
