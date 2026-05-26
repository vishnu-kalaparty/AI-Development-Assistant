package com.aidevassistant.graph;

import com.aidevassistant.agent.AgentStep;
import com.aidevassistant.config.GeminiApiErrors;
import com.aidevassistant.service.AgentToolsService;
import com.aidevassistant.service.CodeIndexService;
import com.aidevassistant.service.PromptService;
import com.aidevassistant.service.WorkflowDraftStore;
import com.aidevassistant.service.WorkflowSnapshotStore;
import com.aidevassistant.tools.SandboxTools;
import com.aidevassistant.tools.StepRecorder;
import com.aidevassistant.tools.SandboxContext;
import com.aidevassistant.tools.WorkflowRunContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

@Component
public class SdlcNodes {

    private static final Logger log = LoggerFactory.getLogger(SdlcNodes.class);

    private final StageAgentFactory agentFactory;
    private final PromptService promptService;
    private final AgentToolsService agentToolsService;
    private final SandboxTools sandboxTools;
    private final StepRecorder recorder;
    private final WorkflowSnapshotStore snapshotStore;
    private final CodeIndexService codeIndexService;
    private final WorkflowDraftStore draftStore;

    public SdlcNodes(
            StageAgentFactory agentFactory,
            PromptService promptService,
            AgentToolsService agentToolsService,
            SandboxTools sandboxTools,
            StepRecorder recorder,
            WorkflowSnapshotStore snapshotStore,
            CodeIndexService codeIndexService,
            WorkflowDraftStore draftStore
    ) {
        this.agentFactory = agentFactory;
        this.promptService = promptService;
        this.agentToolsService = agentToolsService;
        this.sandboxTools = sandboxTools;
        this.recorder = recorder;
        this.snapshotStore = snapshotStore;
        this.codeIndexService = codeIndexService;
        this.draftStore = draftStore;
    }

    public Map<String, Object> requirements(SdlcState state) {
        if (!state.requirementsDoc().isBlank()) {
            return Map.of(SdlcState.PHASE, SdlcGraphRouter.ARCHITECTURE);
        }
        return runDocumentStage(
                state,
                SdlcGraphRouter.REQUIREMENTS,
                SdlcGraphRouter.ARCHITECTURE,
                SdlcState.REQUIREMENTS,
                promptService.getRequirementsAnalystSystemPrompt(state.analystPersona()),
                promptService.getRequirementsAnalystTaskPrompt(state.task()),
                "Drafted requirements"
        );
    }

    public Map<String, Object> architecture(SdlcState state) {
        if (!state.architectureDoc().isBlank()) {
            return Map.of(SdlcState.PHASE, SdlcGraphRouter.IMPLEMENTATION);
        }
        String taskPrompt = promptService.getArchitectTaskPrompt(state.task(), state.requirementsDoc())
                + sandboxContextNote(state);
        return runDocumentStage(
                state,
                SdlcGraphRouter.ARCHITECTURE,
                SdlcGraphRouter.IMPLEMENTATION,
                SdlcState.ARCHITECTURE,
                promptService.getArchitectSystemPrompt(state.architectPersona()),
                taskPrompt,
                "Drafted architecture"
        );
    }

    public Map<String, Object> implementation(SdlcState state) {
        String user = promptService.getEngineerTaskPrompt(state.task(), state.requirementsDoc(),
                state.architectureDoc(), state.testReport(), state.reviewNotes(), state.humanNotes())
                + sandboxContextNote(state);
        return runToolStage(
                state,
                SdlcGraphRouter.IMPLEMENTATION,
                promptService.getEngineerSystemPrompt(state.engineerPersona()),
                user,
                sandboxTools.tools(),
                state.maxSteps(),
                text -> toolResult(state, text, SdlcState.ENGINEER_SUMMARY, SdlcGraphRouter.TESTING, null)
        );
    }

    public Map<String, Object> testing(SdlcState state) {
        return runToolStage(
                state,
                SdlcGraphRouter.TESTING,
                promptService.getQaSystemPrompt(state.reviewerPersona()),
                promptService.getQaTaskPrompt(state.task(), state.engineerSummary(), listing(),
                        state.architectureDoc())
                        + sandboxContextNote(state),
                sandboxTools.tools(),
                Math.max(4, state.maxSteps() - 2),
                text -> {
                    boolean passed = looksLikePass(text);
                    Map<String, Object> result = toolResult(state, text, SdlcState.TEST_REPORT, SdlcGraphRouter.TESTING, null);
                    result.put(SdlcState.TESTS_PASSED, passed);
                    if (!passed) {
                        if (state.iteration() >= state.maxRounds()) {
                            result.put(SdlcState.STATUS, "exhausted");
                            result.put(SdlcState.PHASE, SdlcGraphRouter.APPROVAL);
                        } else {
                            result.put(SdlcState.ITERATION, state.iteration() + 1);
                            result.put(SdlcState.PHASE, SdlcGraphRouter.IMPLEMENTATION);
                        }
                    } else {
                        result.put(SdlcState.PHASE, state.includeReviewer() ? SdlcGraphRouter.REVIEW : SdlcGraphRouter.APPROVAL);
                    }
                    return result;
                }
        );
    }

    public Map<String, Object> review(SdlcState state) {
        return runToolStage(
                state,
                SdlcGraphRouter.REVIEW,
                promptService.getReviewerSystemPrompt(state.reviewerPersona()),
                promptService.getReviewerTaskPrompt(state.task(), state.engineerSummary(), listing(),
                        state.architectureDoc())
                        + sandboxContextNote(state),
                sandboxTools.tools(),
                Math.max(4, state.maxSteps() - 2),
                text -> {
                    String verdict = extractVerdict(text);
                    Map<String, Object> result = toolResult(state, text, SdlcState.REVIEW_NOTES, SdlcGraphRouter.REVIEW, null);
                    result.put(SdlcState.REVIEW_VERDICT, verdict);
                    result.put(SdlcState.SUMMARY, join(state.engineerSummary(), text));
                    if ("CHANGES_REQUESTED".equals(verdict)) {
                        if (state.iteration() >= state.maxRounds()) {
                            result.put(SdlcState.STATUS, "exhausted");
                            result.put(SdlcState.PHASE, SdlcGraphRouter.APPROVAL);
                        } else {
                            result.put(SdlcState.ITERATION, state.iteration() + 1);
                            result.put(SdlcState.PHASE, SdlcGraphRouter.IMPLEMENTATION);
                        }
                    } else {
                        result.put(SdlcState.PHASE, SdlcGraphRouter.APPROVAL);
                    }
                    return result;
                }
        );
    }

    public Map<String, Object> approval(SdlcState state) {
        if (state.hasError()) {
            return Map.of();
        }
        String decision = state.humanDecision();
        if (state.awaitHuman() && (decision == null || decision.isBlank())) {
            return updates(
                    SdlcState.STATUS, "awaiting_human",
                    SdlcState.PHASE, SdlcGraphRouter.APPROVAL,
                    SdlcState.SUMMARY, defaultSummary(state)
            );
        }
        if ("CHANGES_REQUESTED".equalsIgnoreCase(decision)) {
            if (state.iteration() >= state.maxRounds()) {
                return updates(
                        SdlcState.STATUS, "exhausted",
                        SdlcState.PHASE, SdlcGraphRouter.APPROVAL,
                        SdlcState.SUMMARY, defaultSummary(state)
                );
            }
            return updates(
                    SdlcState.STATUS, "running",
                    SdlcState.PHASE, SdlcGraphRouter.IMPLEMENTATION,
                    SdlcState.ITERATION, state.iteration() + 1,
                    SdlcState.ENGINEER_SUMMARY, "",
                    SdlcState.TEST_REPORT, "",
                    SdlcState.TESTS_PASSED, Boolean.FALSE,
                    SdlcState.REVIEW_VERDICT, "",
                    SdlcState.HUMAN_DECISION, ""
            );
        }
        String status = "exhausted".equals(state.status()) ? "exhausted" : "completed";
        if (!state.testsPassed() && !"completed".equals(status)) {
            status = "exhausted".equals(state.status()) ? "exhausted" : "needs_changes";
        }
        if ("CHANGES_REQUESTED".equalsIgnoreCase(state.reviewVerdict()) && "completed".equals(status)) {
            status = "needs_changes";
        }
        return updates(
                SdlcState.STATUS, status,
                SdlcState.PHASE, "complete",
                SdlcState.SUMMARY, defaultSummary(state)
        );
    }

    private Map<String, Object> runDocumentStage(
            SdlcState state,
            String stage,
            String nextPhase,
            String docKey,
            String systemPrompt,
            String taskPrompt,
            String fallbackThought
    ) {
        if (state.hasError()) {
            return Map.of();
        }
        try (WorkflowRunContext.Scope ignored = WorkflowRunContext.scope(state.workflowId())) {
            recorder.begin(state.workflowId(), stage);
            publishPhase(state, stage);
            String text = agentFactory.run(systemPrompt, taskPrompt, null, 1);
            List<AgentStep> merged = StepRecorder.mergeSteps(
                    snapshotStore.get(state.workflowId()),
                    recorder.steps(),
                    stage
            );
            if (merged.isEmpty()) {
                merged = thoughtStep(stage, fallbackThought, text);
            }
            return updates(
                    docKey, text,
                    SdlcState.PHASE, nextPhase,
                    SdlcState.STATUS, "running",
                    SdlcState.STEPS, merged
            );
        } catch (Exception e) {
            return fail(stage, e);
        } finally {
            recorder.clear();
        }
    }

    private Map<String, Object> runToolStage(
            SdlcState state,
            String stage,
            String systemPrompt,
            String taskPrompt,
            Object tools,
            int maxSteps,
            Function<String, Map<String, Object>> onSuccess
    ) {
        if (state.hasError()) {
            return Map.of();
        }
        try (
                WorkflowRunContext.Scope ignored = WorkflowRunContext.scope(state.workflowId());
                SandboxContext.Scope sandboxScope = SandboxContext.scope(state.sandboxPath())
        ) {
            recorder.begin(state.workflowId(), stage);
            publishPhase(state, stage);
            logToolStageStart(state, stage, maxSteps);
            String text = agentFactory.run(systemPrompt, taskPrompt, tools, maxSteps);
            logToolStageEnd(state, stage);
            return onSuccess.apply(text);
        } catch (Exception e) {
            logToolStageEnd(state, stage);
            return fail(stage, e);
        } finally {
            recorder.clear();
        }
    }

    private void logToolStageStart(SdlcState state, String stage, int maxSteps) {
        String sandbox = state.sandboxPath();
        int filesOnDisk = 0;
        if (!sandbox.isBlank()) {
            filesOnDisk = agentToolsService.listFilePaths(sandbox, true).size();
        }
        CodeIndexService.ProjectIndexStats indexStats = codeIndexService.projectIndexStats(sandbox);
        boolean draftMode = draftStore.isActive(state.workflowId());
        log.info(
                "SDLC {} stage start workflowId={} sandbox={} maxToolCalls={} filesOnDisk={} draftMode={} neo4jReady={} indexedFiles={} indexedChunks={} semanticSearchReady={}",
                stage,
                state.workflowId(),
                sandbox,
                maxSteps,
                filesOnDisk,
                draftMode,
                codeIndexService.isReady(),
                indexStats.files(),
                indexStats.chunks(),
                codeIndexService.isEnabled() && indexStats.searchable()
        );
    }

    private void logToolStageEnd(SdlcState state, String stage) {
        List<String> tools = recorder.toolNamesCalled();
        boolean usedSemantic = recorder.usedSemanticSearch();
        CodeIndexService.ProjectIndexStats indexStats = codeIndexService.projectIndexStats(state.sandboxPath());
        log.info(
                "SDLC {} stage end workflowId={} semanticSearchCalled={} toolsInvoked={}",
                stage,
                state.workflowId(),
                usedSemantic,
                tools.isEmpty() ? "[]" : tools
        );
        if (!usedSemantic && indexStats.searchable() && !tools.isEmpty()) {
            log.warn(
                    "SDLC {} workflowId={}: LLM skipped semantic_search despite {} indexed chunks ({} files); tools used: {}",
                    stage,
                    state.workflowId(),
                    indexStats.chunks(),
                    indexStats.files(),
                    tools
            );
        }
    }

    private Map<String, Object> toolResult(
            SdlcState state,
            String text,
            String textKey,
            String phase,
            String extraStatus
    ) {
        List<AgentStep> recorded = recorder.steps();
        List<AgentStep> merged = StepRecorder.mergeSteps(snapshotStore.get(state.workflowId()), recorded, phase);
        if (merged.isEmpty()) {
            merged = thoughtStep(phase, text, text);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(textKey, text);
        result.put(SdlcState.PHASE, phase);
        result.put(SdlcState.STEPS, merged);
        result.put(SdlcState.FILES_WRITTEN, recorder.filesWritten());
        result.put(SdlcState.SUMMARY, join(state.summary(), text));
        if (extraStatus != null) {
            result.put(SdlcState.STATUS, extraStatus);
        }
        return result;
    }

    private Map<String, Object> fail(String phase, Exception e) {
        log.error("SDLC {} node failed", phase, e);
        String detail = GeminiApiErrors.toUserMessage(e);
        return updates(
                SdlcState.STATUS, "error",
                SdlcState.ERROR, detail,
                SdlcState.PHASE, phase,
                SdlcState.SUMMARY, "Error in " + phase + ": " + detail
        );
    }

    private String listing() {
        return agentToolsService.listFiles("", true);
    }

    private void publishPhase(SdlcState state, String phase) {
        if (state.workflowId().isBlank()) {
            return;
        }
        snapshotStore.merge(state.workflowId(), Map.of(
                SdlcState.PHASE, phase,
                SdlcState.STATUS, "running",
                SdlcState.SUMMARY, "Running " + phase + "…"
        ));
    }

    private String sandboxContextNote(SdlcState state) {
        StringBuilder note = new StringBuilder();
        if (!state.sandboxPath().isBlank()) {
            note.append("\n\nWorkflow sandbox folder (all paths are relative to this root): ")
                    .append(state.sandboxPath());
        }
        if (!state.architectureDoc().isBlank()) {
            note.append("\n\nApproved architecture — follow docs/README.md and these paths exactly:\n")
                    .append(state.architectureDoc());
        } else {
            note.append("\n\n").append(PromptService.SANDBOX_BASE_RULES);
            note.append(PromptService.ARCHITECTURE_PATTERN_GUIDE);
        }
        if (state.sandboxPath().isBlank()) {
            return note.toString();
        }
        try (SandboxContext.Scope ignored = SandboxContext.scope(state.sandboxPath())) {
            List<String> paths = agentToolsService.listFilePaths(true);
            if (!paths.isEmpty()) {
                note.append("\nExisting project (Neo4j index when enabled). ")
                        .append("Default: semantic_search first for quick snippets; read_file full files only when you need more context or snippets conflict.\n");
                note.append(paths.size()).append(" file(s) on disk — call list_files if you need the full tree.");
            }
        }
        return note.toString();
    }

    private static List<AgentStep> thoughtStep(String stage, String thought, String observation) {
        List<AgentStep> steps = new ArrayList<>();
        steps.add(new AgentStep(stage, 1, thought, null, null, observation));
        return steps;
    }

    static boolean looksLikePass(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String upper = text.toUpperCase(Locale.ROOT);
        if (upper.contains("TESTS_FAILED") || upper.contains("TEST FAILED") || upper.contains("FAILURES:")) {
            return false;
        }
        return upper.contains("TESTS_PASSED") || upper.contains("ALL TESTS PASSED") || upper.contains("APPROVE");
    }

    static String extractVerdict(String text) {
        if (text == null) {
            return "CHANGES_REQUESTED";
        }
        String upper = text.toUpperCase(Locale.ROOT);
        if (upper.contains("CHANGES_REQUESTED")) {
            return "CHANGES_REQUESTED";
        }
        if (upper.contains("APPROVE")) {
            return "APPROVE";
        }
        return "CHANGES_REQUESTED";
    }

    private static String defaultSummary(SdlcState state) {
        if (!state.summary().isBlank()) {
            return state.summary();
        }
        return join(state.engineerSummary(), state.reviewNotes());
    }

    private static String join(String left, String right) {
        StringBuilder builder = new StringBuilder();
        if (left != null && !left.isBlank()) {
            builder.append(left.trim());
        }
        if (right != null && !right.isBlank()) {
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append(right.trim());
        }
        return builder.toString();
    }

    static Map<String, Object> updates(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            Object value = kv[i + 1];
            if (value != null) {
                map.put(String.valueOf(kv[i]), value);
            }
        }
        return map;
    }
}
