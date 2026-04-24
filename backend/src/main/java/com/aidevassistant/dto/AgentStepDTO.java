package com.aidevassistant.dto;

public class AgentStepDTO {
    private String stage;
    private int step;
    private String thought;
    private String toolName;
    private String toolArgs;
    private String observation;

    public AgentStepDTO() {}

    public AgentStepDTO(String stage, int step, String thought, String toolName, String toolArgs, String observation) {
        this.stage = stage;
        this.step = step;
        this.thought = thought;
        this.toolName = toolName;
        this.toolArgs = toolArgs;
        this.observation = observation;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public int getStep() {
        return step;
    }

    public void setStep(int step) {
        this.step = step;
    }

    public String getThought() {
        return thought;
    }

    public void setThought(String thought) {
        this.thought = thought;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getToolArgs() {
        return toolArgs;
    }

    public void setToolArgs(String toolArgs) {
        this.toolArgs = toolArgs;
    }

    public String getObservation() {
        return observation;
    }

    public void setObservation(String observation) {
        this.observation = observation;
    }
}
