package com.aidevassistant.dto;

public class CodeAssistRequest {
    private String code;
    private String language;
    private String action;
    private String instruction;
    
    public CodeAssistRequest() {}
    
    public CodeAssistRequest(String code, String language, String action, String instruction) {
        this.code = code;
        this.language = language;
        this.action = action;
        this.instruction = instruction;
    }
    
    public String getCode() {
        return code;
    }
    
    public void setCode(String code) {
        this.code = code;
    }
    
    public String getLanguage() {
        return language;
    }
    
    public void setLanguage(String language) {
        this.language = language;
    }
    
    public String getAction() {
        return action;
    }
    
    public void setAction(String action) {
        this.action = action;
    }
    
    public String getInstruction() {
        return instruction;
    }
    
    public void setInstruction(String instruction) {
        this.instruction = instruction;
    }
}
