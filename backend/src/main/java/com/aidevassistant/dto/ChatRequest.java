package com.aidevassistant.dto;

import java.util.List;

public class ChatRequest {
    private List<MessageDTO> messages;
    
    public ChatRequest() {}
    
    public ChatRequest(List<MessageDTO> messages) {
        this.messages = messages;
    }
    
    public List<MessageDTO> getMessages() {
        return messages;
    }
    
    public void setMessages(List<MessageDTO> messages) {
        this.messages = messages;
    }
}
