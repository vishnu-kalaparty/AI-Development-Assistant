package com.aidevassistant.tools;

import com.aidevassistant.service.AgentToolsService;
import com.aidevassistant.service.ExecutionService;
import com.aidevassistant.service.WorkflowDraftStore;
import com.aidevassistant.service.WorkflowRunTracker;
import com.aidevassistant.service.WorkflowSnapshotStore;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SandboxToolsTest {

    @TempDir
    Path tempDir;

    private SandboxTools sandboxTools;
    private StepRecorder recorder;
    private AgentToolsService files;

    @BeforeEach
    void setUp() {
        files = new AgentToolsService(tempDir.toString());
        recorder = new StepRecorder(new WorkflowSnapshotStore());
        WorkflowRunContext.setWorkflowId("wf-sandbox");
        recorder.begin("wf-sandbox", "implementation");
        sandboxTools = new SandboxTools(files, new ExecutionService(files, 2), recorder, disabledIndex(), new WorkflowRunTracker(), inactiveDraftStore());
    }

    private static WorkflowDraftStore inactiveDraftStore() {
        return new WorkflowDraftStore();
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

    @Test
    void implementationToolNamesMatchPromptSnakeCase() {
        Set<String> names = ToolSpecifications.toolSpecificationsFrom(sandboxTools.tools()).stream()
                .map(ToolSpecification::name)
                .collect(Collectors.toSet());
        assertTrue(names.contains("write_file"), names.toString());
        assertTrue(names.contains("read_file"), names.toString());
        assertTrue(names.contains("list_files"), names.toString());
        assertTrue(names.contains("run_code"), names.toString());
        assertTrue(names.contains("semantic_search"), names.toString());
        assertTrue(names.contains("search_files"), names.toString());
        assertFalse(names.contains("writeFile"), names.toString());
    }

    @Test
    void implementationDelegatesToExistingServices() {
        var tools = sandboxTools.tools();
        String write = tools.writeFile("src/app.py", "print(1)");
        String read = tools.readFile("src/app.py");
        assertTrue(write.startsWith("Successfully"), write);
        assertTrue(read.contains("print(1)"), read);
        assertTrue(recorder.filesWritten().contains("src/app.py"));
    }

    @Test
    void mkdirCreatesNestedFolder() {
        String result = sandboxTools.tools().mkdir("src/main");
        assertTrue(result.startsWith("Successfully"), result);
        String listing = sandboxTools.tools().listFiles("", true);
        assertTrue(listing.contains("src/"), listing);
        assertTrue(listing.contains("src/main/"), listing);
    }

    @Test
    void toolsReturnsStableInstance() {
        assertSame(sandboxTools.tools(), sandboxTools.tools());
    }

    @Test
    void truncatesLongToolOutput() {
        files.writeFile("big.txt", "x".repeat(ToolOutputLimiter.MAX_CHARS + 80));
        String result = sandboxTools.tools().readFile("big.txt");
        assertTrue(result.endsWith("...truncated"));
        assertTrue(result.length() < ToolOutputLimiter.MAX_CHARS + 80);
    }

    @Test
    void toolsCanWrite() {
        String result = sandboxTools.tools().writeFile("test_app.py", "assert True");
        assertTrue(result.startsWith("Successfully"), result);
        assertTrue(files.readFile("test_app.py").contains("assert True"));
    }
}
