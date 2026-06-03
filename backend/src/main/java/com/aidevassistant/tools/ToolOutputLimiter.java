package com.aidevassistant.tools;

final class ToolOutputLimiter {

    static final int MAX_CHARS = 8_000;

    private ToolOutputLimiter() {}

    static String truncate(String text) {
        if (text == null) {
            return "";
        }
        if (text.length() <= MAX_CHARS) {
            return text;
        }
        return text.substring(0, MAX_CHARS) + "\n...truncated";
    }
}
