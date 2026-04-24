package com.aidevassistant.service;

import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.Schema;
import com.google.genai.types.Tool;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ToolRegistry {

    private static final int MAX_RESULT_CHARS = 8_000;

    private final AgentToolsService agentToolsService;
    private final ExecutionService executionService;

    public ToolRegistry(AgentToolsService agentToolsService, ExecutionService executionService) {
        this.agentToolsService = agentToolsService;
        this.executionService = executionService;
    }

    public List<Tool> declarations() {
        FunctionDeclaration listFiles = FunctionDeclaration.builder()
                .name("list_files")
                .description("List files in a sandbox directory. Paths are relative to data/sandbox.")
                .parameters(objectSchema(Map.of(
                        "directory", stringDesc("Directory relative to sandbox. Use empty string for the root."),
                        "recursive", Schema.builder().type("BOOLEAN").description("If true, list files recursively.").build()
                ), List.of()))
                .build();

        FunctionDeclaration readFile = FunctionDeclaration.builder()
                .name("read_file")
                .description("Read a text file from the sandbox.")
                .parameters(objectSchema(Map.of(
                        "path", stringDesc("File path relative to the sandbox, e.g. fizzbuzz.py")
                ), List.of("path")))
                .build();

        FunctionDeclaration writeFile = FunctionDeclaration.builder()
                .name("write_file")
                .description("Write a text file inside the sandbox. Creates parent directories if needed.")
                .parameters(objectSchema(Map.of(
                        "path", stringDesc("File path relative to the sandbox"),
                        "content", stringDesc("Full file contents to write")
                ), List.of("path", "content")))
                .build();

        FunctionDeclaration searchFiles = FunctionDeclaration.builder()
                .name("search_files")
                .description("Search file contents in the sandbox for a substring.")
                .parameters(objectSchema(Map.of(
                        "directory", stringDesc("Directory relative to sandbox. Empty string for root."),
                        "pattern", stringDesc("Substring to search for"),
                        "case_sensitive", Schema.builder().type("BOOLEAN").description("Case-sensitive search. Defaults to false.").build()
                ), List.of("pattern")))
                .build();

        FunctionDeclaration runCode = FunctionDeclaration.builder()
                .name("run_code")
                .description("Execute a .py, .js, or .java file already in the sandbox. Working directory is the sandbox. Times out after a few seconds.")
                .parameters(objectSchema(Map.of(
                        "path", stringDesc("File path relative to the sandbox")
                ), List.of("path")))
                .build();

        return List.of(Tool.builder()
                .functionDeclarations(List.of(listFiles, readFile, writeFile, searchFiles, runCode))
                .build());
    }

    public String execute(String name, Map<String, Object> args) {
        if (args == null) {
            args = Map.of();
        }
        String result;
        switch (name) {
            case "list_files" -> result = agentToolsService.listFiles(
                    stringArg(args, "directory", ""),
                    boolArg(args, "recursive", false)
            );
            case "read_file" -> result = agentToolsService.readFile(stringArg(args, "path", ""));
            case "write_file" -> result = agentToolsService.writeFile(
                    stringArg(args, "path", ""),
                    stringArg(args, "content", "")
            );
            case "search_files" -> result = agentToolsService.searchFiles(
                    stringArg(args, "directory", ""),
                    stringArg(args, "pattern", ""),
                    boolArg(args, "case_sensitive", false)
            );
            case "run_code" -> result = executionService.runFile(stringArg(args, "path", ""));
            default -> result = "Error: unknown tool " + name;
        }
        return truncate(result);
    }

    private static Schema stringDesc(String description) {
        return Schema.builder().type("STRING").description(description).build();
    }

    private static Schema objectSchema(Map<String, Schema> properties, List<String> required) {
        Map<String, Schema> ordered = new LinkedHashMap<>(properties);
        Schema.Builder builder = Schema.builder()
                .type("OBJECT")
                .properties(ordered);
        if (required != null && !required.isEmpty()) {
            builder.required(required);
        }
        return builder.build();
    }

    private static String stringArg(Map<String, Object> args, String key, String fallback) {
        Object value = args.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static boolean boolArg(Map<String, Object> args, String key, boolean fallback) {
        Object value = args.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        if (text.length() <= MAX_RESULT_CHARS) {
            return text;
        }
        return text.substring(0, MAX_RESULT_CHARS) + "\n...truncated";
    }
}
