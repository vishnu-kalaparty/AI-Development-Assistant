package com.aidevassistant.service;

import com.aidevassistant.agent.AgentRun;
import com.aidevassistant.agent.AgentStep;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class AgentRunner {

    private static final Logger log = LoggerFactory.getLogger(AgentRunner.class);

    private final LLMService llmService;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${agent.max-steps:8}")
    private int defaultMaxSteps;

    public AgentRunner(LLMService llmService, ToolRegistry toolRegistry) {
        this.llmService = llmService;
        this.toolRegistry = toolRegistry;
    }

    public AgentRun run(String stage, String systemPrompt, String task, int maxSteps) {
        int limit = maxSteps > 0 ? maxSteps : defaultMaxSteps;
        List<Content> history = new ArrayList<>();
        history.add(Content.builder()
                .role("user")
                .parts(List.of(Part.fromText(task)))
                .build());

        List<AgentStep> steps = new ArrayList<>();
        LinkedHashSet<String> filesWritten = new LinkedHashSet<>();
        String lastText = "";

        for (int i = 1; i <= limit; i++) {
            GenerateContentResponse response;
            List<Part> parts;
            List<FunctionCall> calls;
            String thought;
            try {
                response = llmService.generate(history, systemPrompt, toolRegistry.declarations());
                parts = response.parts();
                calls = response.functionCalls();
                thought = response.text() != null ? response.text() : "";
            } catch (Exception e) {
                log.error("{} generate failed at step {}", stage, i, e);
                return new AgentRun("error", e.getMessage(), steps, new ArrayList<>(filesWritten));
            }
            if (!thought.isBlank()) {
                lastText = thought;
            }

            if (parts != null && !parts.isEmpty()) {
                history.add(Content.builder().role("model").parts(parts).build());
            }

            if (calls == null || calls.isEmpty()) {
                log.info("{} finished after {} step(s)", stage, i);
                return AgentRun.completed(lastText, steps, new ArrayList<>(filesWritten));
            }

            List<Part> toolParts = new ArrayList<>();
            for (FunctionCall call : calls) {
                String name = call.name().orElse("");
                Map<String, Object> args = call.args().orElse(Map.of());
                String observation = toolRegistry.execute(name, args);
                String argJson = toJson(args);

                if ("write_file".equals(name) && args.get("path") != null) {
                    filesWritten.add(String.valueOf(args.get("path")));
                }

                steps.add(new AgentStep(stage, i, thought, name, argJson, observation));
                thought = "";
                toolParts.add(Part.fromFunctionResponse(name, Map.of("output", observation)));
            }
            history.add(Content.builder().role("user").parts(toolParts).build());
        }

        log.warn("{} hit max steps {}", stage, limit);
        return AgentRun.exhausted(
                lastText.isBlank() ? "Stopped after " + limit + " tool steps." : lastText,
                steps,
                new ArrayList<>(filesWritten)
        );
    }

    private String toJson(Map<String, Object> args) {
        try {
            Map<String, Object> copy = args;
            if (args.containsKey("content")) {
                copy = new java.util.LinkedHashMap<>(args);
                String content = String.valueOf(args.get("content"));
                if (content.length() > 400) {
                    copy.put("content", content.substring(0, 400) + "...");
                }
            }
            return objectMapper.writeValueAsString(copy);
        } catch (Exception e) {
            return String.valueOf(args);
        }
    }
}
