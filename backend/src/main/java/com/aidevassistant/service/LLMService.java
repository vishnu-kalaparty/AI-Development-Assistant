package com.aidevassistant.service;

import com.aidevassistant.config.GoogleAIConfig;
import com.aidevassistant.dto.MessageDTO;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class LLMService {
    
    private static final Logger log = LoggerFactory.getLogger(LLMService.class);
    
    private final Client client;
    private final String model;
    private final GenerateContentConfig config;
    
    public LLMService(GoogleAIConfig googleConfig) {
        this.client = Client.builder()
                .apiKey(googleConfig.getKey())
                .build();
        this.model = googleConfig.getModel();
        
        this.config = GenerateContentConfig.builder()
                .temperature(0.7f)
                .maxOutputTokens(18192)
                .build();
    }
    
    public String chat(List<MessageDTO> messages) {
        try {
            // Build content with system instruction and messages
            Content systemInstruction = null;
            List<Content> contents = new ArrayList<>();
            
            for (MessageDTO msg : messages) {
                if (msg.getRole().equalsIgnoreCase("system")) {
                    // Set as system instruction
                    systemInstruction = Content.fromParts(Part.fromText(msg.getContent()));
                } else if (msg.getRole().equalsIgnoreCase("user")) {
                    contents.add(Content.fromParts(Part.fromText(msg.getContent())));
                } else if (msg.getRole().equalsIgnoreCase("assistant")) {
                    // Note: Gemini API uses "model" role for assistant responses
                    // For multi-turn conversations, we might need to handle this differently
                    contents.add(Content.fromParts(Part.fromText(msg.getContent())));
                }
            }
            
            // Create config with system instruction if present
            GenerateContentConfig finalConfig = config;
            if (systemInstruction != null) {
                finalConfig = GenerateContentConfig.builder()
                        .temperature(0.7f)
                        .maxOutputTokens(18192)
                        .systemInstruction(systemInstruction)
                        .build();
            }
            
            // Generate content
            GenerateContentResponse response;
            if (contents.size() == 1) {
                // Single message
                response = client.models.generateContent(model, contents.get(0), finalConfig);
            } else {
                // Multi-turn conversation - combine into single prompt for now
                StringBuilder combined = new StringBuilder();
                for (Content content : contents) {
                    combined.append(content.toString()).append("\n");
                }
                response = client.models.generateContent(model, combined.toString(), finalConfig);
            }
            
            return response.text();
            
        } catch (Exception e) {
            log.error("Error calling Google AI API", e);
            throw new RuntimeException("Failed to get response from AI: " + e.getMessage());
        }
    }
    
    public String chatWithSystemPrompt(String systemPrompt, String userMessage) {
        List<MessageDTO> messages = List.of(
                new MessageDTO("system", systemPrompt),
                new MessageDTO("user", userMessage)
        );
        return chat(messages);
    }
}
