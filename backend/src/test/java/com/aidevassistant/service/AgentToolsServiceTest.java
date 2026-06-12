package com.aidevassistant.service;

import com.aidevassistant.dto.SandboxNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentToolsServiceTest {

    @TempDir
    Path tempDir;

    private AgentToolsService tools;

    @BeforeEach
    void setUp() {
        tools = new AgentToolsService(tempDir.toString());
    }

    @Test
    void rejectsPathTraversal() {
        String result = tools.readFile("../secret.txt");
        assertTrue(result.startsWith("Security Error:"), result);
        assertTrue(result.toLowerCase().contains("sandbox") || result.toLowerCase().contains("denied")
                || result.toLowerCase().contains("invalid"), result);
    }

    @Test
    void rejectsNestedPathTraversal() {
        String result = tools.writeFile("src/../../outside.txt", "nope");
        assertTrue(result.startsWith("Security Error:"), result);
    }

    @Test
    void rejectsAbsolutePathOutsideSandbox() {
        String result = tools.writeFile("/tmp/outside.txt", "nope");
        assertTrue(result.startsWith("Security Error:"), result);
    }

    @Test
    void writeFileReportsLineCountsForNewAndUpdatedFiles() {
        String first = tools.writeFile("src/app.py", "line1\nline2\nline3");
        assertTrue(first.contains("3 lines (new file)"), first);
        assertTrue(first.contains("Successfully wrote src/app.py"), first);

        String second = tools.writeFile("src/app.py", "line1\nline2\nline3\nline4\nline5");
        assertTrue(second.contains("5 lines (was 3, +2)"), second);
    }

    @Test
    void countLinesHandlesEmptyAndMultilineContent() {
        assertEquals(0, AgentToolsService.countLines(""));
        assertEquals(1, AgentToolsService.countLines("single"));
        assertEquals(3, AgentToolsService.countLines("a\nb\nc"));
    }

    @Test
    void writesAndReadsInsideSandbox() {
        String write = tools.writeFile("hello.txt", "ok");
        assertTrue(write.startsWith("Successfully"), write);
        String read = tools.readFile("hello.txt");
        assertTrue(read.contains("ok"), read);
    }

    @Test
    void writesAndReadsNestedPaths() {
        String write = tools.writeFile("src/main/App.java", "class App {}");
        assertTrue(write.startsWith("Successfully"), write);
        assertTrue(Files.isDirectory(tempDir.resolve("src/main")));

        String read = tools.readFile("src/main/App.java");
        assertTrue(read.contains("class App"), read);
    }

    @Test
    void listFilesShowsNestedStructure() {
        tools.writeFile("hello.py", "print(1)");
        tools.writeFile("src/main/App.java", "class App {}");
        tools.mkdir("src/test");

        String listing = tools.listFiles("", true);
        assertTrue(listing.contains("hello.py"), listing);
        assertTrue(listing.contains("src/"), listing);
        assertTrue(listing.contains("src/main/"), listing);
        assertTrue(listing.contains("src/main/App.java"), listing);
        assertTrue(listing.contains("src/test/"), listing);

        List<String> files = tools.listFilePaths(true);
        assertTrue(files.contains("hello.py"), files.toString());
        assertTrue(files.contains("src/main/App.java"), files.toString());
        assertFalse(files.stream().anyMatch(f -> f.endsWith("/")), files.toString());
    }

    @Test
    void listTreeReturnsDirectoriesAndFiles() {
        tools.writeFile("src/util/Helper.java", "class Helper {}");
        tools.writeFile("readme.md", "# hi");

        List<SandboxNode> tree = tools.listTree();
        assertEquals(2, tree.size());

        SandboxNode readme = tree.stream().filter(n -> "readme.md".equals(n.getName())).findFirst().orElseThrow();
        assertEquals("file", readme.getType());
        assertEquals("readme.md", readme.getPath());

        SandboxNode src = tree.stream().filter(n -> "src".equals(n.getName())).findFirst().orElseThrow();
        assertEquals("directory", src.getType());
        assertEquals("src", src.getPath());
        assertEquals(1, src.getChildren().size());
        assertEquals("util", src.getChildren().get(0).getName());
        assertEquals("src/util/Helper.java", src.getChildren().get(0).getChildren().get(0).getPath());
    }

    @Test
    void mkdirCreatesNestedDirectory() {
        String result = tools.mkdir("docs/api");
        assertTrue(result.startsWith("Successfully"), result);
        assertTrue(Files.isDirectory(tempDir.resolve("docs/api")));
    }

    @Test
    void listSandboxFoldersReturnsTopLevelAndRunSubfolders() throws Exception {
        tools.mkdir("my-app");
        tools.mkdir("runs/wf-old");
        tools.writeFile("loose.txt", "x");

        List<String> folders = tools.listSandboxFolders();

        assertEquals(List.of("my-app", "runs/wf-old"), folders);
    }

    @Test
    void resolveWorkflowSandboxPathUsesFolderNameUnderRuns() {
        String path = tools.resolveWorkflowSandboxPath(null, "fizzbuzz-demo", "wf-1");
        assertEquals("runs/fizzbuzz-demo", path);
        assertFalse(Files.exists(tempDir.resolve("runs/fizzbuzz-demo")));
    }

    @Test
    void resolveWorkflowSandboxPathUsesExplicitRunsPath() {
        String path = tools.resolveWorkflowSandboxPath(null, "runs/custom-id", "wf-1");
        assertEquals("runs/custom-id", path);
    }

    @Test
    void resolveWorkflowSandboxPathPrefersFolderNameOverParent() {
        String path = tools.resolveWorkflowSandboxPath("my-app", "my-app-v2", "wf-1");
        assertEquals("my-app-v2", path);
    }

    @Test
    void resolveWorkflowSandboxPathFallsBackToParentFolder() {
        String path = tools.resolveWorkflowSandboxPath("shared", null, "wf-1");
        assertEquals("shared", path);
    }

    @Test
    void resolveWorkflowSandboxPathAutoRunWhenBlank() {
        String path = tools.resolveWorkflowSandboxPath(null, null, "abc-123");
        assertEquals("runs/abc-123", path);
    }

    @Test
    void rejectsInvalidFolderNameTraversal() {
        try {
            tools.validateSandboxFolderPath("../escape");
            org.junit.jupiter.api.Assertions.fail("expected rejection");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid"));
        }
    }

    @Test
    void rejectsAbsoluteFolderName() {
        try {
            tools.validateSandboxFolderPath("/tmp/outside");
            org.junit.jupiter.api.Assertions.fail("expected rejection");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Absolute"));
        }
    }

    @Test
    void resolveWorkflowSandboxPathCreatesRunFolderWhenBlank() {
        String path = tools.resolveWorkflowSandboxPath(null, null, "abc-123");
        assertEquals("runs/abc-123", path);
    }

    @Test
    void resolveWorkflowSandboxPathUsesExistingParentFolder() throws Exception {
        tools.writeFile("shared/base.py", "print('hi')");

        String path = tools.resolveWorkflowSandboxPath("shared", null, "wf-1");
        assertEquals("shared", path);

        try (com.aidevassistant.tools.SandboxContext.Scope ignored =
                     com.aidevassistant.tools.SandboxContext.scope("shared")) {
            assertTrue(tools.readFile("base.py").contains("print('hi')"));
        }
    }

    @Test
    void rejectsParentFolderTraversalInResolve() {
        try {
            tools.resolveWorkflowSandboxPath("../outside", null, "wf-1");
            org.junit.jupiter.api.Assertions.fail("expected rejection");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid"));
        }
    }

    @Test
    void sandboxContextScopesFileOperations() {
        tools.writeFile("proj/a.txt", "scoped");

        try (com.aidevassistant.tools.SandboxContext.Scope ignored =
                     com.aidevassistant.tools.SandboxContext.scope("proj")) {
            assertTrue(tools.readFile("a.txt").contains("scoped"));
            String listing = tools.listFiles("", false);
            assertTrue(listing.contains("a.txt"), listing);
        }

        String rootListing = tools.listFiles("", false);
        assertTrue(rootListing.contains("proj/"), rootListing);
    }
}
