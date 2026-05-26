package com.aidevassistant.graph;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.stereotype.Service;

@Service
public class StageAgentFactory {

    private final ChatModel chatModel;

    public StageAgentFactory(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public String run(String systemPrompt, String userMessage, Object tools, int maxToolCalls) {
        if (chatModel == null) {
            throw new IllegalStateException("chatModel is not configured (check llm.provider and API keys)");
        }
        var builder = AiServices.builder(StageAgent.class)
                .systemMessage(systemPrompt == null ? "" : systemPrompt)
                .chatModel(chatModel);
        if (tools != null) {
            builder.tools(tools);
            builder.maxSequentialToolsInvocations(Math.max(1, maxToolCalls));
        }
        return builder.build().chat(userMessage);
    }
}
