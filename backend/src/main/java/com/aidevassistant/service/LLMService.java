package com.aidevassistant.service;

import com.aidevassistant.config.GoogleAIConfig;
import com.aidevassistant.dto.MessageDTO;
import com.google.genai.Client;
import com.google.genai.types.AutomaticFunctionCallingConfig;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.google.genai.types.Tool;
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

    public LLMService(GoogleAIConfig googleConfig) {
        this.client = Client.builder()
                .apiKey(googleConfig.getKey())
                .build();
        this.model = googleConfig.getModel();
    }

    public String chat(List<MessageDTO> messages) {
        try {
            Content systemInstruction = null;
            List<Content> contents = new ArrayList<>();

            for (MessageDTO msg : messages) {
                if (msg.getRole().equalsIgnoreCase("system")) {
                    systemInstruction = Content.fromParts(Part.fromText(msg.getContent()));
                } else if (msg.getRole().equalsIgnoreCase("user")) {
                    contents.add(Content.builder()
                            .role("user")
                            .parts(List.of(Part.fromText(msg.getContent())))
                            .build());
                } else if (msg.getRole().equalsIgnoreCase("assistant") || msg.getRole().equalsIgnoreCase("model")) {
                    contents.add(Content.builder()
                            .role("model")
                            .parts(List.of(Part.fromText(msg.getContent())))
                            .build());
                }
            }

            if (contents.isEmpty()) {
                contents.add(Content.builder()
                        .role("user")
                        .parts(List.of(Part.fromText("Hello")))
                        .build());
            }

            GenerateContentConfig.Builder configBuilder = GenerateContentConfig.builder()
                    .temperature(0.7f)
                    .maxOutputTokens(8192);
            if (systemInstruction != null) {
                configBuilder.systemInstruction(systemInstruction);
            }

            GenerateContentResponse response = client.models.generateContent(model, contents, configBuilder.build());
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

    public GenerateContentResponse generate(List<Content> history, String systemPrompt, List<Tool> tools) {
        try {
            GenerateContentConfig.Builder configBuilder = GenerateContentConfig.builder()
                    .temperature(0.3f)
                    .maxOutputTokens(8192)
                    .automaticFunctionCalling(AutomaticFunctionCallingConfig.builder().disable(true).build());

            if (systemPrompt != null && !systemPrompt.isBlank()) {
                configBuilder.systemInstruction(Content.fromParts(Part.fromText(systemPrompt)));
            }
            if (tools != null && !tools.isEmpty()) {
                configBuilder.tools(tools);
            }

            return client.models.generateContent(model, history, configBuilder.build());
        } catch (Exception e) {
            log.error("Error generating with tools", e);
            throw new RuntimeException("Failed to get response from AI: " + e.getMessage());
        }
    }
}
