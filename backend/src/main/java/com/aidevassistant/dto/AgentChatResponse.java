package com.aidevassistant.dto;

import java.util.List;

public class AgentChatResponse {
    private List<MessageDTO> messages;
    private String editorContent;
    private String editorPath;
    
    public AgentChatResponse() {}
    
    public AgentChatResponse(List<MessageDTO> messages, String editorContent, String editorPath) {
        this.messages = messages;
        this.editorContent = editorContent;
        this.editorPath = editorPath;
    }
    
    public List<MessageDTO> getMessages() {
        return messages;
    }
    
    public void setMessages(List<MessageDTO> messages) {
        this.messages = messages;
    }
    
    public String getEditorContent() {
        return editorContent;
    }
    
    public void setEditorContent(String editorContent) {
        this.editorContent = editorContent;
    }
    
    public String getEditorPath() {
        return editorPath;
    }
    
    public void setEditorPath(String editorPath) {
        this.editorPath = editorPath;
    }
}
