package com.aidevassistant.service;

import com.aidevassistant.dto.MessageDTO;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LLMServiceTest {

    @Test
    void chatUsesLangChainModel() {
        ChatModel model = new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest request) {
                return ChatResponse.builder().aiMessage(AiMessage.from("hello from model")).build();
            }
        };
        LLMService service = new LLMService(model);
        String reply = service.chat(List.of(new MessageDTO("user", "hi")));
        assertEquals("hello from model", reply);
    }
}
