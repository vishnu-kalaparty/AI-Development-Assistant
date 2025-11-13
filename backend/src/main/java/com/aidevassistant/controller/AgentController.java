package com.aidevassistant.controller;

import com.aidevassistant.dto.*;
import com.aidevassistant.service.AgentToolsService;
import com.aidevassistant.service.LLMService;
import com.aidevassistant.service.PromptService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    
    public AgentController(LLMService llmService, PromptService promptService, AgentToolsService agentToolsService) {
        this.llmService = llmService;
        this.promptService = promptService;
        this.agentToolsService = agentToolsService;
    }
    
    @Value("${personas.root.path}")
    private String personasRoot;
    
    @PostMapping("/chat")
    public ResponseEntity<AgentChatResponse> agentChat(@RequestBody AgentChatRequest request) {
        try {
            List<MessageDTO> messages = new ArrayList<>();
            
            // Add base system message
            messages.add(new MessageDTO("system", promptService.getAgentSystemPrompt()));
            
            // Add persona if provided
            if (request.getPersonaPath() != null && !request.getPersonaPath().isEmpty()) {
                String personaContent = loadPersona(request.getPersonaPath());
                if (personaContent != null) {
                    messages.add(new MessageDTO("system", personaContent));
                }
            }
            
            // Add active file context if editor has content
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
            
            // Add conversation history
            messages.addAll(request.getMessages());
            
            // Get AI response
            String response = llmService.chat(messages);
            
            // Add assistant response to messages
            messages.add(new MessageDTO("assistant", response));
            
            // Return updated conversation
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
            System.out.println("Load persona request received for: " + personaPath);
            
            if (personaPath == null || personaPath.isEmpty()) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Persona path is required");
                return ResponseEntity.badRequest().body(error);
            }
            
            String content = loadPersona(personaPath);
            
            Map<String, String> response = new HashMap<>();
            if (content != null && !content.isEmpty()) {
                response.put("content", content);
                response.put("message", "Persona loaded successfully");
                response.put("path", personaPath);
                return ResponseEntity.ok(response);
            } else {
                response.put("error", "Failed to load persona from path: " + personaPath);
                response.put("personasRoot", personasRoot);
                return ResponseEntity.badRequest().body(response);
            }
            
        } catch (Exception e) {
            System.err.println("Exception in loadPersona endpoint: " + e.getMessage());
            e.printStackTrace();
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
            
            System.out.println("File written to sandbox: " + request.getFilePath());
            
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
    
    private String loadPersona(String personaPath) {
        try {
            if (personaPath == null || personaPath.isEmpty()) {
                System.err.println("Persona path is null or empty");
                return null;
            }
            
            Path path = Paths.get(personasRoot, personaPath);
            System.out.println("Attempting to load persona from: " + path.toAbsolutePath());
            
            if (!Files.exists(path)) {
                System.err.println("Persona file does not exist: " + path.toAbsolutePath());
                return null;
            }
            
            if (!Files.isReadable(path)) {
                System.err.println("Persona file is not readable: " + path.toAbsolutePath());
                return null;
            }
            
            String content = Files.readString(path);
            System.out.println("Successfully loaded persona: " + personaPath + " (" + content.length() + " chars)");
            return content;
        } catch (Exception e) {
            System.err.println("Error loading persona: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}
