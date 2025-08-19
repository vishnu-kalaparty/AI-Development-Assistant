package com.aidevassistant.service;

import org.springframework.stereotype.Service;

@Service
public class PromptService {
    
    public String getQuickChatSystemPrompt() {
        return "You are a helpful AI assistant specialized in software development and coding. " +
               "Provide clear, accurate, and practical answers to programming questions. " +
               "When explaining concepts, use examples and be concise but thorough.";
    }
    
    public String getLearningTopicPrompt(String topic, String level) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Explain the following software development topic: ").append(topic).append("\n\n");
        
        switch (level.toLowerCase()) {
            case "beginner":
                prompt.append("The explanation should be suitable for a beginner programmer. ");
                prompt.append("Use simple terms, provide basic examples, and avoid jargon. ");
                prompt.append("Focus on core concepts and practical understanding.");
                break;
            case "intermediate":
                prompt.append("The explanation should be suitable for an intermediate programmer. ");
                prompt.append("Include more technical details, common use cases, and best practices. ");
                prompt.append("Provide code examples and discuss trade-offs.");
                break;
            case "advanced":
                prompt.append("The explanation should be suitable for an advanced programmer. ");
                prompt.append("Include in-depth technical details, performance considerations, and advanced patterns. ");
                prompt.append("Discuss edge cases, optimization strategies, and architectural implications.");
                break;
        }
        
        return prompt.toString();
    }
    
    public String getCodeReviewPrompt(String code, String language) {
        return String.format(
                "Review the following %s code and provide feedback on:\n" +
                "1. Code quality and style\n" +
                "2. Potential bugs or issues\n" +
                "3. Performance considerations\n" +
                "4. Best practices and improvements\n\n" +
                "Code:\n```%s\n%s\n```",
                language, language, code
        );
    }
    
    public String getCodeDebugPrompt(String code, String language) {
        return String.format(
                "Analyze the following %s code for bugs and issues:\n" +
                "1. Identify any syntax errors\n" +
                "2. Find logical errors or edge cases\n" +
                "3. Suggest fixes and corrections\n" +
                "4. Explain what might go wrong\n\n" +
                "Code:\n```%s\n%s\n```",
                language, language, code
        );
    }
    
    public String getCodeModifyPrompt(String code, String language, String instruction) {
        return String.format(
                "Modify the following %s code according to these instructions: %s\n\n" +
                "Original code:\n```%s\n%s\n```\n\n" +
                "Provide the modified code and explain the changes made.",
                language, instruction, language, code
        );
    }
    
    public String getRequirementsPrompt(String productName, String requirementType) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Generate a ").append(requirementType).append(" document for a software product named: ")
              .append(productName).append("\n\n");
        
        switch (requirementType) {
            case "Business Requirements":
                prompt.append("Include:\n");
                prompt.append("1. Executive Summary\n");
                prompt.append("2. Business Objectives\n");
                prompt.append("3. Stakeholder Analysis\n");
                prompt.append("4. Success Criteria\n");
                prompt.append("5. Key Features\n");
                prompt.append("6. Budget and Timeline Considerations\n");
                break;
            case "Vision Statement":
                prompt.append("Include:\n");
                prompt.append("1. Product Vision\n");
                prompt.append("2. Target Audience\n");
                prompt.append("3. Key Problems Solved\n");
                prompt.append("4. Unique Value Proposition\n");
                prompt.append("5. Long-term Goals\n");
                break;
            case "Project Plan":
                prompt.append("Include:\n");
                prompt.append("1. Project Overview\n");
                prompt.append("2. Phases and Milestones\n");
                prompt.append("3. Resource Allocation\n");
                prompt.append("4. Risk Assessment\n");
                prompt.append("5. Timeline\n");
                prompt.append("6. Success Metrics\n");
                break;
            case "Technical Specifications":
                prompt.append("Include:\n");
                prompt.append("1. System Architecture\n");
                prompt.append("2. Technology Stack\n");
                prompt.append("3. API Specifications\n");
                prompt.append("4. Data Models\n");
                prompt.append("5. Security Requirements\n");
                prompt.append("6. Performance Requirements\n");
                break;
        }
        
        prompt.append("\nFormat the document professionally with clear sections and detailed content.");
        return prompt.toString();
    }
    
    public String getAgentSystemPrompt() {
        return "You are an AI coding agent embedded in a development assistant application. " +
               "You help with code review, authoring, and debugging tasks. " +
               "You have access to tools that operate ONLY inside the coding sandbox at 'data/sandbox/'. " +
               "Use the tools whenever you need to inspect files, search code, or write changes. " +
               "Explain your reasoning clearly, but keep answers concise and focused on software engineering.";
    }
}
