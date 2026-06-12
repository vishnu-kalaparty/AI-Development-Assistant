package com.aidevassistant.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeChunkerTest {

    @Test
    void returnsSingleChunkForShortText() {
        assertEquals(1, CodeChunker.chunk("hello").size());
    }

    @Test
    void splitsLongTextIntoMultipleChunks() {
        String text = "a".repeat(4000);
        assertTrue(CodeChunker.chunk(text).size() > 1);
    }

    @Test
    void reconstructFromChunksRejoinsOverlap() {
        String original = "x".repeat(2000);
        String rebuilt = CodeIndexService.reconstructFromChunks(CodeChunker.chunk(original));
        assertEquals(original, rebuilt);
    }
}
