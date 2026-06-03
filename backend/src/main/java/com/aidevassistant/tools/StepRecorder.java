package com.aidevassistant.tools;

import com.aidevassistant.agent.AgentStep;
import com.aidevassistant.graph.SdlcGraphRouter;
import com.aidevassistant.graph.SdlcState;
import com.aidevassistant.service.WorkflowSnapshotStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Records tool and model steps for live SDLC polling. State is keyed by workflow id so
 * Gemini listeners still publish when they run off the graph thread.
 */
@Component
public class StepRecorder {

    private static final Logger log = LoggerFactory.getLogger(StepRecorder.class);

    private final Map<String, RecordingState> byWorkflow = new ConcurrentHashMap<>();
    private final ThreadLocal<String> threadWorkflow = new ThreadLocal<>();
    private final WorkflowSnapshotStore snapshotStore;

    public StepRecorder(WorkflowSnapshotStore snapshotStore) {
        this.snapshotStore = snapshotStore;
    }

    public void begin(String workflowId, String stageName) {
        if (workflowId == null || workflowId.isBlank() || stageName == null || stageName.isBlank()) {
            return;
        }
        byWorkflow.put(workflowId, new RecordingState(workflowId, stageName));
        threadWorkflow.set(workflowId);
    }

    public void record(String thought, String toolName, String toolArgs, String observation) {
        RecordingState recording = resolveRecording();
        if (recording == null) {
            return;
        }
        int index = recording.steps.size() + 1;
        recording.steps.add(new AgentStep(recording.stage, index, thought, toolName, toolArgs, observation));
        if (toolName != null && !toolName.isBlank() && !"model".equals(toolName)) {
            recording.toolNames.add(toolName);
            if ("semantic_search".equals(toolName)) {
                recording.semanticSearchUsed = true;
            }
            log.info("SDLC tool call stage={} workflowId={} tool={} args={}",
                    recording.stage, recording.workflowId, toolName, abbreviate(toolArgs, 120));
        }
        if ("write_file".equals(toolName) && toolArgs != null && !toolArgs.isBlank()) {
            recording.filesWritten.add(toolArgs);
        }
        publish(recording, null);
    }

    public void beginModelStream() {
        RecordingState recording = resolveRecording();
        if (recording != null) {
            recording.thinkingBuffer.setLength(0);
            recording.textBuffer.setLength(0);
            recording.modelStreamOpen = true;
        }
    }

    public void endModelStream() {
        RecordingState recording = resolveRecording();
        if (recording != null) {
            recording.modelStreamOpen = false;
        }
    }

    /** Publishes incremental model thought/output while streaming. */
    public void recordModelPartial(String thinking, String text, String toolHint) {
        RecordingState recording = resolveRecording();
        if (recording == null) {
            return;
        }
        if (thinking != null) {
            recording.thinkingBuffer.setLength(0);
            recording.thinkingBuffer.append(thinking);
        }
        if (text != null) {
            recording.textBuffer.setLength(0);
            recording.textBuffer.append(text);
        }
        upsertModelStep(
                recording,
                normalizeThought(recording.thinkingBuffer.toString()),
                ToolOutputLimiter.truncate(firstNonBlank(recording.textBuffer.toString(), toolHint, ""))
        );
        publish(recording, firstNonBlank(recording.textBuffer.toString(), toolHint, recording.thinkingBuffer.toString()));
    }

    public void recordModelTurn(String thinking, String text, String toolHint) {
        RecordingState recording = resolveRecording();
        if (recording == null) {
            return;
        }
        String observation = firstNonBlank(text, toolHint);
        String thought = normalizeThought(thinking);
        if (observation == null && thought == null) {
            return;
        }
        upsertModelStep(recording, thought, observation == null ? "" : ToolOutputLimiter.truncate(observation));
        publish(recording, firstNonBlank(observation, thought));
        recording.modelStreamOpen = false;
    }

    private static void upsertModelStep(RecordingState recording, String thought, String observation) {
        if (!recording.steps.isEmpty()) {
            AgentStep last = recording.steps.get(recording.steps.size() - 1);
            if ("model".equals(last.getToolName()) && recording.stage.equals(last.getStage())) {
                recording.steps.set(
                        recording.steps.size() - 1,
                        new AgentStep(
                                recording.stage,
                                last.getStep(),
                                thought == null ? "" : thought,
                                "model",
                                null,
                                observation == null ? "" : observation
                        )
                );
                return;
            }
        }
        int index = recording.steps.size() + 1;
        recording.steps.add(new AgentStep(
                recording.stage,
                index,
                thought == null ? "" : thought,
                "model",
                null,
                observation == null ? "" : observation
        ));
    }

    public List<AgentStep> steps() {
        RecordingState recording = resolveRecording();
        return recording == null ? List.of() : new ArrayList<>(recording.steps);
    }

    public List<String> filesWritten() {
        RecordingState recording = resolveRecording();
        return recording == null ? List.of() : new ArrayList<>(recording.filesWritten);
    }

    public boolean usedSemanticSearch() {
        RecordingState recording = resolveRecording();
        return recording != null && recording.semanticSearchUsed;
    }

    public List<String> toolNamesCalled() {
        RecordingState recording = resolveRecording();
        return recording == null ? List.of() : new ArrayList<>(recording.toolNames);
    }

    public void clear() {
        String workflowId = threadWorkflow.get();
        if (workflowId != null) {
            byWorkflow.remove(workflowId);
        }
        threadWorkflow.remove();
    }

    private static String abbreviate(String value, int maxLen) {
        if (value == null) {
            return "";
        }
        String trimmed = value.replace('\n', ' ').trim();
        if (trimmed.length() <= maxLen) {
            return trimmed;
        }
        return trimmed.substring(0, maxLen) + "…";
    }

    public static List<AgentStep> mergeSteps(SdlcState existing, List<AgentStep> currentStageSteps, String currentStage) {
        List<AgentStep> merged = new ArrayList<>();
        if (existing != null && currentStage != null) {
            for (AgentStep step : existing.steps()) {
                if (!currentStage.equalsIgnoreCase(step.getStage())) {
                    merged.add(step);
                }
            }
        }
        if (currentStageSteps != null) {
            merged.addAll(currentStageSteps);
        }
        return merged;
    }

    private RecordingState resolveRecording() {
        String workflowId = WorkflowRunContext.workflowId();
        if (workflowId == null || workflowId.isBlank()) {
            workflowId = threadWorkflow.get();
        }
        if ((workflowId == null || workflowId.isBlank()) && byWorkflow.size() == 1) {
            workflowId = byWorkflow.keySet().iterator().next();
        }
        if (workflowId == null || workflowId.isBlank()) {
            return null;
        }
        return byWorkflow.get(workflowId);
    }

    private void publish(RecordingState recording, String latestModelText) {
        String workflowId = recording.workflowId;
        if (workflowId == null || workflowId.isBlank()) {
            return;
        }
        List<AgentStep> mergedSteps = mergeSteps(snapshotStore.get(workflowId), recording.steps, recording.stage);
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put(SdlcState.STEPS, mergedSteps);
        patch.put(SdlcState.FILES_WRITTEN, new ArrayList<>(recording.filesWritten));
        patch.put(SdlcState.STATUS, "running");
        patch.put(SdlcState.PHASE, recording.stage);
        applyLiveSummary(patch, recording.stage, latestModelText, recording);
        snapshotStore.merge(workflowId, patch);
    }

    private void applyLiveSummary(Map<String, Object> patch, String stageName, String latestModelText, RecordingState recording) {
        String summary = liveSummary(stageName, latestModelText, recording);
        if (SdlcGraphRouter.REQUIREMENTS.equals(stageName)) {
            patch.put(SdlcState.REQUIREMENTS, summary);
        } else if (SdlcGraphRouter.ARCHITECTURE.equals(stageName)) {
            patch.put(SdlcState.ARCHITECTURE, summary);
        } else if (SdlcGraphRouter.IMPLEMENTATION.equals(stageName)) {
            patch.put(SdlcState.ENGINEER_SUMMARY, summary);
        } else if (SdlcGraphRouter.TESTING.equals(stageName)) {
            patch.put(SdlcState.TEST_REPORT, summary);
        } else if (SdlcGraphRouter.REVIEW.equals(stageName)) {
            patch.put(SdlcState.REVIEW_NOTES, summary);
        } else {
            patch.put(SdlcState.SUMMARY, summary);
        }
    }

    private String liveSummary(String stageName, String latestModelText, RecordingState recording) {
        StringBuilder summary = new StringBuilder();
        if (latestModelText != null && !latestModelText.isBlank()) {
            summary.append(ToolOutputLimiter.truncate(latestModelText));
        } else {
            summary.append("Running ").append(stageName).append("…");
        }
        if (!recording.filesWritten.isEmpty()) {
            if (!summary.isEmpty()) {
                summary.append("\n\n");
            }
            summary.append("Files written: ").append(String.join(", ", recording.filesWritten));
        }
        return summary.toString();
    }

    private static String normalizeThought(String thinking) {
        if (thinking == null || thinking.isBlank()) {
            return null;
        }
        return ToolOutputLimiter.truncate(thinking);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static final class RecordingState {
        private final String workflowId;
        private final String stage;
        private final List<AgentStep> steps = new ArrayList<>();
        private final List<String> toolNames = new ArrayList<>();
        private final Set<String> filesWritten = new LinkedHashSet<>();
        private boolean semanticSearchUsed;
        private final StringBuilder thinkingBuffer = new StringBuilder();
        private final StringBuilder textBuffer = new StringBuilder();
        private boolean modelStreamOpen;

        private RecordingState(String workflowId, String stage) {
            this.workflowId = workflowId;
            this.stage = stage;
        }
    }
}
