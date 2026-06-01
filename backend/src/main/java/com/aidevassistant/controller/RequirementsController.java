package com.aidevassistant.controller;

import com.aidevassistant.dto.*;
import com.aidevassistant.service.LLMService;
import com.aidevassistant.service.PromptService;
import com.aidevassistant.service.RequirementsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/requirements")
public class RequirementsController {
    
    private final LLMService llmService;
    private final PromptService promptService;
    private final RequirementsService requirementsService;
    
    @Value("${requirements.root.path}")
    private String requirementsRoot;
    
    public RequirementsController(LLMService llmService, PromptService promptService, RequirementsService requirementsService) {
        this.llmService = llmService;
        this.promptService = promptService;
        this.requirementsService = requirementsService;
    }
    
    @PostMapping("/generate")
    public ResponseEntity<Map<String, String>> generateRequirements(@RequestBody RequirementsRequest request) {
        try {
            String prompt = promptService.getRequirementsPrompt(
                    request.getProductName(),
                    request.getRequirementType()
            );
            
            String content = llmService.chatWithSystemPrompt(
                    promptService.getQuickChatSystemPrompt(),
                    prompt
            );
            
            Map<String, String> response = new HashMap<>();
            response.put("content", content);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    @PostMapping("/save")
    public ResponseEntity<Map<String, String>> saveRequirements(@RequestBody RequirementsSaveRequest request) {
        try {
            boolean success = requirementsService.saveRequirementsDocument(
                    request.getContent(),
                    request.getProductName(),
                    request.getRequirementType()
            );
            
            Map<String, String> response = new HashMap<>();
            if (success) {
                response.put("message", "Requirements saved successfully");
                return ResponseEntity.ok(response);
            } else {
                response.put("error", "Failed to save requirements");
                return ResponseEntity.internalServerError().body(response);
            }
            
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    @GetMapping("/files")
    public ResponseEntity<Map<String, Object>> listRequirements() {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("files", requirementsService.listRequirementsFiles());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(null);
        }
    }
    
    @PostMapping("/load")
    public ResponseEntity<Map<String, String>> loadRequirement(@RequestBody Map<String, String> request) {
        try {
            String fileName = request.get("fileName");
            Path filePath = Paths.get(requirementsRoot, fileName);
            
            if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "File not found");
                return ResponseEntity.badRequest().body(error);
            }
            
            String content = Files.readString(filePath);
            
            Map<String, String> response = new HashMap<>();
            response.put("content", content);
            response.put("fileName", fileName);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    @DeleteMapping("/delete")
    public ResponseEntity<Map<String, String>> deleteRequirement(@RequestBody Map<String, String> request) {
        try {
            String fileName = request.get("fileName");
            Path filePath = Paths.get(requirementsRoot, fileName);
            
            if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "File not found");
                return ResponseEntity.badRequest().body(error);
            }
            
            Files.delete(filePath);
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "File deleted successfully");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
}
