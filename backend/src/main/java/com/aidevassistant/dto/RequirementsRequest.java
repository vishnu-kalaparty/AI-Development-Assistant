package com.aidevassistant.dto;

public class RequirementsRequest {
    private String productName;
    private String requirementType;
    
    public RequirementsRequest() {}
    
    public RequirementsRequest(String productName, String requirementType) {
        this.productName = productName;
        this.requirementType = requirementType;
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
}
