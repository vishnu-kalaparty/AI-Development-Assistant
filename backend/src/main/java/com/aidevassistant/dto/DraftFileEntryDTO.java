package com.aidevassistant.dto;

public class DraftFileEntryDTO {
    private String path;
    /** new = draft only, modified = differs from disk */
    private String status;

    public DraftFileEntryDTO() {
    }

    public DraftFileEntryDTO(String path, String status) {
        this.path = path;
        this.status = status;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
