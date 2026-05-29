package com.aidevassistant.service;

import com.aidevassistant.dto.AgentStepDTO;
import com.aidevassistant.dto.DraftFileEntryDTO;
import com.aidevassistant.dto.FileResponse;
import com.aidevassistant.dto.WorkflowDecisionRequest;
import com.aidevassistant.dto.WorkflowRunResponse;
import com.aidevassistant.dto.WorkflowStartRequest;
import com.aidevassistant.graph.SdlcPhases;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.aidevassistant.tools.SandboxContext;

/**
 * Fake SDLC progression for local demos without Gemini.
 * Advances phases on a timer so clients can poll GET /api/workflow/{id}.
 */
@Service
public class MockWorkflowService implements WorkflowEngine {

    private static final List<String> PHASE_ORDER = List.of(
            "requirements", "architecture", "implementation", "testing", "review", "approval"
    );

    private static final String MOCK_FIZZBUZZ_PY = """
            def fizzbuzz(n=20):
                for i in range(1, n + 1):
                    if i % 15 == 0:
                        print("FizzBuzz")
                    elif i % 3 == 0:
                        print("Fizz")
                    elif i % 5 == 0:
                        print("Buzz")
                    else:
                        print(i)


            if __name__ == "__main__":
                fizzbuzz()
            """;

    private static final String MOCK_TEST_FIZZBUZZ_PY = """
            import subprocess
            import sys


            def test_fizzbuzz_runs():
                result = subprocess.run(
                    [sys.executable, "src/fizzbuzz.py"],
                    capture_output=True,
                    text=True,
                )
                assert result.returncode == 0
                lines = result.stdout.strip().splitlines()
                assert lines[14] == "FizzBuzz"
            """;

    private static final String MOCK_README_MD = """
            # FizzBuzz — folder layout

            Pattern: CLI script (mock).

            ```
            docs/README.md
            src/fizzbuzz.py
            tests/test_fizzbuzz.py
            ```

            ## Run
            - `run_code` on `src/fizzbuzz.py`

            ## Test
            - `run_code` on `tests/test_fizzbuzz.py`
            """;

    private final Map<String, WorkflowRunResponse> runs = new ConcurrentHashMap<>();
    private final Map<String, String> tasks = new ConcurrentHashMap<>();
    private final Map<String, String> sandboxPaths = new ConcurrentHashMap<>();
    /** Draft file contents held in memory until human approval (or auto-complete). */
    private final Map<String, Map<String, String>> pendingDrafts = new ConcurrentHashMap<>();
    private final AgentToolsService agentToolsService;
    private final CodeIndexService codeIndexService;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "mock-sdlc");
        t.setDaemon(true);
        return t;
    });

    @Value("${agent.sdlc.mock-phase-ms:1200}")
    private long phaseDelayMs;

    @Value("${agent.sdlc.mock-tick-ms:220}")
    private long mockTickMs;

    public MockWorkflowService(AgentToolsService agentToolsService, CodeIndexService codeIndexService) {
        this.agentToolsService = agentToolsService;
        this.codeIndexService = codeIndexService;
    }

    @Override
    public WorkflowRunResponse start(WorkflowStartRequest request, boolean awaitHuman) {
        if (request == null || request.getTask() == null || request.getTask().isBlank()) {
            throw new IllegalArgumentException("Task is required");
        }
        String workflowId = UUID.randomUUID().toString();
        String task = request.getTask().trim();
        String sandboxPath = resolveSandboxPath(request, workflowId);
        WorkflowRunResponse response = blankRun(workflowId, sandboxPath);
        tasks.put(workflowId, task);
        sandboxPaths.put(workflowId, sandboxPath);
        runs.put(workflowId, response);
        scheduler.schedule(() -> advance(workflowId, 0, awaitHuman), Math.max(200, phaseDelayMs / 3), TimeUnit.MILLISECONDS);
        return copy(response);
    }

      @Override
    public WorkflowRunResponse startSync(WorkflowStartRequest request, boolean awaitHuman) {
        if (request == null || request.getTask() == null || request.getTask().isBlank()) {
            throw new IllegalArgumentException("Task is required");
        }
        String workflowId = UUID.randomUUID().toString();
        String task = request.getTask().trim();
        String sandboxPath = resolveSandboxPath(request, workflowId);
        WorkflowRunResponse response = blankRun(workflowId, sandboxPath);
        tasks.put(workflowId, task);
        sandboxPaths.put(workflowId, sandboxPath);
        runs.put(workflowId, response);
        WorkflowRunResponse run = response;
        for (int i = 0; i < PHASE_ORDER.size(); i++) {
            String phase = PHASE_ORDER.get(i);
            run.setCurrentPhase(phase);
            for (int tick = 0; tick < ticksForPhase(phase); tick++) {
                applyPhaseTick(run, phase, tick);
            }
            run.setPhases(SdlcPhases.forMock(run, phase));
            if ("approval".equals(phase)) {
                if (awaitHuman) {
                    run.setStatus("awaiting_human");
                    run.setAwaitingHuman(true);
                    run.setSummary("Mock review complete. Waiting for approval.");
                    run.setPhases(SdlcPhases.forMock(run, "approval"));
                } else {
                    flushDraftFiles(workflowId, run.getSandboxPath());
                    run.setSandboxFiles(listSandboxFiles(run.getSandboxPath()));
                    run.setStatus("completed");
                    run.setAwaitingHuman(false);
                    run.setCurrentPhase("complete");
                    run.setSummary("Mock run completed without human approval.");
                    run.setPhases(SdlcPhases.forMock(run, "complete"));
                }
            }
            if (!"running".equals(run.getStatus())) {
                break;
            }
        }
        return copy(runs.get(workflowId));
    }

    @Override
    public WorkflowRunResponse get(String workflowId) {
        WorkflowRunResponse run = runs.get(workflowId);
        if (run == null) {
            throw new IllegalArgumentException("Unknown workflow: " + workflowId);
        }
        synchronized (run) {
            return copy(run);
        }
    }

    @Override
    public WorkflowRunResponse decide(String workflowId, WorkflowDecisionRequest request) {
        WorkflowRunResponse run = runs.get(workflowId);
        if (run == null) {
            throw new IllegalArgumentException("Unknown workflow: " + workflowId);
        }
        synchronized (run) {
            if (!"awaiting_human".equals(run.getStatus())) {
                return copy(run);
            }
            boolean approved = request != null && request.isApproved();
            String notes = request == null || request.getNotes() == null ? "" : request.getNotes();
            if (approved) {
                flushDraftFiles(workflowId, run.getSandboxPath());
                run.setSandboxFiles(listSandboxFiles(run.getSandboxPath()));
                run.setStatus("completed");
                run.setAwaitingHuman(false);
                run.setCurrentPhase("complete");
                run.setSummary("Mock run approved." + (notes.isBlank() ? "" : " Notes: " + notes));
                run.setPhases(SdlcPhases.forMock(run, "complete"));
                return copy(run);
            }
            discardPendingRun(workflowId, run.getSandboxPath());
            run.setSandboxFiles(listSandboxFiles(run.getSandboxPath()));
            run.setStatus("running");
            run.setAwaitingHuman(false);
            run.setCurrentPhase("implementation");
            run.setReviewNotes(notes.isBlank() ? "Human requested changes (mock)." : notes);
            run.setReviewVerdict("CHANGES_REQUESTED");
            run.setSummary("Revising after human feedback (mock)…");
                run.setPhases(SdlcPhases.forMock(run, "implementation"));
            scheduler.schedule(() -> finishRevision(workflowId), Math.max(400, phaseDelayMs), TimeUnit.MILLISECONDS);
            return copy(run);
        }
    }

    @Override
    public boolean knows(String workflowId) {
        return runs.containsKey(workflowId);
    }

    @Override
    public WorkflowRunResponse cancel(String workflowId) {
        WorkflowRunResponse run = runs.get(workflowId);
        if (run == null) {
            throw new IllegalArgumentException("Unknown workflow: " + workflowId);
        }
        synchronized (run) {
            run.setStatus("cancelled");
            run.setAwaitingHuman(false);
            run.setCurrentPhase("complete");
            run.setSummary("Stopped by user (mock).");
            run.setPhases(SdlcPhases.forMock(run, "complete"));
            return copy(run);
        }
    }

    /** Reads in-memory draft content for a workflow awaiting approval. */
    public String readDraft(String workflowId, String relativePath) {
        if (workflowId == null || relativePath == null || relativePath.isBlank()) {
            return null;
        }
        Map<String, String> drafts = pendingDrafts.get(workflowId);
        if (drafts == null || drafts.isEmpty()) {
            return null;
        }
        String normalized = relativePath.replace('\\', '/').replaceAll("^/+", "");
        if (drafts.containsKey(normalized)) {
            return drafts.get(normalized);
        }
        int slash = normalized.indexOf('/');
        while (slash > 0) {
            normalized = normalized.substring(slash + 1);
            if (drafts.containsKey(normalized)) {
                return drafts.get(normalized);
            }
            slash = normalized.indexOf('/');
        }
        return null;
    }

    @Override
    public FileResponse readDraftCompare(String workflowId, String relativePath) {
        String draft = readDraft(workflowId, relativePath);
        if (draft == null) {
            return null;
        }
        String normalized = relativePath.replace('\\', '/').replaceAll("^/+", "");
        String baseline = null;
        String status = "new";
        String sandboxPath = sandboxPaths.get(workflowId);
        if (sandboxPath != null && !sandboxPath.isBlank()) {
            try (SandboxContext.Scope ignored = SandboxContext.scope(sandboxPath)) {
                String disk = agentToolsService.readFile(normalized);
                if (disk != null && !disk.startsWith("Error:") && !disk.startsWith("Security Error:")) {
                    baseline = disk;
                    status = disk.equals(draft) ? "unchanged" : "modified";
                }
            }
        }
        return new FileResponse(draft, "Draft read successfully", baseline, status);
    }

    @Override
    public List<DraftFileEntryDTO> draftManifest(String workflowId) {
        Map<String, String> drafts = pendingDrafts.get(workflowId);
        if (drafts == null || drafts.isEmpty()) {
            return List.of();
        }
        String sandboxPath = sandboxPaths.get(workflowId);
        List<DraftFileEntryDTO> entries = new ArrayList<>();
        if (sandboxPath != null && !sandboxPath.isBlank()) {
            try (SandboxContext.Scope ignored = SandboxContext.scope(sandboxPath)) {
                for (Map.Entry<String, String> entry : drafts.entrySet()) {
                    String path = entry.getKey();
                    String draft = entry.getValue();
                    String disk = agentToolsService.readFile(path);
                    boolean onDisk = disk != null && !disk.startsWith("Error:") && !disk.startsWith("Security Error:");
                    String status = !onDisk ? "new" : (disk.equals(draft) ? "unchanged" : "modified");
                    entries.add(new DraftFileEntryDTO(path, status));
                }
            }
        } else {
            for (String path : drafts.keySet()) {
                entries.add(new DraftFileEntryDTO(path, "new"));
            }
        }
        entries.sort(Comparator.comparing(DraftFileEntryDTO::getPath));
        return entries;
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }

    private void advance(String workflowId, int phaseIndex, boolean awaitHuman) {
        WorkflowRunResponse run = runs.get(workflowId);
        if (run == null || !"running".equals(run.getStatus()) || phaseIndex >= PHASE_ORDER.size()) {
            return;
        }
        synchronized (run) {
            String phase = PHASE_ORDER.get(phaseIndex);
            run.setCurrentPhase(phase);
            run.setSummary("Starting " + phase + "…");
            run.setPhases(SdlcPhases.forMock(run, phase));
        }
        schedulePhaseTick(workflowId, phaseIndex, awaitHuman, 0);
    }

    private void schedulePhaseTick(String workflowId, int phaseIndex, boolean awaitHuman, int tick) {
        WorkflowRunResponse run = runs.get(workflowId);
        if (run == null || !"running".equals(run.getStatus()) || phaseIndex >= PHASE_ORDER.size()) {
            return;
        }
        String phase = PHASE_ORDER.get(phaseIndex);
        int maxTicks = ticksForPhase(phase);
        synchronized (run) {
            applyPhaseTick(run, phase, tick);
            run.setPhases(SdlcPhases.forMock(run, phase));
        }
        if (tick + 1 < maxTicks) {
            scheduler.schedule(
                    () -> schedulePhaseTick(workflowId, phaseIndex, awaitHuman, tick + 1),
                    mockTickMs,
                    TimeUnit.MILLISECONDS
            );
        } else {
            scheduler.schedule(
                    () -> finishPhase(workflowId, phaseIndex, awaitHuman),
                    Math.max(120, mockTickMs / 2),
                    TimeUnit.MILLISECONDS
            );
        }
    }

    private void finishPhase(String workflowId, int phaseIndex, boolean awaitHuman) {
        WorkflowRunResponse run = runs.get(workflowId);
        if (run == null) {
            return;
        }
        synchronized (run) {
            String phase = PHASE_ORDER.get(phaseIndex);
            if ("approval".equals(phase)) {
                if (awaitHuman) {
                    run.setStatus("awaiting_human");
                    run.setAwaitingHuman(true);
                    run.setSummary("Mock review complete. Waiting for approval.");
                    run.setPhases(SdlcPhases.forMock(run, "approval"));
                } else {
                    flushDraftFiles(workflowId, run.getSandboxPath());
                    run.setSandboxFiles(listSandboxFiles(run.getSandboxPath()));
                    run.setStatus("completed");
                    run.setAwaitingHuman(false);
                    run.setCurrentPhase("complete");
                    run.setSummary("Mock run completed without human approval.");
                    run.setPhases(SdlcPhases.forMock(run, "complete"));
                }
                return;
            }
        }
        if (phaseIndex + 1 < PHASE_ORDER.size()) {
            scheduler.schedule(
                    () -> advance(workflowId, phaseIndex + 1, awaitHuman),
                    Math.max(300, phaseDelayMs),
                    TimeUnit.MILLISECONDS
            );
        }
    }

    private static int ticksForPhase(String phase) {
        return switch (phase) {
            case "implementation" -> 5;
            case "requirements", "architecture" -> 4;
            default -> 3;
        };
    }

    private void finishRevision(String workflowId) {
        WorkflowRunResponse run = runs.get(workflowId);
        if (run == null) {
            return;
        }
        synchronized (run) {
            Map<String, String> drafts = pendingDrafts.computeIfAbsent(workflowId, id -> new LinkedHashMap<>());
            drafts.put("src/fizzbuzz.py", MOCK_FIZZBUZZ_PY + "\n# revised per human feedback (mock)\n");
            String sandboxPath = sandboxPaths.getOrDefault(workflowId, run.getSandboxPath());
            run.setSandboxFiles(mergeSandboxListing(sandboxPath, drafts.keySet()));
            run.getSteps().add(new AgentStepDTO(
                    "implementation",
                    run.getSteps().size() + 1,
                    "Applying human revision notes",
                    "write_file",
                    "src/fizzbuzz.py",
                    "Successfully wrote src/fizzbuzz.py: 44 lines (was 42, +2) · 920 chars"
            ));
            run.setTestReport("Revision re-test (mock):\n- test_range … PASS\n- test_fizz … PASS\nAll tests passed.");
            run.setTestsPassed(true);
            run.setReviewVerdict("APPROVE");
            run.setReviewNotes("Changes look good after revision (mock).");
            run.setStatus("awaiting_human");
            run.setAwaitingHuman(true);
            run.setCurrentPhase("approval");
            run.setSummary("Revision complete. Waiting for approval again.");
            run.setPhases(SdlcPhases.forMock(run, "approval"));
        }
    }

    private void applyPhaseTick(WorkflowRunResponse run, String phase, int tick) {
        String task = tasks.getOrDefault(run.getWorkflowId(), "demo task");
        String taskSnippet = task.length() > 48 ? task.substring(0, 48) + "…" : task;
        switch (phase) {
            case "requirements" -> {
                String fullThought = "Reviewing scope and constraints for: " + taskSnippet;
                String fullObs = "Captured goals and acceptance criteria.";
                String fullDoc = """
                        # Requirements (mock)

                        Task: %s

                        ## Goals
                        - Deliver a small runnable program in the sandbox
                        - Cover happy-path behavior with automated checks

                        ## Acceptance criteria
                        - Program runs without errors
                        - Output matches the stated rules
                        """.formatted(task);
                switch (tick) {
                    case 0 -> {
                        run.setSummary("Analyzing task…");
                        upsertModelStep(run, "requirements", 1, partial(fullThought, 1, 4), "");
                    }
                    case 1 -> upsertModelStep(run, "requirements", 1, fullThought, partial(fullObs, 1, 2));
                    case 2 -> {
                        run.setRequirementsDoc(partial(fullDoc, 2, 3));
                        upsertModelStep(run, "requirements", 1, fullThought, fullObs);
                        run.setSummary("Drafting requirements…");
                    }
                    default -> {
                        run.setRequirementsDoc(fullDoc);
                        upsertModelStep(run, "requirements", 1, fullThought, fullObs);
                        run.setSummary("Drafted requirements (mock).");
                    }
                }
            }
            case "architecture" -> {
                String fullThought = "Sketching CLI module layout with docs/README.md and tests.";
                String fullObs = "Chose CLI layout with docs/README.md.";
                String fullDoc = """
                        # Architecture (mock)

                        ## Pattern
                        CLI script — single runnable module with unit tests (not MVC; no web UI).

                        ## File tree
                        ```
                        docs/README.md
                        src/fizzbuzz.py
                        tests/test_fizzbuzz.py
                        ```

                        ## File plan
                        - `docs/README.md` — canonical layout and run instructions
                        - `src/fizzbuzz.py` — main program
                        - `tests/test_fizzbuzz.py` — unit checks

                        ## Run & test
                        - Run: `run_code` on `src/fizzbuzz.py`
                        - Test: `run_code` on `tests/test_fizzbuzz.py`
                        """;
                switch (tick) {
                    case 0 -> {
                        run.setSummary("Designing structure…");
                        upsertModelStep(run, "architecture", 1, partial(fullThought, 1, 4), "");
                    }
                    case 1 -> upsertModelStep(run, "architecture", 1, fullThought, "");
                    case 2 -> {
                        run.setArchitectureDoc(partial(fullDoc, 2, 3));
                        upsertModelStep(run, "architecture", 1, fullThought, partial(fullObs, 1, 2));
                        run.setSummary("Outlining architecture…");
                    }
                    default -> {
                        run.setArchitectureDoc(fullDoc);
                        upsertModelStep(run, "architecture", 1, fullThought, fullObs);
                        run.setSummary("Outlined architecture (mock).");
                    }
                }
            }
            case "implementation" -> {
                String planThought = "Planning implementation: README, main module, and tests.";
                String writeThought = "Write program";
                String writeObs = "Successfully wrote src/fizzbuzz.py: 42 lines (new file) · 890 chars";
                String runThought = "Smoke run";
                String runObs = "1\n2\nFizz\n4\nBuzz\n…\nFizzBuzz";
                switch (tick) {
                    case 0 -> {
                        run.setSummary("Planning implementation…");
                        upsertModelStep(run, "implementation", 1, partial(planThought, 1, 3), "");
                    }
                    case 1 -> {
                        upsertModelStep(run, "implementation", 1, planThought, "Staging sandbox files…");
                        upsertToolStep(run, "implementation", 2, writeThought, "write_file", "src/fizzbuzz.py",
                                partial(writeObs, 1, 2));
                        run.setSummary("Writing src/fizzbuzz.py…");
                    }
                    case 2 -> {
                        upsertToolStep(run, "implementation", 2, writeThought, "write_file", "src/fizzbuzz.py", writeObs);
                        upsertToolStep(run, "implementation", 3, runThought, "run_code", "src/fizzbuzz.py",
                                partial(runObs, 1, 2));
                        run.setSummary("Running smoke test…");
                    }
                    case 3 -> {
                        stageMockDraftFiles(run);
                        upsertToolStep(run, "implementation", 3, runThought, "run_code", "src/fizzbuzz.py", runObs);
                        run.setSummary("Verifying output…");
                    }
                    default -> {
                        stageMockDraftFiles(run);
                        upsertModelStep(run, "implementation", 1, planThought, "Files staged in sandbox (mock).");
                        upsertToolStep(run, "implementation", 2, writeThought, "write_file", "src/fizzbuzz.py", writeObs);
                        upsertToolStep(run, "implementation", 3, runThought, "run_code", "src/fizzbuzz.py", runObs);
                        run.setSummary("Implemented sandbox files (mock).");
                    }
                }
            }
            case "testing" -> {
                String fullThought = "Running automated checks against tests/test_fizzbuzz.py.";
                String fullObs = "4 passed (mock)";
                String fullReport = """
                        Test report (mock)
                        ==================
                        test_range ............... PASS
                        test_fizz ................ PASS
                        test_buzz ................ PASS
                        test_fizzbuzz ............ PASS

                        4 passed in 0.02s
                        """;
                switch (tick) {
                    case 0 -> {
                        run.setSummary("Preparing tests…");
                        upsertModelStep(run, "testing", 1, partial(fullThought, 1, 3), "");
                    }
                    case 1 -> {
                        upsertModelStep(run, "testing", 1, fullThought, "Executing test suite…");
                        upsertToolStep(run, "testing", 2, "Run tests", "run_code", "tests/test_fizzbuzz.py",
                                partial(fullObs, 1, 2));
                        run.setTestReport(partial(fullReport, 1, 2));
                        run.setSummary("Running tests…");
                    }
                    default -> {
                        run.setTestReport(fullReport);
                        run.setTestsPassed(true);
                        upsertModelStep(run, "testing", 1, fullThought, fullObs);
                        upsertToolStep(run, "testing", 2, "Run tests", "run_code", "tests/test_fizzbuzz.py", fullObs);
                        run.setSummary("Tests passed (mock).");
                    }
                }
            }
            case "review" -> {
                String fullThought = "Reviewing sandbox layout and comparing against requirements.";
                String fullObs = "src/fizzbuzz.py";
                String fullNotes = "Code matches requirements. Nested sandbox layout is clear. Recommend approve (mock).";
                switch (tick) {
                    case 0 -> {
                        run.setSummary("Starting review…");
                        upsertModelStep(run, "review", 1, partial(fullThought, 1, 3), "");
                    }
                    case 1 -> {
                        upsertModelStep(run, "review", 1, fullThought, "Scanning files…");
                        upsertToolStep(run, "review", 2, "Review sandbox", "list_files", "src/", partial(fullObs, 1, 2));
                        run.setSummary("Reviewing files…");
                    }
                    default -> {
                        run.setReviewVerdict("APPROVE");
                        run.setReviewNotes(fullNotes);
                        upsertModelStep(run, "review", 1, fullThought, fullNotes);
                        upsertToolStep(run, "review", 2, "Review sandbox", "list_files", "src/", fullObs);
                        run.setSummary("Review approved (mock).");
                    }
                }
            }
            case "approval" -> {
                switch (tick) {
                    case 0 -> run.setSummary("Packaging deliverables…");
                    case 1 -> run.setSummary("Ready for human approval…");
                    default -> run.setSummary("Awaiting approval…");
                }
            }
            default -> {
            }
        }
    }

    private static String partial(String text, int segment, int segments) {
        if (text == null || text.isEmpty() || segments <= 0) {
            return text == null ? "" : text;
        }
        int end = Math.min(text.length(), (text.length() * segment) / segments);
        end = Math.max(end, segment > 0 ? 1 : 0);
        return text.substring(0, end);
    }

    private static AgentStepDTO lastStep(WorkflowRunResponse run) {
        List<AgentStepDTO> steps = run.getSteps();
        if (steps == null || steps.isEmpty()) {
            return null;
        }
        return steps.get(steps.size() - 1);
    }

    private static void upsertModelStep(
            WorkflowRunResponse run,
            String stage,
            int stepNum,
            String thought,
            String observation
    ) {
        AgentStepDTO last = lastStep(run);
        if (last != null && "model".equals(last.getToolName()) && stage.equals(last.getStage())) {
            last.setThought(thought);
            last.setObservation(observation);
            return;
        }
        run.getSteps().add(step(stage, stepNum, thought, "model", "", observation));
    }

    private static void upsertToolStep(
            WorkflowRunResponse run,
            String stage,
            int stepNum,
            String thought,
            String tool,
            String args,
            String observation
    ) {
        AgentStepDTO last = lastStep(run);
        if (last != null && tool.equals(last.getToolName()) && stage.equals(last.getStage())
                && stepNum == last.getStep()) {
            last.setThought(thought);
            last.setToolArgs(args);
            last.setObservation(observation);
            return;
        }
        run.getSteps().add(step(stage, stepNum, thought, tool, args, observation));
    }

    private static WorkflowRunResponse blankRun(String workflowId, String sandboxPath) {
        WorkflowRunResponse response = new WorkflowRunResponse();
        response.setWorkflowId(workflowId);
        response.setSandboxPath(sandboxPath);
        response.setStatus("running");
        response.setCurrentPhase("requirements");
        response.setSummary("Starting mock SDLC run…");
        response.setAwaitingHuman(false);
        response.setTestsPassed(false);
        response.setPhases(SdlcPhases.forMock(response, "requirements"));
        response.setSteps(new ArrayList<>());
        response.setFilesWritten(new ArrayList<>());
        response.setSandboxFiles(new ArrayList<>());
        return response;
    }

    private static AgentStepDTO step(
            String stage,
            int n,
            String thought,
            String tool,
            String args,
            String observation
    ) {
        return new AgentStepDTO(stage, n, thought, tool, args, observation);
    }

    private List<String> listSandboxFiles(String sandboxPath) {
        if (sandboxPath == null || sandboxPath.isBlank()) {
            return List.of();
        }
        try {
            return agentToolsService.listFilePaths(sandboxPath, true);
        } catch (Exception e) {
            return List.of();
        }
    }

    private void stageMockDraftFiles(WorkflowRunResponse run) {
        Map<String, String> drafts = new LinkedHashMap<>();
        drafts.put("docs/README.md", MOCK_README_MD);
        drafts.put("src/fizzbuzz.py", MOCK_FIZZBUZZ_PY);
        drafts.put("tests/test_fizzbuzz.py", MOCK_TEST_FIZZBUZZ_PY);
        pendingDrafts.put(run.getWorkflowId(), drafts);
        run.setFilesWritten(List.of("docs/README.md", "src/fizzbuzz.py", "tests/test_fizzbuzz.py"));
        run.setSandboxFiles(mergeSandboxListing(run.getSandboxPath(), drafts.keySet()));
    }

    private List<String> mergeSandboxListing(String sandboxPath, Collection<String> draftPaths) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        merged.addAll(listSandboxFiles(sandboxPath));
        merged.addAll(draftPaths);
        return new ArrayList<>(merged);
    }

    private void flushDraftFiles(String workflowId, String sandboxPath) {
        Map<String, String> drafts = pendingDrafts.remove(workflowId);
        if (drafts == null || drafts.isEmpty() || sandboxPath == null || sandboxPath.isBlank()) {
            return;
        }
        ensureSandboxDirectory(sandboxPath);
        int filesWritten = 0;
        try (SandboxContext.Scope ignored = SandboxContext.scope(sandboxPath)) {
            for (Map.Entry<String, String> entry : drafts.entrySet()) {
                String path = entry.getKey();
                int slash = path.lastIndexOf('/');
                if (slash > 0) {
                    agentToolsService.mkdir(path.substring(0, slash));
                }
                String result = agentToolsService.writeFile(path, entry.getValue());
                if (result.startsWith("Successfully wrote") || result.startsWith("Wrote")) {
                    filesWritten++;
                }
            }
        }
        if (filesWritten > 0 && codeIndexService.isEnabled()) {
            codeIndexService.syncProjectIndex(sandboxPath, agentToolsService);
        }
    }

    private String resolveSandboxPath(WorkflowStartRequest request, String workflowId) {
        try {
            return agentToolsService.resolveWorkflowSandboxPath(
                    request == null ? null : request.getParentFolder(),
                    request == null ? null : request.getFolderName(),
                    workflowId
            );
        } catch (IllegalArgumentException e) {
            throw e;
        }
    }

    private void ensureSandboxDirectory(String sandboxPath) {
        if (sandboxPath == null || sandboxPath.isBlank()) {
            return;
        }
        agentToolsService.mkdir(sandboxPath);
    }

    private void discardPendingRun(String workflowId, String sandboxPath) {
        pendingDrafts.remove(workflowId);
        if (isAutoRunFolder(sandboxPath)) {
            agentToolsService.deleteDirectory(sandboxPath, true);
        }
    }

    private static boolean isAutoRunFolder(String sandboxPath) {
        return sandboxPath != null && sandboxPath.startsWith("runs/");
    }

    private static WorkflowRunResponse copy(WorkflowRunResponse source) {
        WorkflowRunResponse copy = new WorkflowRunResponse();
        copy.setWorkflowId(source.getWorkflowId());
        copy.setStatus(source.getStatus());
        copy.setCurrentPhase(source.getCurrentPhase());
        copy.setSummary(source.getSummary());
        copy.setRequirementsDoc(source.getRequirementsDoc());
        copy.setArchitectureDoc(source.getArchitectureDoc());
        copy.setTestReport(source.getTestReport());
        copy.setReviewVerdict(source.getReviewVerdict());
        copy.setReviewNotes(source.getReviewNotes());
        copy.setTestsPassed(source.isTestsPassed());
        copy.setAwaitingHuman(source.isAwaitingHuman());
        copy.setPhases(new ArrayList<>(source.getPhases()));
        copy.setSteps(new ArrayList<>(source.getSteps()));
        copy.setFilesWritten(new ArrayList<>(source.getFilesWritten()));
        copy.setSandboxFiles(new ArrayList<>(source.getSandboxFiles()));
        copy.setSandboxPath(source.getSandboxPath());
        return copy;
    }
}
