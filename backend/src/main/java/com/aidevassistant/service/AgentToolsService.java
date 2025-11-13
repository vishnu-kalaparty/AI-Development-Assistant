package com.aidevassistant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class AgentToolsService {
    
    private static final Logger log = LoggerFactory.getLogger(AgentToolsService.class);
    
    @Value("${sandbox.root.path}")
    private String sandboxRoot;
    
    private Path getSandboxRoot() {
        Path root = Paths.get(sandboxRoot).toAbsolutePath();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            log.error("Failed to create sandbox directory", e);
        }
        return root;
    }
    
    private Path validatePath(String pathStr) throws SecurityException {
        try {
            Path sandboxPath = getSandboxRoot();
            Path resolved = sandboxPath.resolve(pathStr).normalize();
            
            if (!resolved.startsWith(sandboxPath)) {
                throw new SecurityException("Access denied - path outside sandbox: " + pathStr);
            }
            
            return resolved;
        } catch (Exception e) {
            throw new SecurityException("Invalid path: " + pathStr);
        }
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
            
            Files.createDirectories(safePath.getParent());
            Files.writeString(safePath, content);
            
            return "Successfully wrote " + content.length() + " characters to " + filePath;
            
        } catch (SecurityException e) {
            return "Security Error: " + e.getMessage();
        } catch (IOException e) {
            return "Error writing file: " + e.getMessage();
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
            
            List<String> files = new ArrayList<>();
            
            if (recursive) {
                try (Stream<Path> paths = Files.walk(safePath)) {
                    files = paths
                            .filter(p -> !p.equals(safePath))
                            .map(p -> {
                                String relative = safePath.relativize(p).toString();
                                return Files.isDirectory(p) ? relative + "/" : relative;
                            })
                            .sorted()
                            .collect(Collectors.toList());
                }
            } else {
                try (Stream<Path> paths = Files.list(safePath)) {
                    files = paths
                            .map(p -> {
                                String name = p.getFileName().toString();
                                return Files.isDirectory(p) ? name + "/" : name;
                            })
                            .sorted()
                            .collect(Collectors.toList());
                }
            }
            
            if (files.isEmpty()) {
                return "No files found in sandbox directory " + directoryPath;
            }
            
            StringBuilder result = new StringBuilder("Files in sandbox directory " + directoryPath + ":\n");
            for (String file : files) {
                result.append("  - ").append(file).append("\n");
            }
            
            return result.toString();
            
        } catch (SecurityException e) {
            return "Security Error: " + e.getMessage();
        } catch (IOException e) {
            return "Error listing files: " + e.getMessage();
        }
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
            log.info("Deleted file: " + filePath);
            
        } catch (SecurityException e) {
            throw new IOException("Security Error: " + e.getMessage());
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
                                String relativePath = safePath.relativize(file).toString();
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
