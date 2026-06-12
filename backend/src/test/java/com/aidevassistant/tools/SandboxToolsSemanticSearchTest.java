package com.aidevassistant.tools;

import com.aidevassistant.service.AgentToolsService;
import com.aidevassistant.service.CodeIndexService;
import com.aidevassistant.service.ExecutionService;
import com.aidevassistant.service.OllamaEmbeddingService;
import com.aidevassistant.service.WorkflowDraftStore;
import com.aidevassistant.service.WorkflowRunTracker;
import com.aidevassistant.service.WorkflowSnapshotStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Live check: Neo4j index + Ollama embeddings + SandboxTools.semantic_search path.
 * Run with: SEMANTIC_SEARCH_IT=1 mvn -Dtest=SandboxToolsSemanticSearchTest test
 */
@EnabledIfEnvironmentVariable(named = "SEMANTIC_SEARCH_IT", matches = "1")
class SandboxToolsSemanticSearchTest {

    private static final String PROJECT = "runs/python-cli-notes-app";

    private SandboxTools sandboxTools;
    private WorkflowDraftStore draftStore;

    @BeforeEach
    void setUp() {
        Assumptions.assumeTrue(ollamaReachable(), "Ollama not reachable on localhost:11434");
        String neo4jUri = System.getenv().getOrDefault("NEO4J_URI", "bolt://localhost:7687");
        String neo4jUser = System.getenv().getOrDefault("NEO4J_USERNAME", "neo4j");
        String neo4jPass = System.getenv().getOrDefault("NEO4J_PASSWORD", "password");
        int dims = Integer.parseInt(System.getenv().getOrDefault("NEO4J_VECTOR_DIMENSIONS", "1024"));
        String embedModel = System.getenv().getOrDefault("OLLAMA_EMBEDDING_MODEL", "mxbai-embed-large");

        Driver driver = GraphDatabase.driver(neo4jUri, AuthTokens.basic(neo4jUser, neo4jPass));
        try (var session = driver.session()) {
            session.run("RETURN 1").consume();
        } catch (Exception e) {
            Assumptions.assumeTrue(false, "Neo4j not reachable: " + e.getMessage());
        }

        AgentToolsService files = new AgentToolsService("data/sandbox");
        OllamaEmbeddingService embedding = new OllamaEmbeddingService(
                "http://localhost:11434", embedModel, new ObjectMapper());
        CodeIndexService index = new CodeIndexService(embedding, true, dims, driver);
        index.initializeSchema();

        StepRecorder recorder = new StepRecorder(new WorkflowSnapshotStore());
        draftStore = new WorkflowDraftStore();
        String workflowId = "wf-semantic-it";
        draftStore.begin(workflowId, PROJECT, true);
        WorkflowRunContext.setWorkflowId(workflowId);
        recorder.begin(workflowId, "implementation");

        sandboxTools = new SandboxTools(
                files,
                new ExecutionService(files, 5),
                recorder,
                index,
                new WorkflowRunTracker(),
                draftStore
        );
    }

    @Test
    void semanticSearchToolReturnsIndexedHits() {
        try (SandboxContext.Scope ignored = SandboxContext.scope(PROJECT)) {
            String result = sandboxTools.tools().semanticSearch("where are notes deleted or listed", 5, "");
            assertFalse(result.startsWith("Semantic search is disabled"), result);
            assertFalse(result.startsWith("No active project"), result);
            assertFalse(result.startsWith("Embedding failed"), result);
            assertFalse(result.startsWith("Semantic search error"), result);
            assertFalse(result.startsWith("No semantic matches"), result);
            assertTrue(result.contains(".py") || result.contains(".md"), result);
        }
    }

    private static boolean ollamaReachable() {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder().uri(URI.create("http://localhost:11434/api/tags")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }
}
