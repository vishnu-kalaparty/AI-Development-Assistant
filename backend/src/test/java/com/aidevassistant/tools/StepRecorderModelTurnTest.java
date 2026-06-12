package com.aidevassistant.tools;

import com.aidevassistant.service.WorkflowSnapshotStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StepRecorderModelTurnTest {

    private StepRecorder recorder;

    @BeforeEach
    void setUp() {
        recorder = new StepRecorder(new WorkflowSnapshotStore());
        WorkflowRunContext.setWorkflowId("wf-model-turn");
        recorder.begin("wf-model-turn", "implementation");
    }

    @AfterEach
    void tearDown() {
        recorder.clear();
        WorkflowRunContext.clear();
    }

    @Test
    void recordModelTurnCombinesThinkingAndResponse() {
        recorder.recordModelTurn("Plan files", "I will write src/app.py", null);

        assertEquals(1, recorder.steps().size());
        var step = recorder.steps().get(0);
        assertEquals("model", step.getToolName());
        assertTrue(step.getThought().contains("Plan files"));
        assertTrue(step.getObservation().contains("write src/app.py"));
    }

    @Test
    void recordModelTurnSkipsEmptyTurns() {
        recorder.recordModelTurn(null, null, null);
        assertTrue(recorder.steps().isEmpty());
    }
}
