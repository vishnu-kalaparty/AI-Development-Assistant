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
               "Use the tools whenever you need to inspect files, search code, write changes, or run code. " +
               "Explain your reasoning clearly, but keep answers concise and focused on software engineering.";
    }

    public String getEngineerSystemPrompt(String persona) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are the Engineer in a two-agent coding loop.\n");
        prompt.append("You MUST use tools to write files into the sandbox and run them.\n");
        prompt.append("Available tools: list_files, read_file, write_file, search_files, run_code.\n");
        prompt.append("All paths are relative to the sandbox root. Never attempt to leave the sandbox.\n");
        prompt.append("Prefer Python unless the user asks for another language.\n");
        prompt.append("Python files must be runnable with `python3 file.py`.\n");
        prompt.append("Java files must be a single file with no package line; the public class name must match the filename.\n");
        prompt.append("After writing a file, call run_code on that exact path to verify it works.\n");
        prompt.append("When the task is done, stop calling tools and give a short summary of files created and how to run them.\n");
        if (persona != null && !persona.isBlank()) {
            prompt.append("\nPersona instructions:\n").append(persona);
        }
        return prompt.toString();
    }

    public String getReviewerSystemPrompt(String persona) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are the Reviewer in a two-agent coding loop.\n");
        prompt.append("Use tools to read and run the Engineer's files. Do not rewrite the whole program unless it is broken.\n");
        prompt.append("Available tools: list_files, read_file, write_file, search_files, run_code.\n");
        prompt.append("Check correctness against the original task, then run the program.\n");
        prompt.append("End your FINAL message with exactly one of these tokens on its own line:\n");
        prompt.append("APPROVE\n");
        prompt.append("CHANGES_REQUESTED\n");
        prompt.append("If you request changes, include a short punch list.\n");
        if (persona != null && !persona.isBlank()) {
            prompt.append("\nPersona instructions:\n").append(persona);
        }
        return prompt.toString();
    }

    public String getEngineerTaskPrompt(String task) {
        return "Implement this task in the sandbox. Write real files and run them.\n\nTask:\n" + task;
    }

    public String getEngineerFixPrompt(String task, String reviewNotes) {
        return "The Reviewer requested changes. Keep using tools to patch files and re-run them.\n\n" +
               "Original task:\n" + task + "\n\nReviewer notes:\n" + reviewNotes;
    }

    public String getReviewerTaskPrompt(String task, String engineerSummary, String sandboxListing) {
        return "Review the Engineer's work for this task.\n\nTask:\n" + task +
               "\n\nEngineer summary:\n" + (engineerSummary == null ? "(none)" : engineerSummary) +
               "\n\nCurrent sandbox listing:\n" + sandboxListing +
               "\n\nRead the relevant files, run them, then approve or request changes.";
    }
}
