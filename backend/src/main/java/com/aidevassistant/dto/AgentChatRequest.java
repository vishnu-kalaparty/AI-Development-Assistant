package com.aidevassistant.dto;

import java.util.List;

public class AgentChatRequest {
    private List<MessageDTO> messages;
    private String personaPath;
    private String editorContent;
    private String editorPath;
    
    public AgentChatRequest() {}
    
    public AgentChatRequest(List<MessageDTO> messages, String personaPath, String editorContent, String editorPath) {
        this.messages = messages;
        this.personaPath = personaPath;
        this.editorContent = editorContent;
        this.editorPath = editorPath;
    }
    
    public List<MessageDTO> getMessages() {
        return messages;
    }
    
    public void setMessages(List<MessageDTO> messages) {
        this.messages = messages;
    }
    
    public String getPersonaPath() {
        return personaPath;
    }
    
    public void setPersonaPath(String personaPath) {
        this.personaPath = personaPath;
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
