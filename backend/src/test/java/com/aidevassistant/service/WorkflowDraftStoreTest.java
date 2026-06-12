package com.aidevassistant.service;

import com.aidevassistant.tools.SandboxContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowDraftStoreTest {

    @TempDir
    Path tempDir;

    private AgentToolsService tools;
    private WorkflowDraftStore drafts;
    private CodeIndexService index;

    @BeforeEach
    void setUp() {
        tools = new AgentToolsService(tempDir.toString());
        drafts = new WorkflowDraftStore();
        index = new CodeIndexService(null, false, 768, null);
        drafts.begin("wf-1", "runs/wf-1", true);
    }

    @Test
    void writeDoesNotTouchDiskUntilFlush() {
        String result = drafts.write("wf-1", tools, "src/app.py", "print('draft')\n");
        assertTrue(result.contains("draft — pending approval"), result);

        try (SandboxContext.Scope ignored = SandboxContext.scope("runs/wf-1")) {
            assertTrue(tools.readFile("src/app.py").startsWith("Error:"));
        }

        drafts.flush("wf-1", tools, index);

        try (SandboxContext.Scope ignored = SandboxContext.scope("runs/wf-1")) {
            assertTrue(tools.readFile("src/app.py").contains("print('draft')"));
        }
    }

    @Test
    void clearDraftsRemovesInMemoryContentWithoutDiskWrite() {
        drafts.write("wf-1", tools, "src/app.py", "print('draft')\n");
        drafts.clearDrafts("wf-1");

        assertNull(drafts.readDraftOnly("wf-1", "src/app.py"));
        try (SandboxContext.Scope ignored = SandboxContext.scope("runs/wf-1")) {
            assertTrue(tools.readFile("src/app.py").startsWith("Error:"));
        }
    }

    @Test
    void flushCallsSyncProjectIndexAfterWritingFiles() {
        TrackingIndex index = new TrackingIndex();
        drafts.write("wf-1", tools, "src/app.py", "print('draft')\n");
        drafts.flush("wf-1", tools, index);
        assertEquals(1, index.syncCalls);
        assertEquals("runs/wf-1", index.lastSandboxPath);
    }

    @Test
    void discardDoesNotDeleteDiskFiles() throws Exception {
        Path runDir = tempDir.resolve("runs/wf-1");
        Files.createDirectories(runDir);
        Files.writeString(runDir.resolve("leftover.txt"), "old");

        drafts.discard("wf-1", tools);

        assertTrue(Files.exists(runDir.resolve("leftover.txt")));
    }

    @Test
    void suspendKeepsDraftSessionActive() {
        drafts.write("wf-1", tools, "src/app.py", "print('draft')\n");
        drafts.suspend("wf-1");
        assertTrue(drafts.isActive("wf-1"));
        assertEquals("print('draft')\n", drafts.readDraftOnly("wf-1", "src/app.py"));
    }

    private static final class TrackingIndex extends CodeIndexService {
        int syncCalls;
        String lastSandboxPath;

        TrackingIndex() {
            super(null, true, 768, null);
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public int syncProjectIndex(String sandboxPath, AgentToolsService tools) {
            syncCalls++;
            lastSandboxPath = sandboxPath;
            return 2;
        }
    }
}
