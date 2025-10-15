package com.aidevassistant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class RequirementsService {
    
    private static final Logger log = LoggerFactory.getLogger(RequirementsService.class);
    
    @Value("${requirements.root.path}")
    private String requirementsRoot;
    
    private Path getRequirementsRoot() {
        Path root = Paths.get(requirementsRoot).toAbsolutePath();
        try {
            Files.createDirectories(root);
            Files.createDirectories(root.resolve("studio"));
        } catch (IOException e) {
            log.error("Failed to create requirements directory", e);
        }
        return root;
    }
    
    public List<String> listRequirementsFiles() {
        try {
            Path root = getRequirementsRoot();
            try (Stream<Path> paths = Files.list(root)) {
                return paths
                        .filter(Files::isRegularFile)
                        .filter(p -> {
                            String name = p.getFileName().toString().toLowerCase();
                            return name.endsWith(".txt") || name.endsWith(".md") || 
                                   name.endsWith(".doc") || name.endsWith(".docx");
                        })
                        .map(p -> p.getFileName().toString())
                        .sorted()
                        .collect(Collectors.toList());
            }
        } catch (IOException e) {
            log.error("Error listing requirements files", e);
            return new ArrayList<>();
        }
    }
    
    public String readRequirementsFile(String filename) {
        try {
            Path file = getRequirementsRoot().resolve(filename);
            if (!Files.exists(file)) {
                return null;
            }
            return Files.readString(file);
        } catch (IOException e) {
            log.error("Error reading requirements file", e);
            return null;
        }
    }
    
    public boolean saveRequirementsDocument(String content, String productName, String requirementType) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
            String timestamp = LocalDateTime.now().format(formatter);
            String filename = requirementType.replace(" ", "_") + "_" + 
                            productName.replace(" ", "_") + "_" + timestamp + ".txt";
            
            Path file = getRequirementsRoot().resolve(filename);
            
            StringBuilder fullContent = new StringBuilder();
            fullContent.append("Product: ").append(productName).append("\n");
            fullContent.append("Document Type: ").append(requirementType).append("\n");
            fullContent.append("Generated: ").append(LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
            fullContent.append("=".repeat(50)).append("\n\n");
            fullContent.append(content);
            
            Files.writeString(file, fullContent.toString());
            return true;
            
        } catch (IOException e) {
            log.error("Error saving requirements document", e);
            return false;
        }
    }
}
