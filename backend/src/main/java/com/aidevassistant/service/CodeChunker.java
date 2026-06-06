package com.aidevassistant.service;

import java.util.ArrayList;
import java.util.List;

final class CodeChunker {

    private static final int MAX_CHARS = 1800;
    private static final int OVERLAP = 120;

    private CodeChunker() {
    }

    static List<String> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        if (text.length() <= MAX_CHARS) {
            return List.of(text);
        }
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + MAX_CHARS);
            chunks.add(text.substring(start, end));
            if (end >= text.length()) {
                break;
            }
            start = Math.max(0, end - OVERLAP);
        }
        return chunks;
    }

    static int overlapChars() {
        return OVERLAP;
    }
}
