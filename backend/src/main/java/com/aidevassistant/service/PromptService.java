package com.aidevassistant.service;

import org.springframework.stereotype.Service;

@Service
public class PromptService {

    /** Top-level sandbox rules; exact paths come from Architecture / docs/README.md. */
    public static final String SANDBOX_BASE_RULES = """
            Sandbox rules:
            - All deliverables live under src/, tests/, and/or docs/ — never at the sandbox root.
            - docs/README.md is the canonical folder-structure document the Engineer writes and everyone follows.
            - Pick a layout that matches the task (see architecture patterns below); do not use one-size-fits-all paths.
            """;

    /** Guidance for the architect to choose a pattern and file tree by task type. */
    public static final String ARCHITECTURE_PATTERN_GUIDE = """
            Choose ONE architecture pattern that fits the task, then list every file path under it.

            Pattern examples (adapt names to language/stack):
            - CLI / script — src/<module>.py, tests/test_<module>.py, docs/README.md
            - MVC web app — src/models/, src/views/, src/controllers/ (or templates/, static/), tests/, docs/README.md
            - REST API — src/routes/ or src/api/, src/services/, src/models/, tests/, docs/README.md
            - Layered backend — src/domain/, src/application/, src/infrastructure/, tests/, docs/README.md
            - Single-file exercise — src/main.py, tests/test_main.py, docs/README.md

            Architecture output MUST include these sections:
            1. ## Pattern — name and one-line rationale (e.g. "MVC — browser UI with separate model/view/controller")
            2. ## File tree — markdown tree for docs/README.md (this is what Engineer will create verbatim)
            3. ## File plan — bullet per path: role, main exports, how to run or test
            4. ## Run & test — commands using paths from the file tree (e.g. run_code on src/..., tests under tests/)
            """;

    /** Rules for agents that have real LangChain4j tools wired in. */
    public static final String TOOL_EXECUTION_RULES = """
            CRITICAL tool rules:
            - Tools are REAL in this runtime — invoke them; do not simulate or describe them.
            - NEVER output JSON like {"name":"writeFile",...} or numbered plans ("Step 1: ...").
            - NEVER paste full file contents in chat — use write_file.
            - All paths are relative to the workflow sandbox root (e.g. src/main.py, docs/README.md).
            - NEVER prefix paths with runs/ or the project folder name.
            Exploration workflow (existing or indexed sandboxes):
            - Default: call semantic_search first to quickly see relevant code snippets for the task — do not read_file every file up front.
            - Use search hits to pick which paths matter; only call read_file on a full file when snippets are enough to identify the path but you need more context, snippets conflict with the task, or you are about to edit that file.
            - Treat Architecture and docs/README.md as the folder map — they say where files live, not what every file contains.
            - Before write_file on existing code, semantic_search to find which file/module should hold the change, then read_file that file if snippets are not enough to edit safely.
            - search_files is for exact strings (function names, error text); semantic_search is for behavior, "where is X handled?", and "where should I add Y?".
            - On-disk files and the user's intent win when index snippets disagree — read_file to verify before changing code.
            - On a brand-new empty sandbox, skip semantic_search and create files from Architecture.
            """;
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
               "For existing code: default to semantic_search first for quick relevant snippets; read_file the full file only when you need more context or snippets conflict with the task. " +
               "Use the tools whenever you need to inspect files, search code, write changes, or run code. " +
               "Explain your reasoning clearly, but keep answers concise and focused on software engineering.";
    }

    public String getRequirementsAnalystSystemPrompt(String persona) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are the Requirements Analyst. Produce a short, structured requirements brief.\n");
        prompt.append("Include goal, users, functional requirements, constraints, and acceptance checks.\n");
        prompt.append("State that the Architect will define the exact folder tree in docs/README.md.\n");
        prompt.append("Do not write code. Do not call tools.\n");
        if (persona != null && !persona.isBlank()) {
            prompt.append("\nPersona instructions:\n").append(persona);
        }
        return prompt.toString();
    }

    public String getRequirementsAnalystTaskPrompt(String task) {
        return "Write requirements for this task:\n\n" + task;
    }

    public String getArchitectSystemPrompt(String persona) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are the System Architect. Propose a compact implementation plan tailored to the task.\n");
        prompt.append(SANDBOX_BASE_RULES);
        prompt.append(ARCHITECTURE_PATTERN_GUIDE);
        prompt.append("Pick MVC, layered, REST, or CLI layout based on whether the task is a web UI, API, service, or script — not a fixed template.\n");
        prompt.append("The ## File tree section must be copy-paste ready for docs/README.md.\n");
        prompt.append("List every file path and how the program will be run and tested.\n");
        prompt.append("Keep it inside the workflow sandbox folder. Do not write code. Do not call tools.\n");
        if (persona != null && !persona.isBlank()) {
            prompt.append("\nPersona instructions:\n").append(persona);
        }
        return prompt.toString();
    }

    public String getArchitectTaskPrompt(String task, String requirements) {
        return "Task:\n" + task + "\n\nRequirements:\n" + nullToEmpty(requirements)
                + "\n\nPropose the architecture: choose the right pattern for this task, then output Pattern, File tree (for docs/README.md), File plan, and Run & test sections.";
    }

    public String getEngineerSystemPrompt(String persona) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are the Engineer in an SDLC coding loop.\n");
        prompt.append("You MUST use tools to write files into the sandbox and run them.\n");
        prompt.append("Available tools: list_files, read_file, write_file, mkdir, search_files, run_code, semantic_search.\n");
        prompt.append(TOOL_EXECUTION_RULES);
        prompt.append(SANDBOX_BASE_RULES);
        prompt.append("The Architecture section and docs/README.md define the exact file tree — follow them exactly.\n");
        prompt.append("When the sandbox already has code, always start with semantic_search for quick relevant snippets — read_file full files only when snippets are insufficient, conflicting, or you are about to edit that path.\n");
        prompt.append("On a brand-new empty sandbox, skip search and create files from Architecture.\n");
        prompt.append("Use MVC/layered/API subfolders when Architecture specifies them (e.g. src/models/, src/views/, src/controllers/).\n");
        prompt.append("Do not invent a different layout than Architecture or docs/README.md.\n");
        prompt.append("Use mkdir to create folders before writing nested files when needed.\n");
        prompt.append("write_file creates parent directories automatically. Never attempt to leave the sandbox.\n");
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

    public String getQaSystemPrompt(String persona) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are QA. Write or run tests against the Engineer's sandbox files.\n");
        prompt.append("Available tools: list_files, read_file, write_file, run_code, semantic_search.\n");
        prompt.append(TOOL_EXECUTION_RULES);
        prompt.append(SANDBOX_BASE_RULES);
        prompt.append("Read docs/README.md and Architecture — tests must use paths from that file tree.\n");
        prompt.append("When tests fail or you need to understand implementation, semantic_search first for quick snippets — read_file full files only if you need more context or snippets conflict.\n");
        prompt.append("Put new test files only where Architecture/docs/README.md specifies (usually tests/).\n");
        prompt.append("Tests that import application modules (e.g. `from calculator import …`) rely on run_code setting PYTHONPATH to the project src/ folder automatically.\n");
        prompt.append("You may write test files and run them. Do not rewrite the whole program unless a test cannot run.\n");
        prompt.append("End your FINAL message with exactly one token on its own line: TESTS_PASSED or TESTS_FAILED.\n");
        if (persona != null && !persona.isBlank()) {
            prompt.append("\nPersona instructions:\n").append(persona);
        }
        return prompt.toString();
    }

    public String getQaTaskPrompt(String task, String engineerSummary, String sandboxListing, String architecture) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Test this task.\n\nTask:\n").append(task);
        if (notBlank(architecture)) {
            prompt.append("\n\nArchitecture (paths must match docs/README.md and this plan):\n").append(architecture);
        }
        prompt.append("\n\nEngineer summary:\n").append(nullToEmpty(engineerSummary));
        prompt.append("\n\nSandbox listing:\n").append(nullToEmpty(sandboxListing));
        prompt.append("\n\nIf the sandbox already has application code, semantic_search first for quick relevant snippets, then read_file only paths that need full context.");
        prompt.append("Write tests if needed, run them, then report TESTS_PASSED or TESTS_FAILED.");
        return prompt.toString();
    }

    public String getReviewerSystemPrompt(String persona) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are the Reviewer in an SDLC coding loop.\n");
        prompt.append("Use tools to read, search, and run the Engineer's files. Do not rewrite the program.\n");
        prompt.append("Available tools: list_files, read_file, search_files, run_code, semantic_search.\n");
        prompt.append(TOOL_EXECUTION_RULES);
        prompt.append(SANDBOX_BASE_RULES);
        prompt.append("Default: semantic_search first for quick relevant snippets; read_file full files only when snippets are insufficient, conflicting, or you need more context before judging or editing.\n");
        prompt.append("Verify files match Architecture and docs/README.md (including MVC/layered subfolders when specified).\n");
        prompt.append("Check correctness against the original task, then run the program from the path in docs/README.md.\n");
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
        return getEngineerTaskPrompt(task, "", "", "", "", "");
    }

    public String getEngineerTaskPrompt(
            String task,
            String requirements,
            String architecture,
            String testReport,
            String reviewNotes,
            String humanNotes
    ) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Implement this task in the sandbox. Write real files and run them.\n\nTask:\n").append(task);
        if (notBlank(requirements)) {
            prompt.append("\n\nRequirements:\n").append(requirements);
        }
        if (notBlank(architecture)) {
            prompt.append("\n\nArchitecture (source of truth for paths — implement exactly):\n").append(architecture);
        }
        if (notBlank(testReport)) {
            prompt.append("\n\nPrevious test report:\n").append(testReport);
            prompt.append("\nsemantic_search first for quick snippets on the failing area; read_file full files only if snippets are not enough to fix the issue.\n");
        }
        if (notBlank(reviewNotes)) {
            prompt.append("\n\nReviewer notes:\n").append(reviewNotes);
            prompt.append("\nsemantic_search first for quick snippets on the review item; read_file full files only if snippets are not enough to verify.\n");
        }
        if (notBlank(humanNotes)) {
            prompt.append("\n\nHuman notes:\n").append(humanNotes);
        }
        prompt.append("\n\n").append(SANDBOX_BASE_RULES);
        if (notBlank(architecture)) {
            prompt.append("Write docs/README.md from Architecture ## File tree if missing, then every other listed path.\n");
        } else {
            prompt.append("Implement under src/, tests/, and docs/README.md.\n");
        }
        prompt.append("Run code from the entry path defined in Architecture or docs/README.md.\n");
        prompt.append("If the sandbox already has files, semantic_search first for quick relevant snippets, then read_file full files only where you need more context before writing.\n");
        return prompt.toString();
    }

    public String getReviewerTaskPrompt(String task, String engineerSummary, String sandboxListing, String architecture) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Review the Engineer's work for this task.\n\nTask:\n").append(task);
        if (notBlank(architecture)) {
            prompt.append("\n\nArchitecture (compare sandbox to this and docs/README.md):\n").append(architecture);
        }
        prompt.append("\n\nEngineer summary:\n").append(engineerSummary == null ? "(none)" : engineerSummary);
        prompt.append("\n\nCurrent sandbox listing:\n").append(sandboxListing);
        prompt.append("\n\nsemantic_search first for quick relevant snippets, then read_file full files only when snippets conflict or you need more context. Run the program, then approve or request changes.");
        return prompt.toString();
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
