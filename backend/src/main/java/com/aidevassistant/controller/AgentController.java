package com.aidevassistant.controller;

import com.aidevassistant.dto.*;
import com.aidevassistant.service.AgentToolsService;
import com.aidevassistant.service.ExecutionService;
import com.aidevassistant.service.LLMService;
import com.aidevassistant.service.PersonaService;
import com.aidevassistant.service.PromptService;
import com.aidevassistant.service.WorkflowService;
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
    private final WorkflowService workflowService;
    private final ExecutionService executionService;

    public AgentController(
            LLMService llmService,
            PromptService promptService,
            AgentToolsService agentToolsService,
            PersonaService personaService,
            WorkflowService workflowService,
            ExecutionService executionService
    ) {
        this.llmService = llmService;
        this.promptService = promptService;
        this.agentToolsService = agentToolsService;
        this.personaService = personaService;
        this.workflowService = workflowService;
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
    public ResponseEntity<WorkflowRunResponse> runAgents(@RequestBody WorkflowStartRequest request) {
        try {
            if (request == null) {
                request = new WorkflowStartRequest();
            }
            WorkflowRunResponse response = workflowService.startBlocking(request, false);
            if ("error".equals(response.getStatus())) {
                return ResponseEntity.internalServerError().body(response);
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            WorkflowRunResponse error = new WorkflowRunResponse();
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
}
