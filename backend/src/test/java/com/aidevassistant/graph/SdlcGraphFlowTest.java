package com.aidevassistant.graph;

import com.aidevassistant.service.AgentToolsService;
import com.aidevassistant.service.ExecutionService;
import com.aidevassistant.service.PromptService;
import com.aidevassistant.service.WorkflowRunTracker;
import com.aidevassistant.service.WorkflowSnapshotStore;
import com.aidevassistant.tools.SandboxTools;
import com.aidevassistant.tools.StepRecorder;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.RunnableConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SdlcGraphFlowTest {

    @TempDir
    Path tempDir;

    @Test
    void autoApproveHappyPathCompletes() throws Exception {
        ScriptedChatModel model = new ScriptedChatModel(
                "Requirements for fizzbuzz.",
                "Use fizzbuzz.py",
                "Wrote fizzbuzz.py",
                "Ran tests.\nTESTS_PASSED",
                "Looks correct.\nAPPROVE"
        );
        CompiledGraph<SdlcState> graph = graph(model).graph(false);
        SdlcState state = run(graph, input("happy", false), "happy");
        assertEquals("completed", state.status());
        assertTrue(state.requirementsDoc().contains("fizzbuzz"));
        assertTrue(state.testsPassed());
        assertEquals("APPROVE", state.reviewVerdict());
    }

    @Test
    void failedTestsRetryThenPass() throws Exception {
        ScriptedChatModel model = new ScriptedChatModel(
                "Requirements",
                "Architecture",
                "First implementation",
                "Still broken\nTESTS_FAILED",
                "Fixed implementation",
                "Green\nTESTS_PASSED",
                "APPROVE"
        );
        SdlcState state = run(graph(model).graph(false), input("retry", false), "retry");
        assertEquals("completed", state.status());
        assertTrue(state.testsPassed());
        assertTrue(state.iteration() >= 1);
    }

    @Test
    void retryExhaustionStopsAtApproval() throws Exception {
        ScriptedChatModel model = new ScriptedChatModel(
                "Requirements",
                "Architecture",
                "Impl 1",
                "TESTS_FAILED",
                "Impl 2",
                "TESTS_FAILED"
        );
        Map<String, Object> input = input("exhaust", false);
        input.put(SdlcState.MAX_ROUNDS, 1);
        SdlcState state = run(graph(model).graph(false), input, "exhaust");
        assertEquals("exhausted", state.status());
        assertTrue(!state.testsPassed());
    }

    @Test
    void humanApprovalCanResumeWithChanges() throws Exception {
        ScriptedChatModel model = new ScriptedChatModel(
                "Requirements",
                "Architecture",
                "Impl",
                "TESTS_PASSED",
                "APPROVE",
                "Revised impl",
                "TESTS_PASSED",
                "APPROVE"
        );
        SdlcGraphBuilder builder = graph(model);
        CompiledGraph<SdlcState> hitl = builder.graph(true);
        RunnableConfig config = RunnableConfig.builder().threadId("hitl").build();
        SdlcState paused = last(hitl, GraphInput.args(input("hitl", true)), config);
        assertEquals("awaiting_human", paused.status());

        hitl.updateState(config, Map.of(
                SdlcState.HUMAN_DECISION, "CHANGES_REQUESTED",
                SdlcState.HUMAN_NOTES, "rename the file"
        ));
        SdlcState afterChanges = last(hitl, GraphInput.resume(), config);
        assertTrue("awaiting_human".equals(afterChanges.status()) || "completed".equals(afterChanges.status())
                || "running".equals(afterChanges.status()));

        if ("awaiting_human".equals(afterChanges.status())) {
            hitl.updateState(config, Map.of(SdlcState.HUMAN_DECISION, "APPROVE", SdlcState.HUMAN_NOTES, ""));
            SdlcState done = last(hitl, GraphInput.resume(), config);
            assertEquals("completed", done.status());
        }
    }

    private SdlcGraphBuilder graph(ChatModel model) {
        AgentToolsService files = new AgentToolsService(tempDir.toString());
        ExecutionService execution = new ExecutionService(files, 5);
        StepRecorder recorder = new StepRecorder(new WorkflowSnapshotStore());
        com.aidevassistant.service.WorkflowDraftStore draftStore = new com.aidevassistant.service.WorkflowDraftStore();
        com.aidevassistant.service.CodeIndexService index = disabledIndex();
        SandboxTools sandbox = new SandboxTools(files, execution, recorder, index, new WorkflowRunTracker(), draftStore);
        StageAgentFactory factory = new StageAgentFactory(model);
        SdlcNodes nodes = new SdlcNodes(factory, new PromptService(), files, sandbox, recorder, new WorkflowSnapshotStore(), index, draftStore);
        return new SdlcGraphBuilder(nodes);
    }

    private static com.aidevassistant.service.CodeIndexService disabledIndex() {
        return new com.aidevassistant.service.CodeIndexService(
                new com.aidevassistant.service.OllamaEmbeddingService(
                        "http://localhost:11434", "nomic-embed-text", new com.fasterxml.jackson.databind.ObjectMapper()),
                false,
                768,
                null
        );
    }

    private static Map<String, Object> input(String id, boolean awaitHuman) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put(SdlcState.WORKFLOW_ID, id);
        input.put(SdlcState.TASK, "Write fizzbuzz.py");
        input.put(SdlcState.ITERATION, 0);
        input.put(SdlcState.MAX_ROUNDS, 1);
        input.put(SdlcState.MAX_STEPS, 4);
        input.put(SdlcState.AWAIT_HUMAN, awaitHuman);
        input.put(SdlcState.INCLUDE_REVIEWER, true);
        input.put(SdlcState.STATUS, "running");
        input.put(SdlcState.ENGINEER_PERSONA, "engineer");
        input.put(SdlcState.REVIEWER_PERSONA, "reviewer");
        input.put(SdlcState.ARCHITECT_PERSONA, "architect");
        input.put(SdlcState.ANALYST_PERSONA, "analyst");
        return input;
    }

    private static SdlcState run(CompiledGraph<SdlcState> graph, Map<String, Object> input, String threadId) {
        return last(graph, GraphInput.args(input), RunnableConfig.builder().threadId(threadId).build());
    }

    private static SdlcState last(
            CompiledGraph<SdlcState> graph,
            GraphInput input,
            RunnableConfig config
    ) {
        NodeOutput<SdlcState> last = null;
        for (NodeOutput<SdlcState> item : graph.stream(input, config)) {
            last = item;
        }
        if (last == null || last.state() == null) {
            throw new IllegalStateException("no state");
        }
        SdlcState state = last.state();
        if (!last.isEND() && !"error".equals(state.status()) && !"completed".equals(state.status())) {
            Map<String, Object> paused = new LinkedHashMap<>(state.data());
            paused.put(SdlcState.STATUS, "awaiting_human");
            paused.put(SdlcState.PHASE, "approval");
            return new SdlcState(paused);
        }
        return state;
    }

    private static final class ScriptedChatModel implements ChatModel {
        private final Queue<String> replies;

        private ScriptedChatModel(String... replies) {
            this.replies = new ArrayDeque<>(java.util.List.of(replies));
        }

        @Override
        public ChatResponse doChat(ChatRequest request) {
            String text = replies.isEmpty() ? "APPROVE\nTESTS_PASSED" : replies.remove();
            return ChatResponse.builder().aiMessage(AiMessage.from(text)).build();
        }
    }
}
