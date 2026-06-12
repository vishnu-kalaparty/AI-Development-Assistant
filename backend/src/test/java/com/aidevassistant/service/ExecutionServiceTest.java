package com.aidevassistant.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionServiceTest {

    @TempDir
    Path tempDir;

    private ExecutionService execution;
    private AgentToolsService tools;

    @BeforeEach
    void setUp() {
        tools = new AgentToolsService(tempDir.toString());
        execution = new ExecutionService(tools, 1);
    }

    @Test
    void rejectsUnsupportedType() throws Exception {
        Files.writeString(tempDir.resolve("notes.txt"), "hello");
        String result = execution.runFile("notes.txt");
        assertTrue(result.contains("Unsupported"), result);
    }

    @Test
    void rejectsPathTraversal() {
        String result = execution.runFile("../outside.py");
        assertTrue(result.startsWith("Security Error:"), result);
    }

    @Test
    @EnabledIf("pythonAvailable")
    void timesOutLongRunningProcess() {
        tools.writeFile("slow.py", "import time\ntime.sleep(20)\nprint('done')\n");
        String result = execution.runFile("slow.py");
        assertTrue(result.toLowerCase().contains("timed out"), result);
    }

    @Test
    @EnabledIf("pythonAvailable")
    void stripsSecretEnvironmentVariables() {
        tools.writeFile("env.py", """
                import os
                print('GOOGLE_AI_API_KEY=' + os.environ.get('GOOGLE_AI_API_KEY', 'absent'))
                print('FAKE_SECRET=' + os.environ.get('FAKE_SECRET', 'absent'))
                """);
        String result = execution.runFile("env.py");
        assertTrue(result.contains("exit=0"), result);
        String apiKey = System.getenv("GOOGLE_AI_API_KEY");
        if (apiKey != null && !apiKey.isBlank()) {
            assertFalse(result.contains(apiKey), "API key leaked into subprocess output");
        }
        assertTrue(result.contains("GOOGLE_AI_API_KEY=absent") || !result.contains(apiKey == null ? "x" : apiKey), result);
    }

    @Test
    @EnabledIf("pythonAvailable")
    void pythonTestsCanImportFromSrc() throws Exception {
        Path project = tempDir.resolve("runs/basic-calculator-cli-in-python");
        Files.createDirectories(project.resolve("src"));
        Files.createDirectories(project.resolve("tests"));
        Files.writeString(project.resolve("src/calculator.py"), """
                def calculate(a, b, op):
                    if op == '+':
                        return a + b
                    return a - b

                def main():
                    pass
                """);
        Files.writeString(project.resolve("tests/test_calculator.py"), """
                from calculator import calculate, main
                assert calculate(1, 2, '+') == 3
                print('ok')
                """);
        String result = execution.runFile("runs/basic-calculator-cli-in-python/tests/test_calculator.py");
        assertTrue(result.contains("exit=0"), result);
        assertTrue(result.contains("ok"), result);
    }

    @Test
    void resolvePythonProjectRootFindsSrcLayout() throws Exception {
        Path project = tempDir.resolve("runs/demo");
        Files.createDirectories(project.resolve("src"));
        Files.createDirectories(project.resolve("tests"));
        Path testFile = project.resolve("tests/test_x.py");
        Files.writeString(testFile, "pass");

        Path root = ExecutionService.resolvePythonProjectRoot(testFile, tempDir);
        assertTrue(root.endsWith("demo"), root.toString());
    }

    static boolean pythonAvailable() {
        String runtime = new ExecutionService(
                new AgentToolsService(System.getProperty("java.io.tmpdir")),
                1
        ).describeRuntime();
        return runtime.contains("python=") && !runtime.contains("python=missing");
    }
}
