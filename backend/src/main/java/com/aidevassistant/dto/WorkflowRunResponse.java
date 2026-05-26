package com.aidevassistant.dto;

import java.util.ArrayList;
import java.util.List;

public class WorkflowRunResponse {
    private String workflowId;
    private String status;
    private String currentPhase;
    private String summary;
    private String requirementsDoc;
    private String architectureDoc;
    private String testReport;
    private String reviewVerdict;
    private String reviewNotes;
    private boolean testsPassed;
    private boolean awaitingHuman;
    private List<WorkflowPhaseDTO> phases = new ArrayList<>();
    private List<AgentStepDTO> steps = new ArrayList<>();
    private List<String> filesWritten = new ArrayList<>();
    private List<String> sandboxFiles = new ArrayList<>();
    private String sandboxPath;

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCurrentPhase() {
        return currentPhase;
    }

    public void setCurrentPhase(String currentPhase) {
        this.currentPhase = currentPhase;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getRequirementsDoc() {
        return requirementsDoc;
    }

    public void setRequirementsDoc(String requirementsDoc) {
        this.requirementsDoc = requirementsDoc;
    }

    public String getArchitectureDoc() {
        return architectureDoc;
    }

    public void setArchitectureDoc(String architectureDoc) {
        this.architectureDoc = architectureDoc;
    }

    public String getTestReport() {
        return testReport;
    }

    public void setTestReport(String testReport) {
        this.testReport = testReport;
    }

    public String getReviewVerdict() {
        return reviewVerdict;
    }

    public void setReviewVerdict(String reviewVerdict) {
        this.reviewVerdict = reviewVerdict;
    }

    public String getReviewNotes() {
        return reviewNotes;
    }

    public void setReviewNotes(String reviewNotes) {
        this.reviewNotes = reviewNotes;
    }

    public boolean isTestsPassed() {
        return testsPassed;
    }

    public void setTestsPassed(boolean testsPassed) {
        this.testsPassed = testsPassed;
    }

    public boolean isAwaitingHuman() {
        return awaitingHuman;
    }

    public void setAwaitingHuman(boolean awaitingHuman) {
        this.awaitingHuman = awaitingHuman;
    }

    public List<WorkflowPhaseDTO> getPhases() {
        return phases;
    }

    public void setPhases(List<WorkflowPhaseDTO> phases) {
        this.phases = phases;
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

    public String getSandboxPath() {
        return sandboxPath;
    }

    public void setSandboxPath(String sandboxPath) {
        this.sandboxPath = sandboxPath;
    }
}
