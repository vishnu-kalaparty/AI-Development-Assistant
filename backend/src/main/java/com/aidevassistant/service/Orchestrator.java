package com.aidevassistant.service;

import com.aidevassistant.agent.AgentRun;
import com.aidevassistant.agent.AgentStep;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class Orchestrator {

    public static final String DEFAULT_ENGINEER = "development/software_engineers/Software-Engineer.md";
    public static final String DEFAULT_REVIEWER = "quality_testing/qa_engineers/Alex-QA.md";

    private final AgentRunner agentRunner;
    private final PersonaService personaService;
    private final PromptService promptService;
    private final AgentToolsService agentToolsService;

    @Value("${agent.max-steps:8}")
    private int maxSteps;

    @Value("${agent.max-review-rounds:1}")
    private int maxReviewRounds;

    public Orchestrator(
            AgentRunner agentRunner,
            PersonaService personaService,
            PromptService promptService,
            AgentToolsService agentToolsService
    ) {
        this.agentRunner = agentRunner;
        this.personaService = personaService;
        this.promptService = promptService;
        this.agentToolsService = agentToolsService;
    }

    public AgentRun runPipeline(String task, String engineerPersonaPath, String reviewerPersonaPath, boolean includeReviewer) {
        if (task == null || task.isBlank()) {
            return AgentRun.error("Task is required");
        }

        String engineerPersona = personaService.load(
                blankToDefault(engineerPersonaPath, DEFAULT_ENGINEER)
        );
        String reviewerPersona = personaService.load(
                blankToDefault(reviewerPersonaPath, DEFAULT_REVIEWER)
        );

        List<AgentStep> allSteps = new ArrayList<>();
        LinkedHashSet<String> filesWritten = new LinkedHashSet<>();

        AgentRun engineerRun = agentRunner.run(
                "ENGINEER",
                promptService.getEngineerSystemPrompt(engineerPersona),
                promptService.getEngineerTaskPrompt(task),
                maxSteps
        );
        allSteps.addAll(engineerRun.getSteps());
        filesWritten.addAll(engineerRun.getFilesWritten());

        if ("error".equals(engineerRun.getStatus())) {
            return new AgentRun("error", engineerRun.getSummary(), allSteps, new ArrayList<>(filesWritten));
        }

        if (!includeReviewer) {
            return new AgentRun(engineerRun.getStatus(), engineerRun.getSummary(), allSteps, new ArrayList<>(filesWritten));
        }

        String reviewNotes = "";
        AgentRun reviewRun = null;
        for (int round = 1; round <= Math.max(1, maxReviewRounds); round++) {
            reviewRun = agentRunner.run(
                    "REVIEWER",
                    promptService.getReviewerSystemPrompt(reviewerPersona),
                    promptService.getReviewerTaskPrompt(task, engineerRun.getSummary(), sandboxSnapshot()),
                    Math.max(4, maxSteps - 2)
            );
            allSteps.addAll(reviewRun.getSteps());
            filesWritten.addAll(reviewRun.getFilesWritten());

            if (!needsChanges(reviewRun.getSummary())) {
                return new AgentRun(
                        "completed",
                        joinSummaries(engineerRun.getSummary(), reviewRun.getSummary()),
                        allSteps,
                        new ArrayList<>(filesWritten)
                );
            }

            reviewNotes = reviewRun.getSummary();
            if (round >= maxReviewRounds) {
                break;
            }

            engineerRun = agentRunner.run(
                    "ENGINEER",
                    promptService.getEngineerSystemPrompt(engineerPersona),
                    promptService.getEngineerFixPrompt(task, reviewNotes),
                    maxSteps
            );
            allSteps.addAll(engineerRun.getSteps());
            filesWritten.addAll(engineerRun.getFilesWritten());
        }

        return new AgentRun(
                "needs_changes",
                joinSummaries(engineerRun.getSummary(), reviewRun != null ? reviewRun.getSummary() : reviewNotes),
                allSteps,
                new ArrayList<>(filesWritten)
        );
    }

    private String sandboxSnapshot() {
        return agentToolsService.listFiles("", true);
    }

    private static boolean needsChanges(String summary) {
        if (summary == null) {
            return false;
        }
        String upper = summary.toUpperCase();
        return upper.contains("CHANGES_REQUESTED");
    }

    private static String joinSummaries(String engineer, String reviewer) {
        StringBuilder builder = new StringBuilder();
        if (engineer != null && !engineer.isBlank()) {
            builder.append("Engineer:\n").append(engineer.trim()).append("\n\n");
        }
        if (reviewer != null && !reviewer.isBlank()) {
            builder.append("Reviewer:\n").append(reviewer.trim());
        }
        return builder.toString().trim();
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
