package com.aidevassistant.service;

import com.aidevassistant.tools.SandboxContext;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Service
public class CodeIndexService {

    private static final Logger log = LoggerFactory.getLogger(CodeIndexService.class);
    private static final String VECTOR_INDEX = "chunk_embeddings";
    private static final Set<String> INDEXABLE_EXTENSIONS = Set.of(
            ".py", ".java", ".js", ".ts", ".tsx", ".jsx", ".md", ".txt", ".json", ".xml", ".html", ".css", ".properties", ".yml", ".yaml"
    );

    private final OllamaEmbeddingService embeddingService;
    private final boolean enabled;
    private final int dimensions;
    private final Driver driver;
    private volatile boolean ready;

    public CodeIndexService(
            OllamaEmbeddingService embeddingService,
            @Value("${neo4j.enabled:true}") boolean enabled,
            @Value("${neo4j.vector-dimensions:768}") int dimensions,
            @Autowired(required = false) Driver driver
    ) {
        this.embeddingService = embeddingService;
        this.enabled = enabled;
        this.dimensions = dimensions;
        this.driver = driver;
    }

    @PostConstruct
    public void initializeSchema() {
        ensureSchema();
    }

    void ensureSchema() {
        if (!enabled || driver == null) {
            ready = false;
            return;
        }
        try (Session session = driver.session()) {
            session.run("RETURN 1").consume();
            session.run("""
                    CREATE CONSTRAINT project_id IF NOT EXISTS
                    FOR (p:Project) REQUIRE p.id IS UNIQUE
                    """);
            session.run("""
                    CREATE CONSTRAINT file_key IF NOT EXISTS
                    FOR (f:File) REQUIRE (f.projectId, f.path) IS UNIQUE
                    """);
            session.run("""
                    CREATE CONSTRAINT chunk_id IF NOT EXISTS
                    FOR (c:Chunk) REQUIRE c.id IS UNIQUE
                    """);
            ensureVectorIndex(session);
            ready = true;
            log.info("Neo4j code index ready (vector dimensions={})", dimensions);
        } catch (Exception e) {
            ready = false;
            log.warn("Neo4j schema setup failed (indexing disabled until Neo4j is available): {}", e.getMessage());
        }
    }

    private void ensureVectorIndex(Session session) {
        int indexedDims = readVectorIndexDimensions(session);
        if (indexedDims > 0 && indexedDims != dimensions) {
            log.warn("Neo4j vector index {} has {} dimensions but config expects {} — recreating index (reindex projects after restart)",
                    VECTOR_INDEX, indexedDims, dimensions);
            session.run("DROP INDEX " + VECTOR_INDEX + " IF EXISTS");
            indexedDims = 0;
        }
        if (indexedDims <= 0) {
            session.run(String.format("""
                    CREATE VECTOR INDEX %s IF NOT EXISTS
                    FOR (c:Chunk) ON (c.embedding)
                    OPTIONS {indexConfig: {
                      `vector.dimensions`: %d,
                      `vector.similarity_function`: 'cosine'
                    }}
                    """, VECTOR_INDEX, dimensions));
        }
    }

    private int readVectorIndexDimensions(Session session) {
        try {
            var result = session.run("""
                    SHOW INDEXES YIELD name, type, options
                    WHERE name = $indexName AND type = 'VECTOR'
                    RETURN options
                    """, Values.parameters("indexName", VECTOR_INDEX));
            if (!result.hasNext()) {
                return 0;
            }
            var options = result.single().get("options").asMap();
            Object indexConfig = options.get("indexConfig");
            if (indexConfig instanceof Map<?, ?> config) {
                Object dims = config.get("vector.dimensions");
                if (dims instanceof Number number) {
                    return number.intValue();
                }
            }
        } catch (Exception e) {
            log.debug("Could not read vector index dimensions: {}", e.getMessage());
        }
        return 0;
    }

    public boolean isConfigured() {
        return enabled;
    }

    public boolean isReady() {
        return ready;
    }

    public boolean isEnabled() {
        return enabled && driver != null && ready;
    }

    /** Indexed file/chunk counts for a project (empty when Neo4j is unavailable). */
    public ProjectIndexStats projectIndexStats(String projectId) {
        if (!isEnabled() || projectId == null || projectId.isBlank()) {
            return ProjectIndexStats.empty();
        }
        String normalized = SandboxContext.normalize(projectId);
        try (Session session = driver.session()) {
            var result = session.run("""
                            MATCH (f:File {projectId: $projectId})
                            OPTIONAL MATCH (f)-[:HAS_CHUNK]->(c:Chunk)
                            RETURN count(DISTINCT f) AS files, count(c) AS chunks
                            """,
                    Values.parameters("projectId", normalized));
            if (!result.hasNext()) {
                return ProjectIndexStats.empty();
            }
            var record = result.single();
            return new ProjectIndexStats(record.get("files").asInt(), record.get("chunks").asInt());
        } catch (Exception e) {
            log.warn("Could not read Neo4j index stats for {}: {}", normalized, e.getMessage());
            return ProjectIndexStats.empty();
        }
    }

    public record ProjectIndexStats(int files, int chunks) {
        public static ProjectIndexStats empty() {
            return new ProjectIndexStats(0, 0);
        }

        public boolean searchable() {
            return chunks > 0;
        }
    }

    public int indexFile(String path, String content) {
        if (!isEnabled() || !isIndexable(path) || content == null) {
            return 0;
        }
        String projectId = currentProjectId();
        if (projectId.isBlank()) {
            return 0;
        }
        String kind = classifyKind(path);
        String hash = Integer.toHexString(content.hashCode());
        List<String> chunks = CodeChunker.chunk(content);
        if (chunks.isEmpty()) {
            return 0;
        }
        int created = 0;
        try (Session session = driver.session()) {
            Integer count = session.executeWrite(tx -> {
                tx.run("""
                        MERGE (p:Project {id: $projectId})
                        MERGE (f:File {projectId: $projectId, path: $path})
                        MERGE (p)-[:HAS_FILE]->(f)
                        SET f.kind = $kind, f.contentHash = $hash, f.updatedAt = datetime()
                        WITH f
                        OPTIONAL MATCH (f)-[:HAS_CHUNK]->(old:Chunk)
                        DETACH DELETE old
                        """, Values.parameters(
                        "projectId", projectId,
                        "path", normalizePath(path),
                        "kind", kind,
                        "hash", hash
                ));
                int chunkCount = 0;
                for (int i = 0; i < chunks.size(); i++) {
                    String chunkText = chunks.get(i);
                    List<Float> embedding = embeddingService.embed(chunkText);
                    if (embedding.isEmpty()) {
                        continue;
                    }
                    chunkCount++;
                    String chunkId = projectId + ":" + normalizePath(path) + "#" + i;
                    tx.run("""
                            MATCH (f:File {projectId: $projectId, path: $path})
                            CREATE (c:Chunk {
                              id: $chunkId,
                              chunkIndex: $index,
                              text: $text,
                              embedding: $embedding
                            })
                            CREATE (f)-[:HAS_CHUNK]->(c)
                            """, Values.parameters(
                            "projectId", projectId,
                            "path", normalizePath(path),
                            "chunkId", chunkId,
                            "index", i,
                            "text", chunkText,
                            "embedding", embedding
                    ));
                }
                return chunkCount;
            });
            created = count == null ? 0 : count;
        } catch (Exception e) {
            log.warn("Failed to index {}: {}", path, e.getMessage());
        }
        return created;
    }

    public void clearProject(String projectId) {
        if (!isEnabled() || projectId == null || projectId.isBlank()) {
            return;
        }
        String normalized = SandboxContext.normalize(projectId);
        try (Session session = driver.session()) {
            session.executeWrite(tx -> {
                tx.run("""
                        MATCH (p:Project {id: $projectId})
                        OPTIONAL MATCH (p)-[:HAS_FILE]->(f:File)
                        OPTIONAL MATCH (f)-[:HAS_CHUNK]->(c:Chunk)
                        DETACH DELETE c, f, p
                        """, Values.parameters("projectId", normalized));
                return null;
            });
            log.info("Cleared Neo4j index for project {}", normalized);
        } catch (Exception e) {
            log.warn("Failed to clear project {}: {}", normalized, e.getMessage());
        }
    }

    public int syncProjectIndex(String sandboxPath, AgentToolsService tools) {
        if (!isEnabled() || sandboxPath == null || sandboxPath.isBlank()) {
            return 0;
        }
        String projectId = SandboxContext.normalize(sandboxPath);
        int filesIndexed = 0;
        int chunksCreated = 0;
        List<String> paths = tools.listFilePaths(projectId, true);
        try (SandboxContext.Scope ignored = SandboxContext.scope(projectId)) {
            for (String path : paths) {
                if (!isIndexable(path)) {
                    continue;
                }
                String content = tools.readFile(path);
                if (content.startsWith("Error:") || content.startsWith("Security Error:")) {
                    log.warn("Skipping index for {}: {}", path, content);
                    continue;
                }
                int chunks = indexFile(path, content);
                if (chunks > 0 || !content.isBlank()) {
                    filesIndexed++;
                }
                chunksCreated += chunks;
            }
        }
        log.info("Synced Neo4j index for {}: {} files, {} chunks", projectId, filesIndexed, chunksCreated);
        if (filesIndexed > 0 && chunksCreated == 0) {
            log.warn("No embeddings created for {} — ensure Ollama is running with {}", projectId, embeddingService.modelName());
        }
        return chunksCreated;
    }

    public Map<String, Object> rebuildProject(String sandboxPath, AgentToolsService tools) {
        Map<String, Object> result = new LinkedHashMap<>();
        String projectId = SandboxContext.normalize(sandboxPath);
        result.put("projectId", projectId);
        if (!isEnabled()) {
            result.put("error", "Neo4j indexing is not ready");
            return result;
        }
        if (projectId.isBlank()) {
            result.put("error", "sandboxPath is required");
            return result;
        }
        clearProject(projectId);
        int chunksCreated = syncProjectIndex(sandboxPath, tools);
        List<String> paths = tools.listFilePaths(projectId, true);
        int filesIndexed = 0;
        int filesSkipped = 0;
        for (String path : paths) {
            if (!isIndexable(path)) {
                filesSkipped++;
            } else {
                filesIndexed++;
            }
        }
        result.put("fileCount", paths.size());
        result.put("filesIndexed", filesIndexed);
        result.put("filesSkipped", filesSkipped);
        result.put("chunksCreated", chunksCreated);
        if (chunksCreated == 0 && filesIndexed > 0) {
            result.put("warning", "No chunks created — ensure Ollama is running with " + embeddingService.modelName());
        }
        log.info("Rebuilt index for {}: {} files, {} chunks", projectId, filesIndexed, chunksCreated);
        return result;
    }

    /**
     * Reconstructs sandbox files from Neo4j chunk text (best-effort recovery after accidental deletion).
     */
    public Map<String, Object> restoreProjectFromIndex(String sandboxPath, AgentToolsService tools) {
        Map<String, Object> result = new LinkedHashMap<>();
        String projectId = SandboxContext.normalize(sandboxPath);
        result.put("projectId", projectId);
        if (!isEnabled()) {
            result.put("error", "Neo4j indexing is not ready");
            return result;
        }
        if (projectId.isBlank()) {
            result.put("error", "sandboxPath is required");
            return result;
        }
        Map<String, TreeMap<Integer, String>> chunksByPath = new LinkedHashMap<>();
        try (Session session = driver.session()) {
            session.run("""
                            MATCH (f:File {projectId: $projectId})-[:HAS_CHUNK]->(c:Chunk)
                            RETURN f.path AS path, c.chunkIndex AS idx, c.text AS text
                            ORDER BY path, idx
                            """,
                    Values.parameters("projectId", projectId)
            ).forEachRemaining(record -> {
                String path = record.get("path").asString();
                int idx = record.get("idx").asInt();
                chunksByPath.computeIfAbsent(path, ignored -> new TreeMap<>()).put(idx, record.get("text").asString(""));
            });
        } catch (Exception e) {
            result.put("error", e.getMessage());
            return result;
        }
        if (chunksByPath.isEmpty()) {
            result.put("error", "No indexed files found for project");
            return result;
        }
        int restored = 0;
        try (SandboxContext.Scope ignored = SandboxContext.scope(projectId)) {
            for (Map.Entry<String, TreeMap<Integer, String>> entry : chunksByPath.entrySet()) {
                String content = reconstructFromChunks(new ArrayList<>(entry.getValue().values()));
                String writeResult = tools.writeFile(entry.getKey(), content);
                if (writeResult.startsWith("Successfully wrote") || writeResult.startsWith("Wrote")) {
                    restored++;
                }
            }
        }
        result.put("filesRestored", restored);
        result.put("fileCount", chunksByPath.size());
        log.info("Restored {} file(s) to sandbox from Neo4j index for {}", restored, projectId);
        return result;
    }

    static String reconstructFromChunks(List<String> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "";
        }
        if (chunks.size() == 1) {
            return chunks.get(0);
        }
        StringBuilder builder = new StringBuilder(chunks.get(0));
        for (int i = 1; i < chunks.size(); i++) {
            String next = chunks.get(i);
            int skip = Math.min(CodeChunker.overlapChars(), next.length());
            builder.append(next.substring(skip));
        }
        return builder.toString();
    }

    public String semanticSearch(String query, int topK, String pathPrefix) {
        return semanticSearch(query, topK, pathPrefix, Set.of());
    }

    public String semanticSearch(String query, int topK, String pathPrefix, Set<String> excludePaths) {
        if (!isEnabled()) {
            log.warn("Neo4j semantic search skipped: index disabled (neo4j.enabled=false or Neo4j unavailable)");
            return "Semantic search is disabled (neo4j.enabled=false or Neo4j unavailable).";
        }
        if (query == null || query.isBlank()) {
            log.warn("Neo4j semantic search skipped: blank query");
            return "Query is required.";
        }
        String projectId = currentProjectId();
        if (projectId.isBlank()) {
            log.warn("Neo4j semantic search skipped: no SandboxContext project (SandboxContext not set)");
            return "No active project sandbox.";
        }
        int limit = Math.max(1, Math.min(topK, 20));
        String prefix = pathPrefix == null ? "" : normalizePath(pathPrefix);
        log.info("Neo4j semantic search start project={} queryChars={} topK={} prefix=\"{}\" embedModel={}",
                projectId, query.length(), limit, prefix, embeddingService.modelName());

        List<Float> vector = embeddingService.embed(query);
        if (vector.isEmpty()) {
            log.warn("Neo4j semantic search failed project={}: Ollama embedding returned empty vector (model={})",
                    projectId, embeddingService.modelName());
            return "Embedding failed — ensure Ollama is running with model " + embeddingService.modelName() + ".";
        }
        log.info("Neo4j semantic search embedded project={} vectorDims={}", projectId, vector.size());
        try (Session session = driver.session()) {
            var result = session.run("""
                            CALL db.index.vector.queryNodes($indexName, $topK, $vector)
                            YIELD node AS chunk, score
                            MATCH (f:File {projectId: $projectId})-[:HAS_CHUNK]->(chunk)
                            WHERE $prefix = '' OR f.path STARTS WITH $prefix
                            RETURN f.path AS path, chunk.chunkIndex AS chunkIndex, chunk.text AS text, score
                            ORDER BY score DESC
                            LIMIT $limit
                            """,
                    Values.parameters(
                            "indexName", VECTOR_INDEX,
                            "topK", limit * 3,
                            "vector", vector,
                            "projectId", projectId,
                            "prefix", prefix,
                            "limit", limit
                    ));
            List<String> lines = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            Set<String> excluded = new LinkedHashSet<>();
            double[] topScore = { Double.NaN };
            if (excludePaths != null) {
                for (String path : excludePaths) {
                    if (path != null && !path.isBlank()) {
                        excluded.add(normalizePath(path));
                    }
                }
            }
            result.forEachRemaining(record -> {
                String path = record.get("path").asString();
                if (excluded.contains(normalizePath(path))) {
                    return;
                }
                String key = path + "#" + record.get("chunkIndex").asInt();
                if (!seen.add(key)) {
                    return;
                }
                double score = record.get("score").asDouble();
                if (lines.isEmpty()) {
                    topScore[0] = score;
                }
                String text = record.get("text").asString("");
                String preview = text.length() > 400 ? text.substring(0, 400) + "..." : text;
                lines.add(String.format(Locale.ROOT, "[%.3f] %s\n%s", score, path, preview));
            });
            if (lines.isEmpty()) {
                log.info("Neo4j semantic search project={} hits=0 (no matches for query)", projectId);
                return "No semantic matches in project " + projectId + ".";
            }
            log.info("Neo4j semantic search project={} hits={} topScore={} paths={}",
                    projectId, lines.size(), topScore[0], summarizePaths(lines));
            return String.join("\n\n---\n\n", lines);
        } catch (Exception e) {
            log.warn("Neo4j semantic search failed project={}: {}", projectId, e.getMessage());
            return "Semantic search error: " + e.getMessage();
        }
    }

    private static String summarizePaths(List<String> lines) {
        List<String> paths = new ArrayList<>();
        for (String line : lines) {
            int bracket = line.indexOf(']');
            if (bracket >= 0 && bracket + 2 < line.length()) {
                String rest = line.substring(bracket + 2);
                int newline = rest.indexOf('\n');
                paths.add(newline >= 0 ? rest.substring(0, newline) : rest);
            }
        }
        return String.join(", ", paths);
    }

    private static String currentProjectId() {
        String prefix = SandboxContext.get();
        return prefix == null ? "" : SandboxContext.normalize(prefix);
    }

    private static String normalizePath(String path) {
        return SandboxContext.normalize(path == null ? "" : path);
    }

    private static boolean isIndexable(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        String lower = path.toLowerCase(Locale.ROOT);
        return INDEXABLE_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    private static String classifyKind(String path) {
        String normalized = normalizePath(path);
        if (normalized.startsWith("tests/")) {
            return "test";
        }
        if (normalized.startsWith("docs/")) {
            return "doc";
        }
        if (normalized.contains("requirements") || normalized.contains("architecture")) {
            return "artifact";
        }
        return "source";
    }
}
