package com.aidevassistant.tools;

import com.aidevassistant.agent.AgentStep;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StepRecorderMergeTest {

    @Test
    void mergeStepsKeepsOtherStagesAndReplacesCurrentStage() {
        List<AgentStep> existing = List.of(
                new AgentStep("requirements", 1, "Draft", null, null, "req doc"),
                new AgentStep("architecture", 1, "Plan", null, null, "arch doc"),
                new AgentStep("implementation", 1, "Old", "model", null, "stale")
        );
        List<AgentStep> current = List.of(
                new AgentStep("implementation", 1, "Gemini", "model", null, "new message"),
                new AgentStep("implementation", 2, "Writing sandbox file", "write_file", "src/app.py", "ok")
        );

        List<AgentStep> merged = StepRecorder.mergeSteps(null, current, "implementation");
        assertEquals(2, merged.size());

        List<AgentStep> mergedWithExisting = StepRecorder.mergeSteps(
                new com.aidevassistant.graph.SdlcState(java.util.Map.of("steps", existing)),
                current,
                "implementation"
        );
        assertEquals(4, mergedWithExisting.size());
        assertEquals("requirements", mergedWithExisting.get(0).getStage());
        assertEquals("architecture", mergedWithExisting.get(1).getStage());
        assertEquals("implementation", mergedWithExisting.get(2).getStage());
        assertEquals("model", mergedWithExisting.get(2).getToolName());
    }
}
