package com.aidevassistant.service;

import com.aidevassistant.agent.AgentRun;
import com.aidevassistant.dto.DraftFileEntryDTO;
import com.aidevassistant.dto.FileResponse;
import com.aidevassistant.dto.WorkflowDecisionRequest;
import com.aidevassistant.dto.WorkflowRunResponse;
import com.aidevassistant.dto.WorkflowStartRequest;
import com.aidevassistant.graph.SdlcGraphMapper;
import com.aidevassistant.graph.SdlcState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WorkflowService {

    public static final String DEFAULT_ENGINEER = "development/software_engineers/Software-Engineer.md";
    public static final String DEFAULT_REVIEWER = "quality_testing/reviewers/Code-Reviewer.md";
    public static final String DEFAULT_ARCHITECT = "design_architecture/system_architects/System-Architect.md";
    public static final String DEFAULT_ANALYST = "requirements_engineering/analysts/Requirements-Analyst.md";

    private final MockWorkflowService mockEngine;
    private final LangGraphWorkflowEngine langGraphEngine;
    private final SdlcGraphMapper mapper;
    private final AgentToolsService agentToolsService;
    /** Per-run mock flag when useApi is overridden on start. */
    private final Map<String, Boolean> mockRuns = new ConcurrentHashMap<>();

    @Value("${agent.approval.hitl:true}")
    private boolean hitlDefault;

    /** 0 = mock (default), 1 = real LangGraph + LLM */
    @Value("${agent.sdlc.use-api:0}")
    private String useApi;

    @Value("${llm.provider:qwen}")
    private String llmProvider;

    public WorkflowService(
            MockWorkflowService mockEngine,
            LangGraphWorkflowEngine langGraphEngine,
            SdlcGraphMapper mapper,
            AgentToolsService agentToolsService
    ) {
        this.mockEngine = mockEngine;
        this.langGraphEngine = langGraphEngine;
        this.mapper = mapper;
        this.agentToolsService = agentToolsService;
    }

    public boolean isMockMode() {
        return !"1".equals(String.valueOf(useApi).trim());
    }

    public boolean requestUsesMock(WorkflowStartRequest request) {
        if (request != null && request.getUseApi() != null) {
            return !request.getUseApi();
        }
        return isMockMode();
    }

    private boolean runUsesMock(String workflowId) {
        return mockRuns.getOrDefault(workflowId, isMockMode());
    }

    private WorkflowEngine engineForStart(WorkflowStartRequest request) {
        return requestUsesMock(request) ? mockEngine : langGraphEngine;
    }

    private WorkflowEngine engineFor(String workflowId) {
        if (runUsesMock(workflowId) || mockEngine.knows(workflowId)) {
            return mockEngine;
        }
        return langGraphEngine;
    }

    private void trackRun(String workflowId, WorkflowEngine engine) {
        mockRuns.put(workflowId, engine == mockEngine);
    }

    public String useApiFlag() {
        return isMockMode() ? "0" : "1";
    }

    public String llmProvider() {
        return llmProvider == null || llmProvider.isBlank() ? "qwen" : llmProvider.trim();
    }

    public WorkflowRunResponse start(WorkflowStartRequest request) {
        return start(request, hitlDefault);
    }

    public WorkflowRunResponse start(WorkflowStartRequest request, boolean awaitHuman) {
        WorkflowEngine engine = engineForStart(request);
        WorkflowRunResponse response = engine.start(request, awaitHuman);
        trackRun(response.getWorkflowId(), engine);
        return response;
    }

    /** Synchronous run — used for blocking API and agent pipeline. */
    public WorkflowRunResponse startBlocking(WorkflowStartRequest request) {
        return startBlocking(request, hitlDefault);
    }

    public WorkflowRunResponse startBlocking(WorkflowStartRequest request, boolean awaitHuman) {
        WorkflowEngine engine = engineForStart(request);
        WorkflowRunResponse response = engine.startSync(request, awaitHuman);
        trackRun(response.getWorkflowId(), engine);
        return response;
    }

    public SdlcState startState(WorkflowStartRequest request, boolean awaitHuman) {
        WorkflowRunResponse response = startBlocking(request, awaitHuman);
        if (engineFor(response.getWorkflowId()) == mockEngine) {
            return fromMockResponse(response, request);
        }
        SdlcState state = langGraphEngine.getState(response.getWorkflowId());
        if (state != null) {
            return state;
        }
        throw new IllegalStateException("Workflow state missing after sync start: " + response.getWorkflowId());
    }

    public WorkflowRunResponse get(String workflowId) {
        return engineFor(workflowId).get(workflowId);
    }

    public WorkflowRunResponse decide(String workflowId, WorkflowDecisionRequest request) {
        return engineFor(workflowId).decide(workflowId, request);
    }

    public WorkflowRunResponse cancel(String workflowId) {
        return engineFor(workflowId).cancel(workflowId);
    }

    public AgentRun runPipeline(String task, String engineerPersonaPath, String reviewerPersonaPath, boolean includeReviewer) {
        if (task == null || task.isBlank()) {
            return AgentRun.error("Task is required");
        }
        try {
            WorkflowStartRequest request = new WorkflowStartRequest();
            request.setTask(task);
            request.setEngineerPersonaPath(engineerPersonaPath);
            request.setReviewerPersonaPath(reviewerPersonaPath);
            request.setIncludeReviewer(includeReviewer);
            WorkflowRunResponse response = startBlocking(request, false);
            return mapper.toAgentRun(response);
        } catch (Exception e) {
            return AgentRun.error(e.getMessage());
        }
    }

    public FileResponse readDraftCompare(String workflowId, String filePath) {
        return engineFor(workflowId).readDraftCompare(workflowId, filePath);
    }

    public List<DraftFileEntryDTO> draftManifest(String workflowId) {
        return engineFor(workflowId).draftManifest(workflowId);
    }

    public List<String> listSandboxFolders() {
        return agentToolsService.listSandboxFolders();
    }

    private static SdlcState fromMockResponse(WorkflowRunResponse response, WorkflowStartRequest request) {
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put(SdlcState.WORKFLOW_ID, response.getWorkflowId());
        data.put(SdlcState.TASK, request.getTask());
        data.put(SdlcState.STATUS, response.getStatus());
        data.put(SdlcState.PHASE, response.getCurrentPhase());
        data.put(SdlcState.SUMMARY, response.getSummary());
        data.put(SdlcState.REQUIREMENTS, response.getRequirementsDoc());
        data.put(SdlcState.ARCHITECTURE, response.getArchitectureDoc());
        data.put(SdlcState.TEST_REPORT, response.getTestReport());
        data.put(SdlcState.REVIEW_VERDICT, response.getReviewVerdict());
        data.put(SdlcState.REVIEW_NOTES, response.getReviewNotes());
        data.put(SdlcState.TESTS_PASSED, response.isTestsPassed());
        data.put(SdlcState.FILES_WRITTEN, response.getFilesWritten());
        data.put(SdlcState.ENGINEER_SUMMARY, response.getSummary());
        data.put(SdlcState.SANDBOX_PATH, response.getSandboxPath());
        return new SdlcState(data);
    }
}
