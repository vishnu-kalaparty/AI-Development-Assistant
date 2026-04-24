package com.aidevassistant.agent;

public class AgentStep {
    private final String stage;
    private final int step;
    private final String thought;
    private final String toolName;
    private final String toolArgs;
    private final String observation;

    public AgentStep(String stage, int step, String thought, String toolName, String toolArgs, String observation) {
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

    public int getStep() {
        return step;
    }

    public String getThought() {
        return thought;
    }

    public String getToolName() {
        return toolName;
    }

    public String getToolArgs() {
        return toolArgs;
    }

    public String getObservation() {
        return observation;
    }
}
