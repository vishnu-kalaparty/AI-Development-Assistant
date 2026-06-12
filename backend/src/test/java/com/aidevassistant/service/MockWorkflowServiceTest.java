package com.aidevassistant.service;

import com.aidevassistant.dto.WorkflowDecisionRequest;
import com.aidevassistant.dto.WorkflowRunResponse;
import com.aidevassistant.dto.WorkflowStartRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockWorkflowServiceTest {

    @TempDir
    Path tempDir;

    private MockWorkflowService mock;

    @BeforeEach
    void setUp() {
        mock = new MockWorkflowService(new AgentToolsService(tempDir.toString()), disabledIndex());
        ReflectionTestUtils.setField(mock, "phaseDelayMs", 50L);
        ReflectionTestUtils.setField(mock, "mockTickMs", 20L);
    }

    private static CodeIndexService disabledIndex() {
        return new CodeIndexService(null, false, 768, null);
    }

    @AfterEach
    void tearDown() {
        mock.shutdown();
    }

    @Test
    void syncRunProducesPhasesWithoutChatModel() {
        WorkflowStartRequest request = new WorkflowStartRequest();
        request.setTask("Write fizzbuzz.py");

        WorkflowRunResponse done = mock.startSync(request, true);

        assertEquals("awaiting_human", done.getStatus());
        assertTrue(done.isAwaitingHuman());
        assertEquals(6, done.getPhases().size());
        assertTrue(done.getPhases().stream().anyMatch(p -> "done".equals(p.getStatus())));
        assertTrue(done.getPhases().stream().anyMatch(p -> "waiting".equals(p.getStatus())));
        assertTrue(done.getRequirementsDoc().contains("Requirements"));
        assertTrue(done.getArchitectureDoc().contains("Architecture"));
        assertTrue(done.getTestReport().toLowerCase().contains("pass"));
        assertEquals("APPROVE", done.getReviewVerdict());
        assertFalse(done.getSteps().isEmpty());
        assertTrue(done.getSandboxFiles().stream().anyMatch(f -> f.contains("/")));
    }

    @Test
    void asyncRunAdvancesUntilAwaitingHuman() throws Exception {
        WorkflowStartRequest request = new WorkflowStartRequest();
        request.setTask("Write fizzbuzz.py");

        WorkflowRunResponse started = mock.start(request, true);
        assertEquals("running", started.getStatus());

        WorkflowRunResponse latest = started;
        for (int i = 0; i < 80; i++) {
            Thread.sleep(100);
            latest = mock.get(started.getWorkflowId());
            if (!"running".equals(latest.getStatus())) {
                break;
            }
        }

        assertEquals("awaiting_human", latest.getStatus());
        assertTrue(latest.getPhases().stream().filter(p -> !"approval".equals(p.getId()))
                .allMatch(p -> "done".equals(p.getStatus())));
    }

    @Test
    void approveCompletesMockRun() {
        WorkflowStartRequest request = new WorkflowStartRequest();
        request.setTask("demo");
        WorkflowRunResponse started = mock.startSync(request, true);

        WorkflowDecisionRequest decision = new WorkflowDecisionRequest();
        decision.setApproved(true);
        WorkflowRunResponse finished = mock.decide(started.getWorkflowId(), decision);

        assertEquals("completed", finished.getStatus());
        assertFalse(finished.isAwaitingHuman());
    }

    @Test
    void customFolderNameUsesLogicalPathWithoutDiskWrite() {
        WorkflowStartRequest request = new WorkflowStartRequest();
        request.setTask("Write fizzbuzz.py");
        request.setFolderName("fizzbuzz-demo");

        WorkflowRunResponse awaiting = mock.startSync(request, true);
        assertEquals("runs/fizzbuzz-demo", awaiting.getSandboxPath());
        assertFalse(Files.exists(tempDir.resolve("runs/fizzbuzz-demo")));
    }

    @Test
    void blankParentFolderUsesLogicalRunPathWithoutCreatingDirectory() throws Exception {
        WorkflowStartRequest request = new WorkflowStartRequest();
        request.setTask("demo");

        WorkflowRunResponse started = mock.start(request, true);

        assertTrue(started.getSandboxPath().startsWith("runs/"));
        Path runDir = tempDir.resolve(started.getSandboxPath());
        assertFalse(Files.exists(runDir), "Run folder must not exist until approval");
    }

    @Test
    void awaitingHumanDoesNotWriteFilesUntilApproved() {
        WorkflowStartRequest request = new WorkflowStartRequest();
        request.setTask("Write fizzbuzz.py");

        WorkflowRunResponse awaiting = mock.startSync(request, true);
        assertEquals("awaiting_human", awaiting.getStatus());
        assertTrue(awaiting.getSandboxFiles().contains("src/fizzbuzz.py"));

        List<String> onDisk = new AgentToolsService(tempDir.toString()).listFilePaths(awaiting.getSandboxPath(), true);
        assertTrue(onDisk.isEmpty(), onDisk.toString());

        WorkflowDecisionRequest decision = new WorkflowDecisionRequest();
        decision.setApproved(true);
        WorkflowRunResponse approved = mock.decide(awaiting.getWorkflowId(), decision);

        assertEquals("completed", approved.getStatus());
        List<String> afterApprove = new AgentToolsService(tempDir.toString())
                .listFilePaths(approved.getSandboxPath(), true);
        assertTrue(afterApprove.contains("src/fizzbuzz.py"), afterApprove.toString());
        assertTrue(afterApprove.contains("tests/test_fizzbuzz.py"), afterApprove.toString());
    }

    @Test
    void rejectDoesNotFlushDraftsToDisk() {
        WorkflowStartRequest request = new WorkflowStartRequest();
        request.setTask("Write fizzbuzz.py");

        WorkflowRunResponse awaiting = mock.startSync(request, true);
        assertEquals("awaiting_human", awaiting.getStatus());

        WorkflowDecisionRequest decision = new WorkflowDecisionRequest();
        decision.setApproved(false);
        decision.setNotes("rename the file");
        WorkflowRunResponse rejected = mock.decide(awaiting.getWorkflowId(), decision);

        assertEquals("running", rejected.getStatus());
        List<String> onDisk = new AgentToolsService(tempDir.toString())
                .listFilePaths(awaiting.getSandboxPath(), true);
        assertTrue(onDisk.isEmpty(), onDisk.toString());
        assertNull(mock.readDraft(awaiting.getWorkflowId(), "src/fizzbuzz.py"));
    }

    @Test
    void readDraftReturnsInMemoryContentBeforeApproval() {
        WorkflowStartRequest request = new WorkflowStartRequest();
        request.setTask("Write fizzbuzz.py");

        WorkflowRunResponse awaiting = mock.startSync(request, true);
        String draft = mock.readDraft(awaiting.getWorkflowId(), "src/fizzbuzz.py");

        assertTrue(draft != null && draft.contains("fizzbuzz"));
    }

    @Test
    void blankParentFolderSeedsNestedFizzbuzzFiles() {
        WorkflowStartRequest request = new WorkflowStartRequest();
        request.setTask("Write fizzbuzz.py");

        WorkflowRunResponse done = mock.startSync(request, false);

        String sandboxPath = done.getSandboxPath();
        assertTrue(sandboxPath.startsWith("runs/"));

        List<String> onDisk = new AgentToolsService(tempDir.toString()).listFilePaths(sandboxPath, true);
        assertTrue(onDisk.contains("src/fizzbuzz.py"), onDisk.toString());
        assertTrue(onDisk.contains("tests/test_fizzbuzz.py"), onDisk.toString());

        assertTrue(done.getSandboxFiles().contains("src/fizzbuzz.py"));
        assertTrue(done.getSandboxFiles().contains("tests/test_fizzbuzz.py"));
    }

    @Test
    void existingFolderKeepsDraftsUntilApproved() throws Exception {
        AgentToolsService tools = new AgentToolsService(tempDir.toString());
        tools.writeFile("my-app/readme.md", "# existing");

        MockWorkflowService scoped = new MockWorkflowService(tools, disabledIndex());
        ReflectionTestUtils.setField(scoped, "phaseDelayMs", 150L);

        WorkflowStartRequest request = new WorkflowStartRequest();
        request.setTask("extend app");
        request.setParentFolder("my-app");

        WorkflowRunResponse awaiting = scoped.startSync(request, true);
        assertEquals("my-app", awaiting.getSandboxPath());
        assertTrue(awaiting.getSandboxFiles().contains("readme.md"));
        assertTrue(awaiting.getSandboxFiles().contains("src/fizzbuzz.py"));

        List<String> onDisk = tools.listFilePaths("my-app", true);
        assertTrue(onDisk.contains("readme.md"), onDisk.toString());
        assertFalse(onDisk.contains("src/fizzbuzz.py"), onDisk.toString());

        WorkflowDecisionRequest decision = new WorkflowDecisionRequest();
        decision.setApproved(true);
        WorkflowRunResponse approved = scoped.decide(awaiting.getWorkflowId(), decision);

        assertEquals("completed", approved.getStatus());
        List<String> afterApprove = tools.listFilePaths("my-app", true);
        assertTrue(afterApprove.contains("readme.md"), afterApprove.toString());
        assertTrue(afterApprove.contains("src/fizzbuzz.py"), afterApprove.toString());
        scoped.shutdown();
    }

    @Test
    void providedParentFolderUsesExistingSandbox() throws Exception {
        AgentToolsService tools = new AgentToolsService(tempDir.toString());
        tools.writeFile("my-app/readme.md", "# existing");

        MockWorkflowService scoped = new MockWorkflowService(tools, disabledIndex());
        ReflectionTestUtils.setField(scoped, "phaseDelayMs", 150L);

        WorkflowStartRequest request = new WorkflowStartRequest();
        request.setTask("extend app");
        request.setParentFolder("my-app");

        WorkflowRunResponse started = scoped.startSync(request, false);

        assertEquals("my-app", started.getSandboxPath());
        assertTrue(started.getSandboxFiles().contains("readme.md"));
        scoped.shutdown();
    }
}
