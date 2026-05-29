package com.aidevassistant.service;

import com.aidevassistant.dto.DraftFileEntryDTO;
import com.aidevassistant.dto.FileResponse;
import com.aidevassistant.dto.WorkflowDecisionRequest;
import com.aidevassistant.dto.WorkflowRunResponse;
import com.aidevassistant.dto.WorkflowStartRequest;
import com.aidevassistant.graph.SdlcGraphBuilder;
import com.aidevassistant.graph.SdlcGraphMapper;
import com.aidevassistant.graph.SdlcState;
import jakarta.annotation.PreDestroy;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.RunnableConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class LangGraphWorkflowEngine implements WorkflowEngine {

    private static final Logger log = LoggerFactory.getLogger(LangGraphWorkflowEngine.class);

    private final SdlcGraphBuilder graphBuilder;
    private final SdlcGraphMapper mapper;
    private final PersonaService personaService;
    private final AgentToolsService agentToolsService;
    private final WorkflowSnapshotStore snapshotStore;
    private final WorkflowRunTracker runTracker;
    private final WorkflowDraftStore draftStore;
    private final CodeIndexService codeIndexService;
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "sdlc-workflow");
        t.setDaemon(true);
        return t;
    });

    @Value("${agent.max-steps:8}")
    private int maxSteps;

    @Value("${agent.max-review-rounds:1}")
    private int maxReviewRounds;

    @Value("${agent.approval.hitl:true}")
    private boolean hitlDefault;

    public LangGraphWorkflowEngine(
            SdlcGraphBuilder graphBuilder,
            SdlcGraphMapper mapper,
            PersonaService personaService,
            AgentToolsService agentToolsService,
            WorkflowSnapshotStore snapshotStore,
            WorkflowRunTracker runTracker,
            WorkflowDraftStore draftStore,
            CodeIndexService codeIndexService
    ) {
        this.graphBuilder = graphBuilder;
        this.mapper = mapper;
        this.personaService = personaService;
        this.agentToolsService = agentToolsService;
        this.snapshotStore = snapshotStore;
        this.runTracker = runTracker;
        this.draftStore = draftStore;
        this.codeIndexService = codeIndexService;
    }

    @Override
    public boolean knows(String workflowId) {
        return workflowId != null && snapshotStore.get(workflowId) != null;
    }

    @Override
    public WorkflowRunResponse start(WorkflowStartRequest request, boolean awaitHuman) {
        validateTask(request);
        String workflowId = UUID.randomUUID().toString();
        Map<String, Object> input = initialState(workflowId, request, awaitHuman);
        if (awaitHuman && hitlDefault) {
            draftStore.begin(workflowId, String.valueOf(input.get(SdlcState.SANDBOX_PATH)), true);
        }
        SdlcState seed = new SdlcState(input);
        snapshotStore.put(workflowId, seed);
        var future = executor.submit(() -> runGraphAsync(workflowId, GraphInput.args(input), config(workflowId), awaitHuman));
        runTracker.register(workflowId, future);
        return mapper.toResponse(seed);
    }

    @Override
    public WorkflowRunResponse startSync(WorkflowStartRequest request, boolean awaitHuman) {
        validateTask(request);
        String workflowId = UUID.randomUUID().toString();
        Map<String, Object> input = initialState(workflowId, request, awaitHuman);
        if (awaitHuman && hitlDefault) {
            draftStore.begin(workflowId, String.valueOf(input.get(SdlcState.SANDBOX_PATH)), true);
        }
        SdlcState state = runGraph(graphBuilder.graph(awaitHuman), GraphInput.args(input), config(workflowId), awaitHuman, workflowId);
        snapshotStore.put(workflowId, state);
        return mapper.toResponse(state);
    }

    @Override
    public WorkflowRunResponse get(String workflowId) {
        SdlcState state = snapshotStore.get(workflowId);
        if (state == null) {
            throw new IllegalArgumentException("Unknown workflow: " + workflowId);
        }
        return mapper.toResponse(state);
    }

    @Override
    public WorkflowRunResponse decide(String workflowId, WorkflowDecisionRequest request) {
        SdlcState current = snapshotStore.get(workflowId);
        if (current == null) {
            throw new IllegalArgumentException("Unknown workflow: " + workflowId);
        }
        if (!"awaiting_human".equals(current.status())) {
            return mapper.toResponse(current);
        }
        boolean approved = request != null && request.isApproved();
        String notes = request == null || request.getNotes() == null ? "" : request.getNotes();
        if (approved) {
            draftStore.flush(workflowId, agentToolsService, codeIndexService);
        } else {
            draftStore.clearDrafts(workflowId);
        }
        RunnableConfig runnable = config(workflowId);
        Map<String, Object> resumed = new LinkedHashMap<>(current.data());
        resumed.put(SdlcState.STATUS, "running");
        resumed.put(SdlcState.PHASE, "approval");
        resumed.put(SdlcState.HUMAN_DECISION, approved ? "APPROVE" : "CHANGES_REQUESTED");
        resumed.put(SdlcState.HUMAN_NOTES, notes);
        snapshotStore.put(workflowId, new SdlcState(resumed));
        try {
            graphBuilder.graph(true).updateState(runnable, Map.of(
                    SdlcState.HUMAN_DECISION, approved ? "APPROVE" : "CHANGES_REQUESTED",
                    SdlcState.HUMAN_NOTES, notes
            ));
        } catch (Exception e) {
            log.warn("Checkpoint update failed, continuing with snapshot: {}", e.getMessage());
        }
        var future = executor.submit(() -> runGraphAsync(workflowId, GraphInput.resume(), runnable, true));
        runTracker.register(workflowId, future);
        return mapper.toResponse(snapshotStore.get(workflowId));
    }

    @Override
    public WorkflowRunResponse cancel(String workflowId) {
        if (workflowId == null || workflowId.isBlank()) {
            throw new IllegalArgumentException("Workflow id is required");
        }
        SdlcState existing = snapshotStore.get(workflowId);
        if (existing == null) {
            throw new IllegalArgumentException("Unknown workflow: " + workflowId);
        }
        if ("completed".equals(existing.status()) || "cancelled".equals(existing.status()) || "error".equals(existing.status())) {
            return mapper.toResponse(existing);
        }
        runTracker.cancel(workflowId);
        draftStore.suspend(workflowId);
        snapshotStore.merge(workflowId, Map.of(
                SdlcState.STATUS, "cancelled",
                SdlcState.PHASE, "complete",
                SdlcState.SUMMARY, "Stopped by user"
        ));
        return mapper.toResponse(snapshotStore.get(workflowId));
    }

    @Override
    public FileResponse readDraftCompare(String workflowId, String filePath) {
        WorkflowDraftStore.DraftCompare compare = draftStore.readCompare(workflowId, filePath, agentToolsService);
        if (compare == null) {
            return null;
        }
        return new FileResponse(compare.content(), "Draft read successfully", compare.baseline(), compare.status());
    }

    @Override
    public List<DraftFileEntryDTO> draftManifest(String workflowId) {
        return draftStore.draftManifest(workflowId, agentToolsService);
    }

    SdlcState getState(String workflowId) {
        return snapshotStore.get(workflowId);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    private void runGraphAsync(
            String workflowId,
            GraphInput input,
            RunnableConfig config,
            boolean awaitHuman
    ) {
        try {
            SdlcState state = runGraph(graphBuilder.graph(awaitHuman), input, config, awaitHuman, workflowId);
            snapshotStore.put(workflowId, state);
        } finally {
            runTracker.unregister(workflowId);
        }
    }

    private Map<String, Object> initialState(String workflowId, WorkflowStartRequest request, boolean awaitHuman) {
        String sandboxPath = agentToolsService.resolveWorkflowSandboxPath(
                request.getParentFolder(),
                request.getFolderName(),
                workflowId
        );
        Map<String, Object> input = new LinkedHashMap<>();
        input.put(SdlcState.WORKFLOW_ID, workflowId);
        input.put(SdlcState.SANDBOX_PATH, sandboxPath);
        input.put(SdlcState.TASK, request.getTask().trim());
        input.put(SdlcState.ENGINEER_PERSONA, personaService.load(blankToDefault(request.getEngineerPersonaPath(), WorkflowService.DEFAULT_ENGINEER)));
        input.put(SdlcState.REVIEWER_PERSONA, personaService.load(blankToDefault(request.getReviewerPersonaPath(), WorkflowService.DEFAULT_REVIEWER)));
        input.put(SdlcState.ARCHITECT_PERSONA, personaService.load(blankToDefault(request.getArchitectPersonaPath(), WorkflowService.DEFAULT_ARCHITECT)));
        input.put(SdlcState.ANALYST_PERSONA, personaService.load(blankToDefault(request.getAnalystPersonaPath(), WorkflowService.DEFAULT_ANALYST)));
        input.put(SdlcState.ITERATION, 0);
        input.put(SdlcState.MAX_ROUNDS, Math.max(1, maxReviewRounds));
        input.put(SdlcState.MAX_STEPS, Math.max(1, maxSteps));
        input.put(SdlcState.AWAIT_HUMAN, awaitHuman);
        input.put(SdlcState.INCLUDE_REVIEWER, request.isIncludeReviewer());
        input.put(SdlcState.STATUS, "running");
        input.put(SdlcState.PHASE, "requirements");
        input.put(SdlcState.SUMMARY, "Workflow started…");
        return input;
    }

    private SdlcState runGraph(
            CompiledGraph<SdlcState> graph,
            GraphInput input,
            RunnableConfig config,
            boolean awaitHuman,
            String workflowId
    ) {
        NodeOutput<SdlcState> last = null;
        try {
            for (NodeOutput<SdlcState> item : graph.stream(input, config)) {
                if (runTracker.isCancelled(workflowId)) {
                    return cancelledState(workflowId, snapshotStore.get(workflowId));
                }
                last = item;
                if (last.state() != null) {
                    snapshotStore.put(workflowId, last.state());
                }
            }
        } catch (WorkflowCancelledException e) {
            log.info("Workflow {} cancelled", workflowId);
            return cancelledState(workflowId, snapshotStore.get(workflowId));
        } catch (Exception e) {
            if (runTracker.isCancelled(workflowId) || isCancellationCause(e)) {
                log.info("Workflow {} cancelled during execution", workflowId);
                return cancelledState(workflowId, snapshotStore.get(workflowId));
            }
            log.error("Workflow {} failed", config.threadId().orElse("unknown"), e);
            String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            Map<String, Object> error = new LinkedHashMap<>();
            error.put(SdlcState.STATUS, "error");
            error.put(SdlcState.ERROR, detail);
            error.put(SdlcState.SUMMARY, "Error: " + detail);
            if (config.threadId().isPresent()) {
                error.put(SdlcState.WORKFLOW_ID, config.threadId().get());
            }
            SdlcState failed = new SdlcState(error);
            snapshotStore.put(workflowId, failed);
            return failed;
        }
        if (last == null || last.state() == null) {
            SdlcState empty = new SdlcState(Map.of(SdlcState.STATUS, "error", SdlcState.SUMMARY, "Graph produced no state"));
            snapshotStore.put(workflowId, empty);
            return empty;
        }
        SdlcState state = last.state();
        if (awaitHuman && !last.isEND() && !"error".equals(state.status()) && !"completed".equals(state.status())) {
            Map<String, Object> paused = new LinkedHashMap<>(state.data());
            paused.put(SdlcState.STATUS, "awaiting_human");
            paused.put(SdlcState.PHASE, "approval");
            state = new SdlcState(paused);
        }
        snapshotStore.put(workflowId, state);
        return state;
    }

    private static RunnableConfig config(String workflowId) {
        return RunnableConfig.builder().threadId(workflowId).build();
    }

    private SdlcState cancelledState(String workflowId, SdlcState existing) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (existing != null) {
            data.putAll(existing.data());
        }
        data.put(SdlcState.WORKFLOW_ID, workflowId);
        data.put(SdlcState.STATUS, "cancelled");
        data.put(SdlcState.PHASE, "complete");
        data.put(SdlcState.SUMMARY, "Stopped by user");
        SdlcState cancelled = new SdlcState(data);
        snapshotStore.put(workflowId, cancelled);
        return cancelled;
    }

    private static boolean isCancellationCause(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof WorkflowCancelledException || current instanceof InterruptedException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static void validateTask(WorkflowStartRequest request) {
        if (request == null || request.getTask() == null || request.getTask().isBlank()) {
            throw new IllegalArgumentException("Task is required");
        }
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
