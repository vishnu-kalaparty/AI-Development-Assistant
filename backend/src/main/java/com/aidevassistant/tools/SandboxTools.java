package com.aidevassistant.tools;

import com.aidevassistant.service.AgentToolsService;
import com.aidevassistant.service.CodeIndexService;
import com.aidevassistant.service.ExecutionService;
import com.aidevassistant.service.WorkflowDraftStore;
import com.aidevassistant.service.WorkflowRunTracker;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class SandboxTools {

    private static final Logger log = LoggerFactory.getLogger(SandboxTools.class);

    private final AgentToolsService agentToolsService;
    private final ExecutionService executionService;
    private final StepRecorder recorder;
    private final CodeIndexService codeIndexService;
    private final WorkflowRunTracker runTracker;
    private final WorkflowDraftStore draftStore;
    private final StageToolSet stageToolSet = new StageToolSet();

    public SandboxTools(
            AgentToolsService agentToolsService,
            ExecutionService executionService,
            StepRecorder recorder,
            CodeIndexService codeIndexService,
            WorkflowRunTracker runTracker,
            WorkflowDraftStore draftStore
    ) {
        this.agentToolsService = agentToolsService;
        this.executionService = executionService;
        this.recorder = recorder;
        this.codeIndexService = codeIndexService;
        this.runTracker = runTracker;
        this.draftStore = draftStore;
    }

    private void ensureRunning() {
        String workflowId = WorkflowRunContext.workflowId();
        if (workflowId != null && !workflowId.isBlank()) {
            runTracker.checkCancelled(workflowId);
        }
    }

    private String workflowId() {
        return WorkflowRunContext.workflowId();
    }

    private boolean draftMode() {
        String workflowId = workflowId();
        return workflowId != null && draftStore.isActive(workflowId);
    }

    public StageToolSet tools() {
        return stageToolSet;
    }

    String listFiles(String directory, boolean recursive) {
        ensureRunning();
        logExplorationWithoutSemanticSearch("list_files", directory);
        String dir = directory == null ? "" : directory;
        String result = ToolOutputLimiter.truncate(
                draftMode()
                        ? draftStore.listFiles(workflowId(), agentToolsService, dir, recursive)
                        : agentToolsService.listFiles(dir, recursive)
        );
        recorder.record("Listing sandbox files", "list_files", dir, result);
        return result;
    }

    String readFile(String path) {
        ensureRunning();
        logExplorationWithoutSemanticSearch("read_file", path);
        String result = ToolOutputLimiter.truncate(
                draftMode()
                        ? draftStore.read(workflowId(), agentToolsService, path)
                        : agentToolsService.readFile(path)
        );
        recorder.record("Reading sandbox file", "read_file", path, result);
        return result;
    }

    String writeFile(String path, String content) {
        ensureRunning();
        String body = content == null ? "" : content;
        String result;
        if (draftMode()) {
            result = ToolOutputLimiter.truncate(draftStore.write(workflowId(), agentToolsService, path, body));
        } else {
            result = ToolOutputLimiter.truncate(agentToolsService.writeFile(path, body));
            if (result.startsWith("Successfully wrote") || result.startsWith("Wrote")) {
                codeIndexService.indexFile(path, body);
            }
        }
        recorder.record("Writing sandbox file", "write_file", path, result);
        return result;
    }

    String mkdir(String path) {
        ensureRunning();
        String result = ToolOutputLimiter.truncate(
                draftMode()
                        ? draftStore.mkdir(workflowId(), agentToolsService, path)
                        : agentToolsService.mkdir(path == null ? "" : path)
        );
        recorder.record("Creating sandbox directory", "mkdir", path, result);
        return result;
    }

    String searchFiles(String directory, String pattern, boolean caseSensitive) {
        ensureRunning();
        logExplorationWithoutSemanticSearch("search_files", pattern);
        String dir = directory == null ? "" : directory;
        String result = ToolOutputLimiter.truncate(
                draftMode()
                        ? draftStore.searchFiles(workflowId(), agentToolsService, dir, pattern, caseSensitive)
                        : agentToolsService.searchFiles(dir, pattern == null ? "" : pattern, caseSensitive)
        );
        recorder.record("Searching sandbox files", "search_files", pattern, result);
        return result;
    }

    String runCode(String path) {
        ensureRunning();
        String result;
        if (draftMode()) {
            String workflowId = workflowId();
            try {
                Path tempRoot = draftStore.materializeForExecution(workflowId, agentToolsService);
                Path target = tempRoot.resolve(SandboxContext.normalize(path)).normalize();
                if (!target.startsWith(tempRoot) || !Files.isRegularFile(target)) {
                    result = "Error: File not found in sandbox: " + path;
                } else {
                    result = executionService.runAbsoluteFile(target, tempRoot);
                }
            } catch (Exception e) {
                result = "Error running file: " + e.getMessage();
            } finally {
                draftStore.cleanupTempDirs(workflowId);
            }
        } else {
            result = executionService.runFile(path);
        }
        result = ToolOutputLimiter.truncate(result);
        recorder.record("Running sandbox file", "run_code", path, result);
        return result;
    }

    String semanticSearch(String query, int topK, String pathPrefix) {
        ensureRunning();
        int k = topK <= 0 ? 8 : topK;
        String prefix = pathPrefix == null ? "" : pathPrefix;
        String workflowId = workflowId();
        String project = SandboxContext.get();
        boolean hybrid = draftMode();
        log.info("semantic_search invoked workflowId={} project={} draftMode={} query=\"{}\" topK={} pathPrefix=\"{}\"",
                workflowId, project, hybrid, query, k, prefix);
        String result = ToolOutputLimiter.truncate(
                hybrid
                        ? draftStore.hybridSemanticSearch(
                                workflowId, query, k, prefix, codeIndexService, agentToolsService)
                        : codeIndexService.semanticSearch(query, k, prefix)
        );
        log.info("semantic_search result workflowId={} outcome={} preview={}",
                workflowId, summarizeSemanticOutcome(result), abbreviate(result, 160));
        recorder.record("Semantic code search", "semantic_search", query, result);
        return result;
    }

    private static String summarizeSemanticOutcome(String result) {
        if (result == null || result.isBlank()) {
            return "empty";
        }
        if (result.startsWith("Semantic search is disabled")) {
            return "disabled";
        }
        if (result.startsWith("No active project")) {
            return "no_sandbox_context";
        }
        if (result.startsWith("Embedding failed")) {
            return "embedding_failed";
        }
        if (result.startsWith("Semantic search error")) {
            return "error";
        }
        if (result.startsWith("No semantic matches") || result.startsWith("No matches")) {
            return "no_hits";
        }
        int hits = result.split("\n\n---\n\n", -1).length;
        return "hits=" + hits;
    }

    private static String abbreviate(String value, int maxLen) {
        if (value == null) {
            return "";
        }
        String trimmed = value.replace('\n', ' ').trim();
        if (trimmed.length() <= maxLen) {
            return trimmed;
        }
        return trimmed.substring(0, maxLen) + "…";
    }

    private void logExplorationWithoutSemanticSearch(String tool, String args) {
        if (recorder.usedSemanticSearch()) {
            return;
        }
        String project = SandboxContext.get();
        if (project == null || project.isBlank()) {
            return;
        }
        CodeIndexService.ProjectIndexStats stats = codeIndexService.projectIndexStats(project);
        if (!stats.searchable()) {
            return;
        }
        log.warn("LLM called {} before semantic_search workflowId={} project={} indexedChunks={} args={}",
                tool, workflowId(), project, stats.chunks(), abbreviate(args, 80));
    }

    public class StageToolSet {
        @Tool(name = "list_files", value = "List files and folders in a sandbox directory. Directories end with /. Nested paths are supported when recursive=true.")
        public String listFiles(
                @P("Directory relative to sandbox. Use empty string for the root.") String directory,
                @P("If true, list nested files and folders recursively.") boolean recursive
        ) {
            return SandboxTools.this.listFiles(directory, recursive);
        }

        @Tool(name = "read_file", value = "Read a text file from the sandbox. Nested paths are allowed.")
        public String readFile(@P("File path relative to the sandbox") String path) {
            return SandboxTools.this.readFile(path);
        }

        @Tool(name = "write_file", value = "Write a text file inside the sandbox. Paths must match docs/README.md and Architecture.")
        public String writeFile(
                @P("File path relative to the sandbox") String path,
                @P("Full file contents to write") String content
        ) {
            return SandboxTools.this.writeFile(path, content);
        }

        @Tool(name = "mkdir", value = "Create a directory inside the sandbox. Nested parents are created if needed.")
        public String mkdir(@P("Directory path relative to the sandbox") String path) {
            return SandboxTools.this.mkdir(path);
        }

        @Tool(name = "search_files", value = "Search file contents in the sandbox for a substring.")
        public String searchFiles(
                @P("Directory relative to sandbox. Empty string for root.") String directory,
                @P("Substring to search for") String pattern,
                @P("Case-sensitive search. Defaults to false.") boolean caseSensitive
        ) {
            return SandboxTools.this.searchFiles(directory, pattern, caseSensitive);
        }

        @Tool(name = "run_code", value = "Execute a .py, .js, or .java file already in the sandbox.")
        public String runCode(@P("File path relative to the sandbox") String path) {
            return SandboxTools.this.runCode(path);
        }

        @Tool(name = "semantic_search", value = "Quickly find relevant code snippets by meaning (Neo4j vector index). Default first step on existing projects. Returns short previews — call read_file on a path only when you need the full file (snippets conflict, incomplete, or you are about to edit).")
        public String semanticSearch(
                @P("Natural language query") String query,
                @P("Max results (1-20). Default 8.") int topK,
                @P("Optional path prefix filter. Empty string for all.") String pathPrefix
        ) {
            int k = topK <= 0 ? 8 : topK;
            return SandboxTools.this.semanticSearch(query, k, pathPrefix);
        }
    }
}
