package com.aidevassistant.controller;

import com.aidevassistant.dto.ChatResponse;
import com.aidevassistant.dto.CodeAssistRequest;
import com.aidevassistant.service.AgentToolsService;
import com.aidevassistant.service.LLMService;
import com.aidevassistant.service.PromptService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/code")
public class CodeController {
    
    private final LLMService llmService;
    private final PromptService promptService;
    private final AgentToolsService agentToolsService;
    
    public CodeController(LLMService llmService, PromptService promptService, AgentToolsService agentToolsService) {
        this.llmService = llmService;
        this.promptService = promptService;
        this.agentToolsService = agentToolsService;
    }
    
    @PostMapping("/assist")
    public ResponseEntity<ChatResponse> assistCode(@RequestBody CodeAssistRequest request) {
        try {
            String prompt;
            
            switch (request.getAction()) {
                case "review":
                    prompt = promptService.getCodeReviewPrompt(request.getCode(), request.getLanguage());
                    break;
                case "debug":
                    prompt = promptService.getCodeDebugPrompt(request.getCode(), request.getLanguage());
                    break;
                case "modify":
                    prompt = promptService.getCodeModifyPrompt(
                            request.getCode(),
                            request.getLanguage(),
                            request.getInstruction()
                    );
                    break;
                default:
                    return ResponseEntity.badRequest()
                            .body(new ChatResponse("Invalid action: " + request.getAction()));
            }
            
            String response = llmService.chatWithSystemPrompt(
                    promptService.getQuickChatSystemPrompt(),
                    prompt
            );
            
            return ResponseEntity.ok(new ChatResponse(response));
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(new ChatResponse("Error: " + e.getMessage()));
        }
    }
    
    @PostMapping("/save")
    public ResponseEntity<ChatResponse> saveCode(@RequestBody CodeAssistRequest request) {
        try {
            String fileName = request.getInstruction(); // Using instruction field as fileName
            if (fileName == null || fileName.trim().isEmpty()) {
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                String fileExtension = getFileExtension(request.getLanguage());
                fileName = "code_" + timestamp + fileExtension;
            }
            
            String result = agentToolsService.writeFile(fileName, request.getCode());
            return ResponseEntity.ok(new ChatResponse(result));
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(new ChatResponse("Error saving file: " + e.getMessage()));
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
    
    @PostMapping("/load")
    public ResponseEntity<Map<String, String>> loadFile(@RequestBody Map<String, String> request) {
        try {
            String fileName = request.get("fileName");
            String content = agentToolsService.readFile(fileName);
            
            Map<String, String> response = new HashMap<>();
            response.put("content", content);
            response.put("fileName", fileName);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(null);
        }
    }
    
    @DeleteMapping("/delete")
    public ResponseEntity<ChatResponse> deleteFile(@RequestBody Map<String, String> request) {
        try {
            String fileName = request.get("fileName");
            agentToolsService.deleteFile(fileName);
            return ResponseEntity.ok(new ChatResponse("File deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(new ChatResponse("Error deleting file: " + e.getMessage()));
        }
    }
    
    private java.util.List<String> parseFileList(String result) {
        java.util.List<String> files = new java.util.ArrayList<>();
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
    
    private String getFileExtension(String language) {
        switch (language.toLowerCase()) {
            case "python":
                return ".py";
            case "javascript":
                return ".js";
            case "typescript":
                return ".ts";
            case "java":
                return ".java";
            case "cpp":
                return ".cpp";
            case "go":
                return ".go";
            default:
                return ".txt";
        }
    }
}
