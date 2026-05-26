package com.aidevassistant.dto;

public class WorkflowPhaseDTO {
    private String id;
    private String label;
    private String status;
    private String summary;

    public WorkflowPhaseDTO() {}

    public WorkflowPhaseDTO(String id, String label, String status, String summary) {
        this.id = id;
        this.label = label;
        this.status = status;
        this.summary = summary;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

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
}
