package com.aidevassistant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class PersonaService {

    private static final Logger log = LoggerFactory.getLogger(PersonaService.class);

    @Value("${personas.root.path}")
    private String personasRoot;

    public String load(String personaPath) {
        try {
            if (personaPath == null || personaPath.isBlank()) {
                return null;
            }

            Path path = Paths.get(personasRoot, personaPath);
            if (!Files.exists(path) || !Files.isReadable(path)) {
                log.warn("Persona not found or unreadable: {}", path.toAbsolutePath());
                return null;
            }

            return Files.readString(path);
        } catch (Exception e) {
            log.error("Failed to load persona {}", personaPath, e);
            return null;
        }
    }
}
