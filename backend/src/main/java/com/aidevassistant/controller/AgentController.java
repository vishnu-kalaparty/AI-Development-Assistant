package com.aidevassistant.controller;

import com.aidevassistant.agent.AgentRun;
import com.aidevassistant.agent.AgentStep;
import com.aidevassistant.dto.*;
import com.aidevassistant.service.AgentToolsService;
import com.aidevassistant.service.ExecutionService;
import com.aidevassistant.service.LLMService;
import com.aidevassistant.service.Orchestrator;
import com.aidevassistant.service.PersonaService;
import com.aidevassistant.service.PromptService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agents")
public class AgentController {

    private final LLMService llmService;
    private final PromptService promptService;
    private final AgentToolsService agentToolsService;
    private final PersonaService personaService;
    private final Orchestrator orchestrator;
    private final ExecutionService executionService;

    public AgentController(
            LLMService llmService,
            PromptService promptService,
            AgentToolsService agentToolsService,
            PersonaService personaService,
            Orchestrator orchestrator,
            ExecutionService executionService
    ) {
        this.llmService = llmService;
        this.promptService = promptService;
        this.agentToolsService = agentToolsService;
        this.personaService = personaService;
        this.orchestrator = orchestrator;
        this.executionService = executionService;
    }

    @GetMapping("/runtime")
    public ResponseEntity<Map<String, String>> runtime() {
        Map<String, String> body = new HashMap<>();
        body.put("interpreters", executionService.describeRuntime());
        body.put("sandbox", agentToolsService.getSandboxRootPath().toString());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/run")
    public ResponseEntity<AgentRunResponse> runAgents(@RequestBody AgentRunRequest request) {
        try {
            AgentRun run = orchestrator.runPipeline(
                    request.getTask(),
                    request.getEngineerPersonaPath(),
                    request.getReviewerPersonaPath(),
                    request.isIncludeReviewer()
            );

            AgentRunResponse response = new AgentRunResponse();
            response.setStatus(run.getStatus());
            response.setSummary(run.getSummary());
            response.setFilesWritten(run.getFilesWritten());
            response.setSandboxFiles(parseFileList(agentToolsService.listFiles("", false)));

            List<AgentStepDTO> steps = new ArrayList<>();
            for (AgentStep step : run.getSteps()) {
                steps.add(new AgentStepDTO(
                        step.getStage(),
                        step.getStep(),
                        step.getThought(),
                        step.getToolName(),
                        step.getToolArgs(),
                        step.getObservation()
                ));
            }
            response.setSteps(steps);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            AgentRunResponse error = new AgentRunResponse();
            error.setStatus("error");
            error.setSummary("Error: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @PostMapping("/chat")
    public ResponseEntity<AgentChatResponse> agentChat(@RequestBody AgentChatRequest request) {
        try {
            List<MessageDTO> messages = new ArrayList<>();

            messages.add(new MessageDTO("system", promptService.getAgentSystemPrompt()));

            if (request.getPersonaPath() != null && !request.getPersonaPath().isEmpty()) {
                String personaContent = personaService.load(request.getPersonaPath());
                if (personaContent != null) {
                    messages.add(new MessageDTO("system", personaContent));
                }
            }

            if (request.getEditorContent() != null && !request.getEditorContent().isEmpty()) {
                StringBuilder contextMsg = new StringBuilder();
                contextMsg.append("Current file in sandbox editor");
                if (request.getEditorPath() != null && !request.getEditorPath().isEmpty()) {
                    contextMsg.append(" (").append(request.getEditorPath()).append(")");
                }
                contextMsg.append(":\n\n```\n");
                contextMsg.append(request.getEditorContent());
                contextMsg.append("\n```");

                messages.add(new MessageDTO("system", contextMsg.toString()));
            }

            messages.addAll(request.getMessages());

            String response = llmService.chat(messages);

            messages.add(new MessageDTO("assistant", response));

            AgentChatResponse chatResponse = new AgentChatResponse();
            chatResponse.setMessages(messages);
            chatResponse.setEditorContent(request.getEditorContent());
            chatResponse.setEditorPath(request.getEditorPath());

            return ResponseEntity.ok(chatResponse);

        } catch (Exception e) {
            List<MessageDTO> errorMessages = new ArrayList<>(request.getMessages());
            errorMessages.add(new MessageDTO("assistant", "Error: " + e.getMessage()));

            AgentChatResponse errorResponse = new AgentChatResponse();
            errorResponse.setMessages(errorMessages);
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    @PostMapping("/load-persona")
    public ResponseEntity<Map<String, String>> loadPersona(@RequestBody Map<String, String> request) {
        try {
            String personaPath = request.get("personaPath");

            if (personaPath == null || personaPath.isEmpty()) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Persona path is required");
                return ResponseEntity.badRequest().body(error);
            }

            String content = personaService.load(personaPath);

            Map<String, String> response = new HashMap<>();
            if (content != null && !content.isEmpty()) {
                response.put("content", content);
                response.put("message", "Persona loaded successfully");
                response.put("path", personaPath);
                return ResponseEntity.ok(response);
            } else {
                response.put("error", "Failed to load persona from path: " + personaPath);
                return ResponseEntity.badRequest().body(response);
            }

        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Exception: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @PostMapping("/read-file")
    public ResponseEntity<FileResponse> readFile(@RequestBody FileRequest request) {
        try {
            String content = agentToolsService.readFile(request.getFilePath());

            if (content.startsWith("Error:") || content.startsWith("Security Error:")) {
                return ResponseEntity.badRequest().body(new FileResponse(null, content));
            }

            return ResponseEntity.ok(new FileResponse(content, "File read successfully"));

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(new FileResponse(null, "Error: " + e.getMessage()));
        }
    }

    @PostMapping("/write-file")
    public ResponseEntity<FileResponse> writeFile(@RequestBody FileRequest request) {
        try {
            String message = agentToolsService.writeFile(request.getFilePath(), request.getContent());

            if (message.startsWith("Error:") || message.startsWith("Security Error:")) {
                return ResponseEntity.badRequest().body(new FileResponse(null, message));
            }

            return ResponseEntity.ok(new FileResponse(null, message));

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(new FileResponse(null, "Error: " + e.getMessage()));
        }
    }

    @PostMapping("/save-editor-content")
    public ResponseEntity<Map<String, String>> saveEditorContent(@RequestBody Map<String, String> request) {
        try {
            String content = request.get("content");
            String path = request.get("path");

            if (content == null || path == null || path.isEmpty()) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Content and path are required");
                return ResponseEntity.badRequest().body(error);
            }

            String message = agentToolsService.writeFile(path, content);

            Map<String, String> response = new HashMap<>();
            if (message.startsWith("Error:") || message.startsWith("Security Error:")) {
                response.put("error", message);
                return ResponseEntity.badRequest().body(response);
            }

            response.put("message", message);
            response.put("path", path);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Exception: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @GetMapping("/files")
    public ResponseEntity<Map<String, Object>> listFiles() {
        try {
            String result = agentToolsService.listFiles("", false);
            Map<String, Object> response = new HashMap<>();
            response.put("files", parseFileList(result));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(null);
        }
    }

    @DeleteMapping("/delete-file")
    public ResponseEntity<Map<String, String>> deleteFile(@RequestBody Map<String, String> request) {
        try {
            String fileName = request.get("fileName");
            agentToolsService.deleteFile(fileName);

            Map<String, String> response = new HashMap<>();
            response.put("message", "File deleted successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Error deleting file: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    private List<String> parseFileList(String result) {
        List<String> files = new ArrayList<>();
        String[] lines = result.split("\n");
        for (String line : lines) {
            if (line.trim().startsWith("- ")) {
                String fileName = line.trim().substring(2).trim();
                if (!fileName.endsWith("/")) {
                    files.add(fileName);
                }
            }
        }
        return files;
    }
}
