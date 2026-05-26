package com.aidevassistant.graph;

import com.aidevassistant.agent.AgentStep;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SdlcState extends AgentState {

    public static final String WORKFLOW_ID = "workflowId";
    public static final String TASK = "task";
    public static final String REQUIREMENTS = "requirementsDoc";
    public static final String ARCHITECTURE = "architectureDoc";
    public static final String ENGINEER_SUMMARY = "engineerSummary";
    public static final String TEST_REPORT = "testReport";
    public static final String TESTS_PASSED = "testsPassed";
    public static final String REVIEW_VERDICT = "reviewVerdict";
    public static final String REVIEW_NOTES = "reviewNotes";
    public static final String HUMAN_DECISION = "humanDecision";
    public static final String HUMAN_NOTES = "humanNotes";
    public static final String ITERATION = "iteration";
    public static final String MAX_ROUNDS = "maxRounds";
    public static final String MAX_STEPS = "maxSteps";
    public static final String AWAIT_HUMAN = "awaitHuman";
    public static final String INCLUDE_REVIEWER = "includeReviewer";
    public static final String STATUS = "status";
    public static final String PHASE = "currentPhase";
    public static final String ERROR = "error";
    public static final String SUMMARY = "summary";
    public static final String ENGINEER_PERSONA = "engineerPersona";
    public static final String REVIEWER_PERSONA = "reviewerPersona";
    public static final String ARCHITECT_PERSONA = "architectPersona";
    public static final String ANALYST_PERSONA = "analystPersona";
    public static final String STEPS = "steps";
    public static final String FILES_WRITTEN = "filesWritten";
    public static final String SANDBOX_PATH = "sandboxPath";

    public static final Map<String, Channel<?>> SCHEMA = schema();

    private static Map<String, Channel<?>> schema() {
        Map<String, Channel<?>> schema = new HashMap<>();
        schema.put(WORKFLOW_ID, Channels.base(() -> ""));
        schema.put(TASK, Channels.base(() -> ""));
        schema.put(REQUIREMENTS, Channels.base(() -> ""));
        schema.put(ARCHITECTURE, Channels.base(() -> ""));
        schema.put(ENGINEER_SUMMARY, Channels.base(() -> ""));
        schema.put(TEST_REPORT, Channels.base(() -> ""));
        schema.put(TESTS_PASSED, Channels.base(() -> Boolean.FALSE));
        schema.put(REVIEW_VERDICT, Channels.base(() -> ""));
        schema.put(REVIEW_NOTES, Channels.base(() -> ""));
        schema.put(HUMAN_DECISION, Channels.base(() -> ""));
        schema.put(HUMAN_NOTES, Channels.base(() -> ""));
        schema.put(ITERATION, Channels.base(() -> 0));
        schema.put(MAX_ROUNDS, Channels.base(() -> 1));
        schema.put(MAX_STEPS, Channels.base(() -> 8));
        schema.put(AWAIT_HUMAN, Channels.base(() -> Boolean.TRUE));
        schema.put(INCLUDE_REVIEWER, Channels.base(() -> Boolean.TRUE));
        schema.put(STATUS, Channels.base(() -> "running"));
        schema.put(PHASE, Channels.base(() -> "requirements"));
        schema.put(ERROR, Channels.base(() -> ""));
        schema.put(SUMMARY, Channels.base(() -> ""));
        schema.put(ENGINEER_PERSONA, Channels.base(() -> ""));
        schema.put(REVIEWER_PERSONA, Channels.base(() -> ""));
        schema.put(ARCHITECT_PERSONA, Channels.base(() -> ""));
        schema.put(ANALYST_PERSONA, Channels.base(() -> ""));
        schema.put(STEPS, Channels.appender(ArrayList::new));
        schema.put(FILES_WRITTEN, Channels.appender(ArrayList::new));
        schema.put(SANDBOX_PATH, Channels.base(() -> ""));
        return Map.copyOf(schema);
    }

    public SdlcState(Map<String, Object> initData) {
        super(initData);
    }

    public String workflowId() {
        return str(WORKFLOW_ID);
    }

    public String task() {
        return str(TASK);
    }

    public String requirementsDoc() {
        return str(REQUIREMENTS);
    }

    public String architectureDoc() {
        return str(ARCHITECTURE);
    }

    public String engineerSummary() {
        return str(ENGINEER_SUMMARY);
    }

    public String testReport() {
        return str(TEST_REPORT);
    }

    public boolean testsPassed() {
        return bool(TESTS_PASSED);
    }

    public String reviewVerdict() {
        return str(REVIEW_VERDICT);
    }

    public String reviewNotes() {
        return str(REVIEW_NOTES);
    }

    public String humanDecision() {
        return str(HUMAN_DECISION);
    }

    public String humanNotes() {
        return str(HUMAN_NOTES);
    }

    public int iteration() {
        return num(ITERATION);
    }

    public int maxRounds() {
        return num(MAX_ROUNDS);
    }

    public int maxSteps() {
        int value = num(MAX_STEPS);
        return value <= 0 ? 8 : value;
    }

    public boolean awaitHuman() {
        return bool(AWAIT_HUMAN);
    }

    public boolean includeReviewer() {
        return bool(INCLUDE_REVIEWER);
    }

    public String status() {
        return str(STATUS);
    }

    public String currentPhase() {
        return str(PHASE);
    }

    public String error() {
        return str(ERROR);
    }

    public String summary() {
        return str(SUMMARY);
    }

    public String engineerPersona() {
        return str(ENGINEER_PERSONA);
    }

    public String reviewerPersona() {
        return str(REVIEWER_PERSONA);
    }

    public String architectPersona() {
        return str(ARCHITECT_PERSONA);
    }

    public String analystPersona() {
        return str(ANALYST_PERSONA);
    }

    public String sandboxPath() {
        return str(SANDBOX_PATH);
    }

    public boolean hasError() {
        return "error".equals(status()) || !error().isBlank();
    }

    @SuppressWarnings("unchecked")
    public List<AgentStep> steps() {
        Object raw = value(STEPS).orElse(List.of());
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<AgentStep> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof AgentStep step) {
                result.add(step);
            } else if (item instanceof List<?> nested) {
                for (Object nestedItem : nested) {
                    if (nestedItem instanceof AgentStep step) {
                        result.add(step);
                    } else if (nestedItem instanceof Map<?, ?> map) {
                        result.add(fromMap(map));
                    }
                }
            } else if (item instanceof Map<?, ?> map) {
                result.add(fromMap(map));
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public List<String> filesWritten() {
        Object raw = value(FILES_WRITTEN).orElse(List.of());
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof String s) {
                result.add(s);
            } else if (item instanceof List<?> nested) {
                for (Object nestedItem : nested) {
                    if (nestedItem instanceof String s) {
                        result.add(s);
                    }
                }
            }
        }
        return result;
    }

    private String str(String key) {
        Object value = value(key).orElse("");
        return value == null ? "" : String.valueOf(value);
    }

    private boolean bool(String key) {
        Object value = value(key).orElse(Boolean.FALSE);
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private int num(String key) {
        Object value = value(key).orElse(0);
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static AgentStep fromMap(Map<?, ?> map) {
        return new AgentStep(
                stringFrom(map, "stage"),
                toInt(map.get("step")),
                stringFrom(map, "thought"),
                nullable(map.get("toolName")),
                nullable(map.get("toolArgs")),
                nullable(map.get("observation"))
        );
    }

    private static String stringFrom(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static String nullable(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static int toInt(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return 0;
        }
    }
}
