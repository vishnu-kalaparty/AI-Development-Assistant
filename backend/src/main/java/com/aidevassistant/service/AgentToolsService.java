package com.aidevassistant.service;

import com.aidevassistant.dto.SandboxNode;
import com.aidevassistant.tools.SandboxContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class AgentToolsService {

    private static final Logger log = LoggerFactory.getLogger(AgentToolsService.class);

    private final String sandboxRoot;

    public AgentToolsService(@Value("${sandbox.root.path}") String sandboxRoot) {
        this.sandboxRoot = sandboxRoot;
    }

    public Path getSandboxRootPath() {
        Path root = Paths.get(sandboxRoot).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            log.error("Failed to create sandbox directory", e);
        }
        return root;
    }

    public Path resolveSafePath(String pathStr) throws SecurityException {
        if (pathStr == null || pathStr.isBlank()) {
            return getEffectiveRoot();
        }
        return validatePath(pathStr);
    }

    /**
     * Validates and normalizes a sandbox-relative folder path (no disk writes).
     */
    public String validateSandboxFolderPath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Folder name is required");
        }
        String trimmed = path.trim();
        if (trimmed.startsWith("/") || trimmed.startsWith("\\") || trimmed.matches("^[A-Za-z]:.*")) {
            throw new IllegalArgumentException("Absolute paths are not allowed: " + path);
        }
        String normalized = SandboxContext.normalize(trimmed);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Invalid folder name");
        }
        if (hasTraversalSegment(normalized)) {
            throw new IllegalArgumentException("Invalid folder path: " + path);
        }
        for (String segment : normalized.split("/")) {
            if (segment.isBlank()) {
                throw new IllegalArgumentException("Invalid folder path: " + path);
            }
        }
        Path global = getSandboxRootPath();
        Path resolved = global.resolve(normalized).normalize();
        if (!resolved.startsWith(global)) {
            throw new IllegalArgumentException("Invalid folder path: " + path);
        }
        return normalized;
    }

    /**
     * Resolves the logical sandbox folder for a workflow run without creating directories.
     * folderName overrides parentFolder when set. Blank both → runs/{workflowId}.
     * Single-segment folderName with no parent → runs/{folderName}.
     */
    public String resolveWorkflowSandboxPath(String parentFolder, String folderName, String workflowId) {
        if (folderName != null && !folderName.isBlank()) {
            String target = validateSandboxFolderPath(folderName);
            if (!target.contains("/") && (parentFolder == null || parentFolder.isBlank())) {
                return "runs/" + target;
            }
            return target;
        }
        if (parentFolder != null && !parentFolder.isBlank()) {
            return validateSandboxFolderPath(parentFolder);
        }
        return "runs/" + workflowId;
    }

    public List<String> listFilePaths(String sandboxPrefix, boolean recursive) {
        try (SandboxContext.Scope ignored = SandboxContext.scope(sandboxPrefix)) {
            return listFilePaths(recursive);
        }
    }

    private Path getEffectiveRoot() {
        Path global = getSandboxRootPath();
        String prefix = SandboxContext.get();
        if (prefix == null || prefix.isBlank()) {
            return global;
        }
        Path effective = global.resolve(prefix).normalize();
        if (!effective.startsWith(global)) {
            throw new SecurityException("Access denied - invalid sandbox context: " + prefix);
        }
        return effective;
    }

    private Path validatePath(String pathStr) throws SecurityException {
        try {
            if (pathStr != null && (pathStr.contains("\0") || hasTraversalSegment(pathStr))) {
                throw new SecurityException("Access denied - path outside sandbox: " + pathStr);
            }

            Path global = getSandboxRootPath();
            Path effective = getEffectiveRoot();
            Path resolved = effective.resolve(pathStr == null ? "" : pathStr).normalize();

            if (!resolved.startsWith(global)) {
                throw new SecurityException("Access denied - path outside sandbox: " + pathStr);
            }

            return resolved;
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new SecurityException("Invalid path: " + pathStr);
        }
    }

    private static boolean hasTraversalSegment(String pathStr) {
        for (String segment : pathStr.replace('\\', '/').split("/")) {
            if ("..".equals(segment)) {
                return true;
            }
        }
        return false;
    }

    private String toSandboxRelative(Path absolute) {
        String relative = getEffectiveRoot().relativize(absolute).toString().replace('\\', '/');
        return relative.isEmpty() ? "" : relative;
    }

    public String readFile(String filePath) {
        try {
            Path safePath = validatePath(filePath);

            if (!Files.exists(safePath)) {
                return "Error: File not found in sandbox: " + filePath;
            }

            if (!Files.isRegularFile(safePath)) {
                return "Error: Path is not a file: " + filePath;
            }

            return Files.readString(safePath);

        } catch (SecurityException e) {
            return "Security Error: " + e.getMessage();
        } catch (IOException e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    public String writeFile(String filePath, String content) {
        try {
            Path safePath = validatePath(filePath);

            if (Files.exists(safePath) && Files.isDirectory(safePath)) {
                return "Error: Path is a directory, not a file: " + filePath;
            }

            String body = content == null ? "" : content;
            int previousLines = 0;
            boolean existed = Files.exists(safePath) && Files.isRegularFile(safePath);
            if (existed) {
                previousLines = countLines(Files.readString(safePath));
            }

            Path parent = safePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(safePath, body);

            int newLines = countLines(body);
            String relative = toSandboxRelative(safePath);
            String lineSummary = formatWriteLineSummary(existed, previousLines, newLines);
            return "Successfully wrote " + relative + ": " + lineSummary + " · " + body.length() + " chars";

        } catch (SecurityException e) {
            return "Security Error: " + e.getMessage();
        } catch (IOException e) {
            return "Error writing file: " + e.getMessage();
        }
    }

    static int countLines(String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        return content.split("\n", -1).length;
    }

    public static String formatWriteLineSummary(boolean existed, int previousLines, int newLines) {
        if (!existed) {
            return newLines + " lines (new file)";
        }
        int delta = newLines - previousLines;
        String deltaText = delta > 0 ? "+" + delta : String.valueOf(delta);
        return newLines + " lines (was " + previousLines + ", " + deltaText + ")";
    }

    public String mkdir(String directoryPath) {
        try {
            Path safePath = validatePath(directoryPath);

            if (Files.exists(safePath) && Files.isRegularFile(safePath)) {
                return "Error: Path is a file, not a directory: " + directoryPath;
            }

            Files.createDirectories(safePath);
            return "Successfully created directory " + toSandboxRelative(safePath);

        } catch (SecurityException e) {
            return "Security Error: " + e.getMessage();
        } catch (IOException e) {
            return "Error creating directory: " + e.getMessage();
        }
    }

    public String listFiles(String directoryPath, boolean recursive) {
        try {
            Path safePath = validatePath(directoryPath);

            if (!Files.exists(safePath)) {
                return "Error: Directory not found in sandbox: " + directoryPath;
            }

            if (!Files.isDirectory(safePath)) {
                return "Error: Path is not a directory: " + directoryPath;
            }

            List<String> entries = listEntryNames(safePath, recursive);

            if (entries.isEmpty()) {
                return "No files found in sandbox directory " + (directoryPath == null || directoryPath.isBlank() ? "." : directoryPath);
            }

            String label = directoryPath == null || directoryPath.isBlank() ? "." : directoryPath;
            StringBuilder result = new StringBuilder("Files in sandbox directory " + label + ":\n");
            for (String entry : entries) {
                result.append("  - ").append(entry).append("\n");
            }

            return result.toString();

        } catch (SecurityException e) {
            return "Security Error: " + e.getMessage();
        } catch (IOException e) {
            return "Error listing files: " + e.getMessage();
        }
    }

    public List<String> listFilePaths(boolean recursive) {
        try {
            Path root = getEffectiveRoot();
            if (!Files.exists(root)) {
                return List.of();
            }
            return listEntryNames(root, recursive).stream()
                    .filter(name -> !name.endsWith("/"))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.warn("Failed to list sandbox files", e);
            return List.of();
        }
    }

    public List<SandboxNode> listTree() {
        try {
            return buildTree(getEffectiveRoot(), "");
        } catch (IOException e) {
            log.warn("Failed to build sandbox tree", e);
            return List.of();
        }
    }

    /**
     * Lists selectable sandbox folders: top-level directories plus individual run folders under runs/.
     */
    public List<String> listSandboxFolders() {
        Path root = getSandboxRootPath();
        if (!Files.exists(root)) {
            return List.of();
        }
        List<String> folders = new ArrayList<>();
        try (Stream<Path> paths = Files.list(root)) {
            List<Path> topLevel = paths
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .collect(Collectors.toList());
            for (Path dir : topLevel) {
                String name = dir.getFileName().toString();
                if ("runs".equals(name)) {
                    addRunSubfolders(dir, folders);
                } else {
                    folders.add(name);
                }
            }
        } catch (IOException e) {
            log.warn("Failed to list sandbox folders", e);
        }
        return folders;
    }

    private void addRunSubfolders(Path runsDir, List<String> folders) {
        if (!Files.isDirectory(runsDir)) {
            return;
        }
        try (Stream<Path> runPaths = Files.list(runsDir)) {
            runPaths.filter(Files::isDirectory)
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .forEach(runDir -> folders.add("runs/" + runDir.getFileName().toString()));
        } catch (IOException e) {
            log.warn("Failed to list run folders under runs/", e);
        }
    }

    private List<String> listEntryNames(Path directory, boolean recursive) throws IOException {
        List<String> entries = new ArrayList<>();
        if (recursive) {
            try (Stream<Path> paths = Files.walk(directory)) {
                paths.filter(p -> !p.equals(directory))
                        .sorted()
                        .forEach(p -> {
                            String relative = directory.relativize(p).toString().replace('\\', '/');
                            entries.add(Files.isDirectory(p) ? relative + "/" : relative);
                        });
            }
        } else {
            try (Stream<Path> paths = Files.list(directory)) {
                paths.sorted(Comparator.comparing(p -> p.getFileName().toString()))
                        .forEach(p -> {
                            String name = p.getFileName().toString();
                            entries.add(Files.isDirectory(p) ? name + "/" : name);
                        });
            }
        }
        return entries;
    }

    private List<SandboxNode> buildTree(Path directory, String relativePrefix) throws IOException {
        List<SandboxNode> nodes = new ArrayList<>();
        try (Stream<Path> paths = Files.list(directory)) {
            List<Path> children = paths
                    .sorted(Comparator
                            .comparing((Path p) -> !Files.isDirectory(p))
                            .thenComparing(p -> p.getFileName().toString()))
                    .collect(Collectors.toList());

            for (Path child : children) {
                String name = child.getFileName().toString();
                String path = relativePrefix.isEmpty() ? name : relativePrefix + "/" + name;
                if (Files.isDirectory(child)) {
                    SandboxNode dir = new SandboxNode(name, path, "directory");
                    dir.setChildren(buildTree(child, path));
                    nodes.add(dir);
                } else if (Files.isRegularFile(child)) {
                    nodes.add(new SandboxNode(name, path, "file"));
                }
            }
        }
        return nodes;
    }

    public void deleteFile(String filePath) throws IOException {
        try {
            Path safePath = validatePath(filePath);

            if (!Files.exists(safePath)) {
                throw new IOException("File not found: " + filePath);
            }

            if (!Files.isRegularFile(safePath)) {
                throw new IOException("Path is not a file: " + filePath);
            }

            Files.delete(safePath);
            log.info("Deleted file: {}", filePath);

        } catch (SecurityException e) {
            throw new IOException("Security Error: " + e.getMessage());
        }
    }

    public String deleteDirectory(String directoryPath, boolean recursive) {
        try {
            Path safePath = validatePath(directoryPath);
            Path root = getSandboxRootPath();

            if (safePath.equals(root)) {
                return "Error: Cannot delete the sandbox root";
            }

            if (!Files.exists(safePath)) {
                return "Error: Directory not found in sandbox: " + directoryPath;
            }

            if (!Files.isDirectory(safePath)) {
                return "Error: Path is not a directory: " + directoryPath;
            }

            if (recursive) {
                try (Stream<Path> walk = Files.walk(safePath)) {
                    List<Path> toDelete = walk
                            .sorted(Comparator.reverseOrder())
                            .collect(Collectors.toList());
                    for (Path path : toDelete) {
                        Files.deleteIfExists(path);
                    }
                }
            } else {
                Files.delete(safePath);
            }

            return "Successfully deleted directory " + toSandboxRelative(safePath);

        } catch (DirectoryNotEmptyException e) {
            return "Error: Directory is not empty. Pass recursive=true to delete contents.";
        } catch (SecurityException e) {
            return "Security Error: " + e.getMessage();
        } catch (IOException e) {
            return "Error deleting directory: " + e.getMessage();
        }
    }

    public String searchFiles(String directoryPath, String pattern, boolean caseSensitive) {
        try {
            Path safePath = validatePath(directoryPath);

            if (!Files.exists(safePath)) {
                return "Error: Directory not found in sandbox: " + directoryPath;
            }

            if (!Files.isDirectory(safePath)) {
                return "Error: Path is not a directory: " + directoryPath;
            }

            List<String> matches = new ArrayList<>();
            String searchPattern = caseSensitive ? pattern : pattern.toLowerCase();

            try (Stream<Path> paths = Files.walk(safePath)) {
                paths.filter(Files::isRegularFile).forEach(file -> {
                    try {
                        List<String> lines = Files.readAllLines(file);
                        for (int i = 0; i < lines.size(); i++) {
                            String line = lines.get(i);
                            String searchLine = caseSensitive ? line : line.toLowerCase();

                            if (searchLine.contains(searchPattern)) {
                                String relativePath = safePath.relativize(file).toString().replace('\\', '/');
                                matches.add(String.format("%s:%d: %s", relativePath, i + 1, line.trim()));
                            }
                        }
                    } catch (IOException e) {
                        // Skipping files that can't be read
                    }
                });
            }

            if (matches.isEmpty()) {
                return "No matches found for pattern '" + pattern + "' in sandbox directory " + directoryPath;
            }

            StringBuilder result = new StringBuilder("Found " + matches.size() + " match(es) for '" + pattern + "':\n\n");
            String currentFile = null;

            for (String match : matches) {
                String[] parts = match.split(":", 3);
                if (parts.length >= 3) {
                    String file = parts[0];
                    if (!file.equals(currentFile)) {
                        currentFile = file;
                        result.append("\n").append(file).append(":\n");
                    }
                    result.append("  Line ").append(parts[1]).append(": ").append(parts[2]).append("\n");
                }
            }

            return result.toString();

        } catch (SecurityException e) {
            return "Security Error: " + e.getMessage();
        } catch (IOException e) {
            return "Error searching files: " + e.getMessage();
        }
    }
}
