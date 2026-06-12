package com.aidevassistant.tools;

import com.aidevassistant.service.AgentToolsService;
import com.aidevassistant.service.ExecutionService;
import com.aidevassistant.service.WorkflowDraftStore;
import com.aidevassistant.service.WorkflowRunTracker;
import com.aidevassistant.service.WorkflowSnapshotStore;
import com.aidevassistant.tools.SandboxContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SandboxToolsDraftTest {

    @TempDir
    Path tempDir;

    private AgentToolsService files;
    private WorkflowDraftStore drafts;
    private SandboxTools sandboxTools;

    @BeforeEach
    void setUp() {
        files = new AgentToolsService(tempDir.toString());
        drafts = new WorkflowDraftStore();
        drafts.begin("wf-draft", "runs/wf-draft", true);
        StepRecorder recorder = new StepRecorder(new WorkflowSnapshotStore());
        WorkflowRunContext.setWorkflowId("wf-draft");
        recorder.begin("wf-draft", "implementation");
        sandboxTools = new SandboxTools(
                files,
                new ExecutionService(files, 2),
                recorder,
                new com.aidevassistant.service.CodeIndexService(null, false, 768, null),
                new WorkflowRunTracker(),
                drafts
        );
    }

    @Test
    void writeStaysInDraftUntilFlush() {
        var tools = sandboxTools.tools();
        String write = tools.writeFile("src/app.py", "print('pending')\n");
        assertTrue(write.contains("pending approval"), write);
        try (SandboxContext.Scope ignored = SandboxContext.scope("runs/wf-draft")) {
            assertTrue(files.readFile("src/app.py").startsWith("Error:"));
        }
    }
}
