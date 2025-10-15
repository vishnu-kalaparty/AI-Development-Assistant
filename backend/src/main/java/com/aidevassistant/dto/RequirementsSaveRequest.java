package com.aidevassistant.dto;

public class RequirementsSaveRequest {
    private String productName;
    private String requirementType;
    private String content;
    
    public RequirementsSaveRequest() {}
    
    public RequirementsSaveRequest(String productName, String requirementType, String content) {
        this.productName = productName;
        this.requirementType = requirementType;
        this.content = content;
    }
    
    public String getProductName() {
        return productName;
    }
    
    public void setProductName(String productName) {
        this.productName = productName;
    }
    
    public String getRequirementType() {
        return requirementType;
    }
    
    public void setRequirementType(String requirementType) {
        this.requirementType = requirementType;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
}
