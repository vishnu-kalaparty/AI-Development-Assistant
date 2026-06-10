package com.aidevassistant.service;

import com.aidevassistant.dto.MessageDTO;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class LLMService {

    private static final Logger log = LoggerFactory.getLogger(LLMService.class);

    private final ChatModel chatModel;

    public LLMService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public String chat(List<MessageDTO> messages) {
        try {
            List<ChatMessage> contents = new ArrayList<>();
            StringBuilder system = new StringBuilder();

            for (MessageDTO msg : messages) {
                if (msg.getRole().equalsIgnoreCase("system")) {
                    if (system.length() > 0) {
                        system.append('\n');
                    }
                    system.append(msg.getContent());
                } else if (msg.getRole().equalsIgnoreCase("user")) {
                    contents.add(UserMessage.from(msg.getContent()));
                } else if (msg.getRole().equalsIgnoreCase("assistant") || msg.getRole().equalsIgnoreCase("model")) {
                    contents.add(AiMessage.from(msg.getContent()));
                }
            }

            if (system.length() > 0) {
                contents.add(0, SystemMessage.from(system.toString()));
            }
            if (contents.stream().noneMatch(m -> m instanceof UserMessage)) {
                contents.add(UserMessage.from("Hello"));
            }

            ChatResponse response = chatModel.chat(contents);
            if (response == null || response.aiMessage() == null) {
                return "";
            }
            String text = response.aiMessage().text();
            return text != null ? text : "";
        } catch (Exception e) {
            log.error("Chat request failed", e);
            throw new RuntimeException("Failed to get response from AI: " + e.getMessage(), e);
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
