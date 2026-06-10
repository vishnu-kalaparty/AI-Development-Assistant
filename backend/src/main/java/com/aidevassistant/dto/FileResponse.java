package com.aidevassistant.dto;

public class FileResponse {
    private String content;
    private String message;
    /** Disk baseline when comparing a draft file (null for new files). */
    private String baseline;
    /** new | modified | disk */
    private String fileStatus;

    public FileResponse() {}

    public FileResponse(String content, String message) {
        this.content = content;
        this.message = message;
    }

    public FileResponse(String content, String message, String baseline, String fileStatus) {
        this.content = content;
        this.message = message;
        this.baseline = baseline;
        this.fileStatus = fileStatus;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }

    public String getBaseline() {
        return baseline;
    }

    public void setBaseline(String baseline) {
        this.baseline = baseline;
    }

    public String getFileStatus() {
        return fileStatus;
    }

    public void setFileStatus(String fileStatus) {
        this.fileStatus = fileStatus;
    }
}
