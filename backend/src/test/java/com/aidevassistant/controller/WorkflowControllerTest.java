package com.aidevassistant.controller;

import com.aidevassistant.service.AgentToolsService;
import com.aidevassistant.service.CodeIndexService;
import com.aidevassistant.dto.WorkflowDecisionRequest;
import com.aidevassistant.dto.WorkflowRunResponse;
import com.aidevassistant.dto.WorkflowStartRequest;
import com.aidevassistant.graph.SdlcGraphBuilder;
import com.aidevassistant.graph.SdlcGraphMapper;
import com.aidevassistant.service.MockWorkflowService;
import com.aidevassistant.service.PersonaService;
import com.aidevassistant.service.WorkflowRunTracker;
import com.aidevassistant.service.WorkflowSnapshotStore;
import com.aidevassistant.service.WorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WorkflowControllerTest {

    private MockMvc mockMvc;
    private StubWorkflowService workflowService;

    @BeforeEach
    void setUp() {
        workflowService = new StubWorkflowService();
        mockMvc = MockMvcBuilders.standaloneSetup(
                new WorkflowController(workflowService, new StubCodeIndexService(), null)
        ).build();
    }

    @Test
    void startReturnsWorkflow() throws Exception {
        mockMvc.perform(post("/api/workflow/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"task\":\"Write fizzbuzz.py\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowId").value("wf-1"))
                .andExpect(jsonPath("$.status").value("awaiting_human"));
    }

    @Test
    void missingTaskIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/workflow/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void decisionResumesWorkflow() throws Exception {
        mockMvc.perform(post("/api/workflow/wf-1/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"));
    }

    @Test
    void unknownWorkflowIsNotFound() throws Exception {
        mockMvc.perform(get("/api/workflow/missing")).andExpect(status().isNotFound());
    }

    @Test
    void modeReportsMockByDefault() throws Exception {
        mockMvc.perform(get("/api/workflow/mode"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.useApi").value("0"))
                .andExpect(jsonPath("$.mock").value(true))
                .andExpect(jsonPath("$.neo4jConfigured").value(true))
                .andExpect(jsonPath("$.neo4jReady").value(false));
    }

    @Test
    void sandboxFoldersReturnsList() throws Exception {
        mockMvc.perform(get("/api/workflow/sandbox-folders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.folders").isArray());
    }

    private static final class StubCodeIndexService extends CodeIndexService {
        private StubCodeIndexService() {
            super(null, true, 768, null);
        }

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public boolean isReady() {
            return false;
        }
    }

    private static final class StubWorkflowService extends WorkflowService {
        private StubWorkflowService() {
            super(new MockWorkflowService(null, null), null, null, null);
        }

        @Override
        public boolean isMockMode() {
            return true;
        }

        @Override
        public String useApiFlag() {
            return "0";
        }

        @Override
        public String llmProvider() {
            return "ollama";
        }

        @Override
        public WorkflowRunResponse start(WorkflowStartRequest request) {
            if (request == null || request.getTask() == null || request.getTask().isBlank()) {
                throw new IllegalArgumentException("Task is required");
            }
            WorkflowRunResponse response = new WorkflowRunResponse();
            response.setWorkflowId("wf-1");
            response.setStatus("awaiting_human");
            response.setAwaitingHuman(true);
            return response;
        }

        @Override
        public WorkflowRunResponse get(String workflowId) {
            if (!"wf-1".equals(workflowId)) {
                throw new IllegalArgumentException("Unknown workflow: " + workflowId);
            }
            WorkflowRunResponse response = new WorkflowRunResponse();
            response.setWorkflowId(workflowId);
            response.setStatus("awaiting_human");
            return response;
        }

        @Override
        public WorkflowRunResponse decide(String workflowId, WorkflowDecisionRequest request) {
            WorkflowRunResponse response = new WorkflowRunResponse();
            response.setWorkflowId(workflowId);
            response.setStatus("completed");
            return response;
        }

        @Override
        public List<String> listSandboxFolders() {
            return List.of("my-app", "runs/demo-id");
        }

        @Override
        public WorkflowRunResponse cancel(String workflowId) {
            WorkflowRunResponse response = new WorkflowRunResponse();
            response.setWorkflowId(workflowId);
            response.setStatus("cancelled");
            response.setSummary("Stopped by user");
            return response;
        }
    }
}
