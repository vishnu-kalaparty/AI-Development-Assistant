package com.aidevassistant.controller;

import com.aidevassistant.dto.FileRequest;
import com.aidevassistant.dto.FileResponse;
import com.aidevassistant.service.AgentToolsService;
import com.aidevassistant.tools.SandboxContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/sandbox")
public class SandboxController {

    private final AgentToolsService agentToolsService;

    public SandboxController(AgentToolsService agentToolsService) {
        this.agentToolsService = agentToolsService;
    }

    @GetMapping("/files")
    public ResponseEntity<Map<String, Object>> listFiles(
            @RequestParam(value = "prefix", required = false) String prefix
    ) {
        try {
            Map<String, Object> response = new HashMap<>();
            String scoped = prefix == null ? "" : prefix.trim();
            if (scoped.isEmpty()) {
                response.put("files", agentToolsService.listFilePaths(true));
                response.put("tree", agentToolsService.listTree());
            } else {
                try (SandboxContext.Scope ignored = SandboxContext.scope(scoped)) {
                    response.put("files", agentToolsService.listFilePaths(true));
                    response.put("tree", agentToolsService.listTree());
                }
                response.put("prefix", scoped);
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(null);
        }
    }

    @PostMapping("/read")
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

    @PostMapping("/write")
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

    @DeleteMapping("/delete")
    public ResponseEntity<Map<String, String>> deleteFile(@RequestBody Map<String, String> request) {
        try {
            String filePath = request.get("filePath");
            if (filePath == null || filePath.isBlank()) {
                filePath = request.get("fileName");
            }
            agentToolsService.deleteFile(filePath);
            Map<String, String> response = new HashMap<>();
            response.put("message", "File deleted successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Error deleting file: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
}
