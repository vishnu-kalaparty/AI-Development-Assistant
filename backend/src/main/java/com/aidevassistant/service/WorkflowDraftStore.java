package com.aidevassistant.service;

import com.aidevassistant.tools.SandboxContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds sandbox file writes in memory until human approval when HITL is enabled.
 * Nothing is written to disk or Neo4j until {@link #flush(String, AgentToolsService, CodeIndexService)}.
 */
@Service
public class WorkflowDraftStore {

    private static final Logger log = LoggerFactory.getLogger(WorkflowDraftStore.class);

    private final Map<String, DraftSession> sessions = new ConcurrentHashMap<>();

    public void begin(String workflowId, String sandboxPath, boolean awaitHuman) {
        if (!awaitHuman || workflowId == null || workflowId.isBlank()) {
            return;
        }
        sessions.put(workflowId, new DraftSession(workflowId, SandboxContext.normalize(sandboxPath)));
    }

    public boolean isActive(String workflowId) {
        return workflowId != null && sessions.containsKey(workflowId);
    }

    public String write(String workflowId, AgentToolsService tools, String path, String content) {
        DraftSession session = requireSession(workflowId);
        String normalized = normalizePath(path);
        if (normalized.isBlank()) {
            return "Error: File path is required";
        }
        String body = content == null ? "" : content;
        int previousLines = previousLineCount(session, tools, normalized);
        boolean existed = session.drafts.containsKey(normalized) || fileExistsOnDisk(tools, normalized);
        session.drafts.put(normalized, body);
        int newLines = AgentToolsService.countLines(body);
        String lineSummary = AgentToolsService.formatWriteLineSummary(existed, previousLines, newLines);
        return "Successfully wrote " + normalized + ": " + lineSummary + " · " + body.length() + " chars (draft — pending approval)";
    }

    public String read(String workflowId, AgentToolsService tools, String path) {
        DraftSession session = sessions.get(workflowId);
        String normalized = normalizePath(path);
        if (session != null && session.drafts.containsKey(normalized)) {
            return session.drafts.get(normalized);
        }
        return tools.readFile(path);
    }

    public String mkdir(String workflowId, AgentToolsService tools, String path) {
        DraftSession session = requireSession(workflowId);
        String normalized = normalizePath(path);
        if (normalized.isBlank()) {
            return "Error: Directory path is required";
        }
        session.virtualDirs.add(normalized);
        ensureParentDirs(session, normalized);
        return "Successfully created directory " + normalized + " (draft — pending approval)";
    }

    public String listFiles(String workflowId, AgentToolsService tools, String directory, boolean recursive) {
        DraftSession session = sessions.get(workflowId);
        if (session == null) {
            return tools.listFiles(directory, recursive);
        }
        String dir = directory == null ? "" : normalizePath(directory);
        List<String> entries = mergedEntries(session, tools, dir, recursive);
        if (entries.isEmpty()) {
            String label = dir.isBlank() ? "." : dir;
            return "No files found in sandbox directory " + label;
        }
        String label = dir.isBlank() ? "." : dir;
        StringBuilder result = new StringBuilder("Files in sandbox directory " + label + ":\n");
        for (String entry : entries) {
            result.append("  - ").append(entry).append("\n");
        }
        return result.toString();
    }

    public String searchFiles(
            String workflowId,
            AgentToolsService tools,
            String directory,
            String pattern,
            boolean caseSensitive
    ) {
        DraftSession session = sessions.get(workflowId);
        if (session == null) {
            return tools.searchFiles(directory, pattern, caseSensitive);
        }
        String dir = directory == null ? "" : normalizePath(directory);
        String needle = pattern == null ? "" : pattern;
        if (needle.isBlank()) {
            return "Error: Search pattern is required";
        }
        List<String> hits = new ArrayList<>();
        for (Map.Entry<String, String> entry : session.drafts.entrySet()) {
            if (!matchesDirPrefix(entry.getKey(), dir)) {
                continue;
            }
            if (contains(entry.getValue(), needle, caseSensitive)) {
                hits.add(entry.getKey());
            }
        }
        String disk = tools.searchFiles(directory, pattern, caseSensitive);
        if (disk.startsWith("Error:") || disk.startsWith("Security Error:")) {
            if (hits.isEmpty()) {
                return disk;
            }
        } else {
            for (String line : disk.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("- ")) {
                    String path = trimmed.substring(2).trim();
                    if (!session.drafts.containsKey(path) && !hits.contains(path)) {
                        hits.add(path);
                    }
                }
            }
        }
        if (hits.isEmpty()) {
            return "No matches for \"" + needle + "\"";
        }
        hits.sort(String::compareTo);
        StringBuilder result = new StringBuilder("Matches for \"" + needle + "\":\n");
        for (String hit : hits) {
            result.append("  - ").append(hit).append("\n");
        }
        return result.toString();
    }

    public String semanticSearchDrafts(String workflowId, String query, int topK, String pathPrefix) {
        DraftSession session = sessions.get(workflowId);
        if (session == null || session.drafts.isEmpty()) {
            return "No indexed draft files yet. Use read_file or wait until approval for semantic search.";
        }
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.isBlank()) {
            return "Error: query is required";
        }
        String prefix = normalizePath(pathPrefix);
        List<String> hits = new ArrayList<>();
        for (Map.Entry<String, String> entry : session.drafts.entrySet()) {
            if (!prefix.isBlank() && !entry.getKey().startsWith(prefix)) {
                continue;
            }
            if (entry.getValue().toLowerCase(Locale.ROOT).contains(q)) {
                hits.add(entry.getKey());
            }
        }
        hits.sort(String::compareTo);
        int limit = Math.max(1, Math.min(topK, hits.size()));
        if (hits.isEmpty()) {
            return "No draft matches for \"" + query + "\"";
        }
        StringBuilder result = new StringBuilder("Draft matches for \"" + query + "\":\n");
        for (int i = 0; i < limit; i++) {
            String path = hits.get(i);
            String preview = session.drafts.get(path);
            if (preview != null && preview.length() > 200) {
                preview = preview.substring(0, 200) + "...";
            }
            result.append("  - ").append(path);
            if (preview != null && !preview.isBlank()) {
                result.append("\n    ").append(preview.replace("\n", "\n    "));
            }
            result.append("\n");
        }
        return result.toString();
    }

    public Path materializeForExecution(String workflowId, AgentToolsService tools) throws IOException {
        DraftSession session = requireSession(workflowId);
        Path temp = Files.createTempDirectory("sdlc-exec-" + workflowId.substring(0, Math.min(8, workflowId.length())) + "-");
        session.tempExecDirs.add(temp);
        try (SandboxContext.Scope ignored = SandboxContext.scope(session.sandboxPath)) {
            for (String path : tools.listFilePaths(true)) {
                if (session.drafts.containsKey(path)) {
                    continue;
                }
                copyRelativeFile(tools, temp, path);
            }
        }
        for (Map.Entry<String, String> entry : session.drafts.entrySet()) {
            writeRelative(temp, entry.getKey(), entry.getValue());
        }
        return temp;
    }

    public void cleanupTempDirs(String workflowId) {
        DraftSession session = sessions.get(workflowId);
        if (session == null) {
            return;
        }
        for (Path dir : session.tempExecDirs) {
            deleteTreeQuietly(dir);
        }
        session.tempExecDirs.clear();
    }

    public List<String> draftPaths(String workflowId) {
        DraftSession session = sessions.get(workflowId);
        if (session == null) {
            return List.of();
        }
        return new ArrayList<>(session.drafts.keySet());
    }

    public String readDraftOnly(String workflowId, String path) {
        DraftSession session = sessions.get(workflowId);
        if (session == null) {
            return null;
        }
        String normalized = normalizePath(path);
        return session.drafts.get(normalized);
    }

    public record DraftCompare(String content, String baseline, String status) {
    }

    public DraftCompare readCompare(String workflowId, String path, AgentToolsService tools) {
        DraftSession session = sessions.get(workflowId);
        if (session == null) {
            return null;
        }
        String normalized = normalizePath(path);
        String draft = session.drafts.get(normalized);
        if (draft == null) {
            return null;
        }
        String baseline = null;
        String status = "new";
        try (SandboxContext.Scope ignored = SandboxContext.scope(session.sandboxPath)) {
            String disk = tools.readFile(normalized);
            if (disk != null && !disk.startsWith("Error:") && !disk.startsWith("Security Error:")) {
                baseline = disk;
                status = disk.equals(draft) ? "unchanged" : "modified";
            }
        }
        return new DraftCompare(draft, baseline, status);
    }

    public List<com.aidevassistant.dto.DraftFileEntryDTO> draftManifest(String workflowId, AgentToolsService tools) {
        DraftSession session = sessions.get(workflowId);
        if (session == null || session.drafts.isEmpty()) {
            return List.of();
        }
        List<com.aidevassistant.dto.DraftFileEntryDTO> entries = new ArrayList<>();
        try (SandboxContext.Scope ignored = SandboxContext.scope(session.sandboxPath)) {
            for (Map.Entry<String, String> entry : session.drafts.entrySet()) {
                String path = entry.getKey();
                String draft = entry.getValue();
                String disk = tools.readFile(path);
                boolean onDisk = disk != null && !disk.startsWith("Error:") && !disk.startsWith("Security Error:");
                String status = !onDisk ? "new" : (disk.equals(draft) ? "unchanged" : "modified");
                entries.add(new com.aidevassistant.dto.DraftFileEntryDTO(path, status));
            }
        }
        entries.sort(Comparator.comparing(com.aidevassistant.dto.DraftFileEntryDTO::getPath));
        return entries;
    }

    public String hybridSemanticSearch(
            String workflowId,
            String query,
            int topK,
            String pathPrefix,
            CodeIndexService codeIndexService,
            AgentToolsService tools
    ) {
        DraftSession session = sessions.get(workflowId);
        Set<String> draftPaths = session == null ? Set.of() : session.drafts.keySet();
        int limit = Math.max(1, Math.min(topK, 20));
        StringBuilder result = new StringBuilder();
        boolean neo4jHits = false;
        boolean draftHits = false;

        if (codeIndexService.isEnabled()) {
            String neo4j = codeIndexService.semanticSearch(query, limit, pathPrefix, draftPaths);
            if (neo4j != null && !neo4j.isBlank() && !neo4j.startsWith("Semantic search is disabled")
                    && !neo4j.startsWith("No semantic matches") && !neo4j.startsWith("Semantic search error")
                    && !neo4j.startsWith("Embedding failed") && !neo4j.startsWith("No active project")) {
                result.append("Unchanged files (Neo4j):\n").append(neo4j);
                neo4jHits = true;
            }
        }

        String draftResult = semanticSearchDrafts(workflowId, query, limit, pathPrefix);
        if (draftResult != null && !draftResult.startsWith("Error:") && !draftResult.startsWith("No draft matches")
                && !draftResult.startsWith("No indexed draft")) {
            if (!result.isEmpty()) {
                result.append("\n\n---\n\n");
            }
            result.append("Draft changes (in memory):\n").append(draftResult);
            draftHits = true;
        }

        if (result.isEmpty()) {
            if (session == null || session.drafts.isEmpty()) {
                String fallback = codeIndexService.isEnabled()
                        ? codeIndexService.semanticSearch(query, limit, pathPrefix)
                        : "No matches (drafts empty, Neo4j unavailable).";
                log.info("hybrid semantic_search workflowId={} neo4jHits=false draftHits=false outcome={}",
                        workflowId, summarizeHybridOutcome(fallback));
                return fallback;
            }
            String noMatch = "No matches in Neo4j or drafts for \"" + query + "\".";
            log.info("hybrid semantic_search workflowId={} neo4jHits=false draftHits=false draftCount={}",
                    workflowId, draftPaths.size());
            return noMatch;
        }
        log.info("hybrid semantic_search workflowId={} neo4jHits={} draftHits={} draftPaths={}",
                workflowId, neo4jHits, draftHits, draftPaths.size());
        return result.toString();
    }

    private static String summarizeHybridOutcome(String result) {
        if (result == null || result.isBlank()) {
            return "empty";
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
        return "hits";
    }

    public List<String> mergedFilePaths(String workflowId, AgentToolsService tools) {
        DraftSession session = sessions.get(workflowId);
        if (session == null) {
            return List.of();
        }
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        try (SandboxContext.Scope ignored = SandboxContext.scope(session.sandboxPath)) {
            merged.addAll(tools.listFilePaths(true));
        }
        merged.addAll(session.drafts.keySet());
        return new ArrayList<>(merged);
    }

    public void flush(String workflowId, AgentToolsService tools, CodeIndexService codeIndexService) {
        DraftSession session = sessions.remove(workflowId);
        if (session == null || session.drafts.isEmpty()) {
            cleanupRemovedSession(session);
            return;
        }
        int filesWritten = 0;
        try (SandboxContext.Scope ignored = SandboxContext.scope(session.sandboxPath)) {
            for (Map.Entry<String, String> entry : session.drafts.entrySet()) {
                String path = entry.getKey();
                int slash = path.lastIndexOf('/');
                if (slash > 0) {
                    tools.mkdir(path.substring(0, slash));
                }
                String result = tools.writeFile(path, entry.getValue());
                if (result.startsWith("Successfully wrote") || result.startsWith("Wrote")) {
                    filesWritten++;
                }
            }
        }
        int chunksIndexed = 0;
        if (filesWritten > 0 && codeIndexService.isEnabled()) {
            chunksIndexed = codeIndexService.syncProjectIndex(session.sandboxPath, tools);
        }
        cleanupRemovedSession(session);
        log.info("Flushed {} draft file(s) for workflow {} → {} written, {} chunks indexed in Neo4j",
                session.drafts.size(), workflowId, filesWritten, chunksIndexed);
    }

    /** Clears in-memory drafts without writing to disk (reject / revision loop). */
    public void clearDrafts(String workflowId) {
        DraftSession session = sessions.get(workflowId);
        if (session != null) {
            session.drafts.clear();
            session.virtualDirs.clear();
            cleanupTempDirs(workflowId);
        }
    }

    /** Stops a run but keeps in-memory drafts and on-disk sandbox files for review. */
    public void suspend(String workflowId) {
        DraftSession session = sessions.get(workflowId);
        if (session != null) {
            session.suspended = true;
            cleanupTempDirs(workflowId);
        }
    }

    /** Ends draft mode without deleting sandbox files on disk. */
    public void discard(String workflowId, AgentToolsService tools) {
        end(workflowId);
    }

    public void end(String workflowId) {
        DraftSession session = sessions.remove(workflowId);
        cleanupRemovedSession(session);
    }

    private void cleanupRemovedSession(DraftSession session) {
        if (session == null) {
            return;
        }
        for (Path dir : session.tempExecDirs) {
            deleteTreeQuietly(dir);
        }
    }

    private DraftSession requireSession(String workflowId) {
        DraftSession session = sessions.get(workflowId);
        if (session == null) {
            throw new IllegalStateException("Draft session not active for workflow: " + workflowId);
        }
        return session;
    }

    private static boolean isAutoRunFolder(String sandboxPath) {
        return sandboxPath != null && sandboxPath.startsWith("runs/");
    }

    private static String normalizePath(String path) {
        return SandboxContext.normalize(path == null ? "" : path);
    }

    private static void ensureParentDirs(DraftSession session, String normalizedPath) {
        int slash = normalizedPath.lastIndexOf('/');
        if (slash <= 0) {
            return;
        }
        session.virtualDirs.add(normalizedPath.substring(0, slash));
    }

    private static int previousLineCount(DraftSession session, AgentToolsService tools, String normalized) {
        if (session.drafts.containsKey(normalized)) {
            return AgentToolsService.countLines(session.drafts.get(normalized));
        }
        if (fileExistsOnDisk(tools, normalized)) {
            String existing = tools.readFile(normalized);
            if (!existing.startsWith("Error:") && !existing.startsWith("Security Error:")) {
                return AgentToolsService.countLines(existing);
            }
        }
        return 0;
    }

    private static boolean fileExistsOnDisk(AgentToolsService tools, String normalized) {
        String existing = tools.readFile(normalized);
        return existing != null
                && !existing.startsWith("Error:")
                && !existing.startsWith("Security Error:");
    }

    private static List<String> mergedEntries(DraftSession session, AgentToolsService tools, String dir, boolean recursive) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        try (SandboxContext.Scope ignored = SandboxContext.scope(session.sandboxPath)) {
            Path effective = dir.isBlank()
                    ? tools.resolveSafePath("")
                    : tools.resolveSafePath(dir);
            if (Files.isDirectory(effective)) {
                names.addAll(listDiskEntryNames(tools, dir, recursive));
            }
        } catch (Exception ignored) {
            // Empty sandbox is fine for draft-only runs.
        }
        for (String draftPath : session.drafts.keySet()) {
            if (matchesDirPrefix(draftPath, dir)) {
                if (recursive) {
                    names.add(draftPath);
                } else {
                    String relative = dir.isBlank() ? draftPath : draftPath.substring(dir.length() + (dir.endsWith("/") ? 0 : 1));
                    if (!relative.contains("/")) {
                        names.add(relative);
                    }
                }
            }
            ensureVirtualDirs(session, draftPath, names, dir, recursive);
        }
        for (String virtualDir : session.virtualDirs) {
            if (matchesDirPrefix(virtualDir + "/", dir) || virtualDir.equals(dir)) {
                String name = dir.isBlank() ? virtualDir + "/" : virtualDir.substring(dir.length() + (dir.isBlank() ? 0 : 1)) + "/";
                if (!name.contains("//")) {
                    names.add(name.endsWith("/") ? name : name + "/");
                }
            }
        }
        return names.stream().sorted(Comparator.naturalOrder()).toList();
    }

    private static void ensureVirtualDirs(DraftSession session, String draftPath, Set<String> names, String dir, boolean recursive) {
        int slash = draftPath.lastIndexOf('/');
        if (slash <= 0) {
            return;
        }
        String parent = draftPath.substring(0, slash);
        session.virtualDirs.add(parent);
        if (recursive && (dir.isBlank() || parent.startsWith(dir))) {
            names.add(parent + "/");
        }
    }

    private static List<String> listDiskEntryNames(AgentToolsService tools, String dir, boolean recursive) {
        String listing = tools.listFiles(dir, recursive);
        if (listing.startsWith("Error:") || listing.startsWith("Security Error:") || listing.startsWith("No files")) {
            return List.of();
        }
        List<String> entries = new ArrayList<>();
        for (String line : listing.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("- ")) {
                entries.add(trimmed.substring(2).trim());
            }
        }
        return entries;
    }

    private static boolean matchesDirPrefix(String path, String dir) {
        if (dir == null || dir.isBlank()) {
            return true;
        }
        return path.equals(dir) || path.startsWith(dir + "/");
    }

    private static boolean contains(String haystack, String needle, boolean caseSensitive) {
        if (haystack == null) {
            return false;
        }
        return caseSensitive ? haystack.contains(needle) : haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private static void copyRelativeFile(AgentToolsService tools, Path tempRoot, String relativePath) throws IOException {
        String content = tools.readFile(relativePath);
        if (content.startsWith("Error:") || content.startsWith("Security Error:")) {
            return;
        }
        writeRelative(tempRoot, relativePath, content);
    }

    private static void writeRelative(Path root, String relativePath, String content) throws IOException {
        Path target = root.resolve(relativePath).normalize();
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(target, content == null ? "" : content);
    }

    private static void deleteTreeQuietly(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try {
            try (var walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                        // Best effort temp cleanup.
                    }
                });
            }
        } catch (IOException ignored) {
            // Best effort temp cleanup.
        }
    }

    private static final class DraftSession {
        private final String workflowId;
        private final String sandboxPath;
        private boolean suspended;
        private final Map<String, String> drafts = new LinkedHashMap<>();
        private final Set<String> virtualDirs = new LinkedHashSet<>();
        private final List<Path> tempExecDirs = new ArrayList<>();

        private DraftSession(String workflowId, String sandboxPath) {
            this.workflowId = workflowId;
            this.sandboxPath = sandboxPath;
        }
    }
}
