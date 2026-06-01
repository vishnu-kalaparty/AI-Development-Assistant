package com.aidevassistant.controller;

import com.aidevassistant.dto.DraftFileEntryDTO;
import com.aidevassistant.dto.FileRequest;
import com.aidevassistant.dto.FileResponse;
import com.aidevassistant.dto.WorkflowDecisionRequest;
import com.aidevassistant.dto.WorkflowRunResponse;
import com.aidevassistant.dto.WorkflowStartRequest;
import com.aidevassistant.service.AgentToolsService;
import com.aidevassistant.service.CodeIndexService;
import com.aidevassistant.service.WorkflowService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/workflow")
public class WorkflowController {

    private final WorkflowService workflowService;
    private final CodeIndexService codeIndexService;
    private final AgentToolsService agentToolsService;

    public WorkflowController(
            WorkflowService workflowService,
            CodeIndexService codeIndexService,
            AgentToolsService agentToolsService
    ) {
        this.workflowService = workflowService;
        this.codeIndexService = codeIndexService;
        this.agentToolsService = agentToolsService;
    }

    @GetMapping("/mode")
    public Map<String, Object> mode() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("useApi", workflowService.useApiFlag());
        body.put("mock", workflowService.isMockMode());
        body.put("llmProvider", workflowService.llmProvider());
        body.put("neo4jConfigured", codeIndexService.isConfigured());
        body.put("neo4jReady", codeIndexService.isReady());
        return body;
    }

    @GetMapping("/sandbox-folders")
    public Map<String, List<String>> sandboxFolders() {
        Map<String, List<String>> body = new LinkedHashMap<>();
        body.put("folders", workflowService.listSandboxFolders());
        return body;
    }

    @PostMapping("/reindex")
    public ResponseEntity<Map<String, Object>> reindex(@RequestBody Map<String, String> request) {
        String sandboxPath = request == null ? null : request.get("sandboxPath");
        if (sandboxPath == null || sandboxPath.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "sandboxPath is required"));
        }
        return ResponseEntity.ok(codeIndexService.rebuildProject(sandboxPath, agentToolsService));
    }

    @PostMapping("/restore-from-index")
    public ResponseEntity<Map<String, Object>> restoreFromIndex(@RequestBody Map<String, String> request) {
        String sandboxPath = request == null ? null : request.get("sandboxPath");
        if (sandboxPath == null || sandboxPath.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "sandboxPath is required"));
        }
        return ResponseEntity.ok(codeIndexService.restoreProjectFromIndex(sandboxPath, agentToolsService));
    }

    @PostMapping("/run")
    public ResponseEntity<WorkflowRunResponse> run(@RequestBody WorkflowStartRequest request) {
        try {
            if (request != null && Boolean.TRUE.equals(request.getBlocking())) {
                return ResponseEntity.ok(workflowService.startBlocking(request, false));
            }
            return ResponseEntity.ok(workflowService.start(request));
        } catch (IllegalArgumentException e) {
            WorkflowRunResponse error = new WorkflowRunResponse();
            error.setStatus("error");
            error.setSummary(e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            WorkflowRunResponse error = new WorkflowRunResponse();
            error.setStatus("error");
            error.setSummary("Error: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkflowRunResponse> status(@PathVariable("id") String id) {
        try {
            return ResponseEntity.ok(workflowService.get(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/decision")
    public ResponseEntity<WorkflowRunResponse> decide(
            @PathVariable("id") String id,
            @RequestBody WorkflowDecisionRequest request
    ) {
        try {
            return ResponseEntity.ok(workflowService.decide(id, request));
        } catch (IllegalArgumentException e) {
            WorkflowRunResponse error = new WorkflowRunResponse();
            error.setStatus("error");
            error.setSummary(e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            WorkflowRunResponse error = new WorkflowRunResponse();
            error.setStatus("error");
            error.setSummary("Error: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<WorkflowRunResponse> cancel(@PathVariable("id") String id) {
        try {
            return ResponseEntity.ok(workflowService.cancel(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            WorkflowRunResponse error = new WorkflowRunResponse();
            error.setStatus("error");
            error.setSummary("Error: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @PostMapping("/{id}/read-draft")
    public ResponseEntity<FileResponse> readDraft(
            @PathVariable("id") String id,
            @RequestBody FileRequest request
    ) {
        try {
            String path = request == null ? null : request.getFilePath();
            FileResponse file = workflowService.readDraftCompare(id, path);
            if (file == null || file.getContent() == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(file);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(new FileResponse(null, "Error: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}/draft-manifest")
    public ResponseEntity<Map<String, List<DraftFileEntryDTO>>> draftManifest(@PathVariable("id") String id) {
        try {
            Map<String, List<DraftFileEntryDTO>> body = new LinkedHashMap<>();
            body.put("files", workflowService.draftManifest(id));
            return ResponseEntity.ok(body);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
