package com.aidevassistant.controller;

import com.aidevassistant.dto.*;
import com.aidevassistant.service.LLMService;
import com.aidevassistant.service.PromptService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    
    private final LLMService llmService;
    private final PromptService promptService;
    
    public ChatController(LLMService llmService, PromptService promptService) {
        this.llmService = llmService;
        this.promptService = promptService;
    }
    
    @PostMapping("/quick")
    public ResponseEntity<ChatResponse> quickChat(@RequestBody ChatRequest request) {
        try {
            // Add system prompt if not present
            List<MessageDTO> messages = new ArrayList<>(request.getMessages());
            if (messages.isEmpty() || !messages.get(0).getRole().equals("system")) {
                messages.add(0, new MessageDTO("system", promptService.getQuickChatSystemPrompt()));
            }
            
            String response = llmService.chat(messages);
            return ResponseEntity.ok(new ChatResponse(response));
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(new ChatResponse("Error: " + e.getMessage()));
        }
    }
    
    @PostMapping("/learning")
    public ResponseEntity<ChatResponse> learningTopic(@RequestBody LearningRequest request) {
        try {
            String prompt = promptService.getLearningTopicPrompt(request.getTopic(), request.getLevel());
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
}
