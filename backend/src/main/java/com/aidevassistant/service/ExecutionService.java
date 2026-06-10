package com.aidevassistant.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionService.class);
    private static final int MAX_OUTPUT_CHARS = 32_000;
    private static final Pattern PUBLIC_CLASS = Pattern.compile("public\\s+class\\s+(\\w+)");
    private static final Pattern ANY_CLASS = Pattern.compile("(?:public\\s+)?class\\s+(\\w+)");
    private static final Pattern PACKAGE = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
    private static final List<String> EXTRA_BIN_DIRS = List.of(
            "/opt/homebrew/bin",
            "/usr/local/bin",
            "/usr/bin",
            "/bin",
            "/opt/java/openjdk/bin"
    );

    private final AgentToolsService agentToolsService;
    private final int timeoutSeconds;

    public ExecutionService(
            AgentToolsService agentToolsService,
            @Value("${execution.timeout-seconds:15}") int timeoutSeconds
    ) {
        this.agentToolsService = agentToolsService;
        this.timeoutSeconds = timeoutSeconds;
    }

    @PostConstruct
    void logRuntime() {
        log.info("Sandbox interpreters: python={}, java={}, javac={}",
                findExecutable("python3", "python"),
                findExecutable("java"),
                findExecutable("javac"));
    }

    public String describeRuntime() {
        return "python=" + orMissing(findExecutable("python3", "python"))
                + ", java=" + orMissing(findExecutable("java"))
                + ", javac=" + orMissing(findExecutable("javac"));
    }

    public String runFile(String filePath) {
        try {
            Path safePath = agentToolsService.resolveSafePath(filePath);
            Path sandbox = agentToolsService.getSandboxRootPath();
            return runAbsoluteFile(safePath, sandbox);
        } catch (SecurityException e) {
            return "Security Error: " + e.getMessage();
        } catch (Exception e) {
            log.error("Failed to run {}", filePath, e);
            return "Error running file: " + e.getMessage();
        }
    }

    /** Runs a file from an absolute path (e.g. materialized draft sandbox in a temp directory). */
    public String runAbsoluteFile(Path absoluteFile, Path workingDir) {
        try {
            if (!Files.exists(absoluteFile) || !Files.isRegularFile(absoluteFile)) {
                return "Error: File not found: " + absoluteFile;
            }

            String lower = absoluteFile.getFileName().toString().toLowerCase(Locale.ROOT);
            if (lower.endsWith(".py")) {
                return runPython(absoluteFile, workingDir);
            }
            if (lower.endsWith(".js")) {
                Path node = findExecutable("node");
                if (node == null) {
                    return "Error: node not found. Install Node.js or run a .py / .java file.";
                }
                return execute(List.of(node.toString(), absoluteFile.toString()), workingDir);
            }
            if (lower.endsWith(".java")) {
                return runJava(absoluteFile, workingDir);
            }
            return "Error: Unsupported file type. Use .py, .js, or .java";
        } catch (Exception e) {
            log.error("Failed to run {}", absoluteFile, e);
            return "Error running file: " + e.getMessage();
        }
    }

    private String runPython(Path file, Path sandbox) {
        Path python = findExecutable("python3", "python");
        if (python == null) {
            return "Error: python3 not found on PATH (" + describeRuntime() + ")";
        }
        Path projectRoot = resolvePythonProjectRoot(file, sandbox);
        Path workingDir = projectRoot != null ? projectRoot : sandbox;
        Map<String, String> extraEnv = new LinkedHashMap<>();
        String pythonPath = buildPythonPath(file, projectRoot);
        if (!pythonPath.isBlank()) {
            extraEnv.put("PYTHONPATH", pythonPath);
        }
        return execute(List.of(python.toString(), "-u", file.toString()), workingDir, extraEnv);
    }

    /**
     * Walk upward from the script to find a Python project root (directory with {@code src/} or {@code tests/}).
     */
    static Path resolvePythonProjectRoot(Path file, Path sandbox) {
        Path dir = file.getParent();
        while (dir != null && (dir.startsWith(sandbox) || dir.equals(sandbox))) {
            boolean hasSrc = Files.isDirectory(dir.resolve("src"));
            boolean hasTests = Files.isDirectory(dir.resolve("tests"));
            if (hasSrc || hasTests) {
                return dir;
            }
            if (dir.equals(sandbox)) {
                break;
            }
            dir = dir.getParent();
        }
        return file.getParent();
    }

    static String buildPythonPath(Path file, Path projectRoot) {
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        Path parent = file.getParent();
        if (parent != null) {
            paths.add(parent.toAbsolutePath().toString());
        }
        if (projectRoot != null) {
            Path src = projectRoot.resolve("src");
            if (Files.isDirectory(src)) {
                paths.add(src.toAbsolutePath().toString());
            }
            paths.add(projectRoot.toAbsolutePath().toString());
        }
        String existing = System.getenv("PYTHONPATH");
        if (existing != null && !existing.isBlank()) {
            for (String part : existing.split(java.io.File.pathSeparator)) {
                if (!part.isBlank()) {
                    paths.add(part);
                }
            }
        }
        return String.join(File.pathSeparator, paths);
    }

    private String runJava(Path file, Path sandbox) throws IOException {
        Path java = findExecutable("java");
        if (java == null) {
            return "Error: java not found on PATH (" + describeRuntime() + ")";
        }

        // JDK 11+ can run a single source file without a separate javac step.
        String sourceRun = execute(List.of(java.toString(), file.toString()), sandbox);
        if (sourceRun.startsWith("exit=0")) {
            return sourceRun;
        }

        Path javac = findExecutable("javac");
        if (javac == null) {
            return "Java run failed (javac not installed, source-file launch also failed):\n"
                    + sourceRun + "\nRuntime: " + describeRuntime();
        }

        String compile = execute(List.of(javac.toString(), file.getFileName().toString()), file.getParent());
        if (!compile.startsWith("exit=0")) {
            return "Compile failed:\n" + compile;
        }

        String source = Files.readString(file);
        Matcher pkg = PACKAGE.matcher(source);
        if (pkg.find()) {
            return "Error: Java sandbox runs do not support packages. Remove the package declaration.";
        }

        String className = classNameFrom(source, file);
        Path classDir = file.getParent() != null ? file.getParent() : sandbox;
        return execute(List.of(java.toString(), "-cp", classDir.toString(), className), sandbox);
    }

    private static String classNameFrom(String source, Path file) {
        Matcher pub = PUBLIC_CLASS.matcher(source);
        if (pub.find()) {
            return pub.group(1);
        }
        Matcher any = ANY_CLASS.matcher(source);
        if (any.find()) {
            return any.group(1);
        }
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private Path findExecutable(String... names) {
        LinkedHashSet<Path> dirs = new LinkedHashSet<>();
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String dir : pathEnv.split(File.pathSeparator)) {
                if (!dir.isBlank()) {
                    dirs.add(Paths.get(dir));
                }
            }
        }
        String javaHome = firstNonBlank(System.getenv("JAVA_HOME"), System.getProperty("java.home"));
        if (javaHome != null) {
            dirs.add(Paths.get(javaHome, "bin"));
        }
        for (String extra : EXTRA_BIN_DIRS) {
            dirs.add(Paths.get(extra));
        }

        for (String name : names) {
            for (Path dir : dirs) {
                Path candidate = dir.resolve(name);
                if (Files.isExecutable(candidate) && Files.isRegularFile(candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private String execute(List<String> command, Path workingDir) {
        return execute(command, workingDir, Map.of());
    }

    private String execute(List<String> command, Path workingDir, Map<String, String> extraEnv) {
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(new ArrayList<>(command));
            builder.directory(workingDir.toFile());
            builder.redirectErrorStream(true);
            sanitizeEnvironment(builder.environment(), workingDir);
            if (extraEnv != null) {
                extraEnv.forEach((key, value) -> {
                    if (key != null && !key.isBlank() && value != null && !value.isBlank()) {
                        builder.environment().put(key, value);
                    }
                });
            }

            log.info("Executing in {}: {}", workingDir, command);
            process = builder.start();
            StreamCollector collector = new StreamCollector(process.getInputStream());
            Thread thread = new Thread(collector, "exec-output");
            thread.setDaemon(true);
            thread.start();

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                thread.join(1000);
                return "Error: timed out after " + timeoutSeconds + "s";
            }

            thread.join(1000);
            int exit = process.exitValue();
            String output = collector.getOutput();
            if (output.length() > MAX_OUTPUT_CHARS) {
                output = output.substring(0, MAX_OUTPUT_CHARS) + "\n...truncated";
            }
            if (output.isBlank()) {
                return "exit=" + exit + "\n(no output)";
            }
            return "exit=" + exit + "\n" + output;
        } catch (IOException e) {
            log.warn("Failed to start {}: {}", command, e.getMessage());
            return "Error: " + e.getMessage() + " [" + describeRuntime() + "]";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) {
                process.destroyForcibly();
            }
            return "Error: execution interrupted";
        }
    }

    private void sanitizeEnvironment(Map<String, String> env, Path workingDir) {
        Set<String> keys = new LinkedHashSet<>(env.keySet());
        for (String key : keys) {
            String upper = key.toUpperCase(Locale.ROOT);
            if (upper.contains("API_KEY") || upper.contains("SECRET") || upper.contains("TOKEN")
                    || upper.contains("PASSWORD") || upper.contains("CREDENTIAL")) {
                env.remove(key);
            }
        }
        String path = env.get("PATH");
        if (path == null || path.isBlank()) {
            path = System.getenv("PATH");
        }
        String extra = String.join(File.pathSeparator, EXTRA_BIN_DIRS);
        env.put("PATH", (path == null || path.isBlank()) ? extra : path + File.pathSeparator + extra);

        String javaHome = firstNonBlank(env.get("JAVA_HOME"), System.getenv("JAVA_HOME"), System.getProperty("java.home"));
        if (javaHome != null) {
            env.put("JAVA_HOME", javaHome);
        }
        env.putIfAbsent("LANG", "en_US.UTF-8");
        env.put("HOME", workingDir.toAbsolutePath().toString());
        env.put("PYTHONUNBUFFERED", "1");
        env.put("PYTHONDONTWRITEBYTECODE", "1");
        env.put("JAVA_TOOL_OPTIONS", "-Xmx256m");
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String orMissing(Path path) {
        return path == null ? "missing" : path.toString();
    }

    private static class StreamCollector implements Runnable {
        private final InputStream input;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        StreamCollector(InputStream input) {
            this.input = input;
        }

        @Override
        public void run() {
            try {
                input.transferTo(buffer);
            } catch (IOException ignored) {
                // Process ended or was destroyed.
            }
        }

        String getOutput() {
            return buffer.toString(StandardCharsets.UTF_8);
        }
    }
}
