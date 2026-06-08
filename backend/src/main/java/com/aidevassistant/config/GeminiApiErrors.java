package com.aidevassistant.config;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Gemini API quota / rate-limit errors into actionable user messages.
 */
public final class GeminiApiErrors {

    private static final Pattern RETRY_AFTER_SECONDS = Pattern.compile(
            "retry in ([\\d.]+)s",
            Pattern.CASE_INSENSITIVE
    );

    private GeminiApiErrors() {
    }

    public static boolean isRateLimitError(Throwable error) {
        String combined = collectMessages(error).toLowerCase();
        return combined.contains("429")
                || combined.contains("rate limit")
                || combined.contains("quota")
                || combined.contains("resource exhausted")
                || combined.contains("too many requests");
    }

    /** Transient Gemini/Google errors that are safe to retry (503 overload, auth backend blips, etc.). */
    public static boolean isRetryableTransient(Throwable error) {
        if (isRateLimitError(error)) {
            return true;
        }
        String combined = collectMessages(error).toLowerCase();
        return combined.contains("503")
                || combined.contains("\"unavailable\"")
                || combined.contains("authentication backend unavailable")
                || combined.contains("high demand")
                || combined.contains("overloaded")
                || combined.contains("temporarily unavailable")
                || (combined.contains("500") && combined.contains("internal"));
    }

    public static boolean isDailyQuotaExceeded(Throwable error) {
        String combined = collectMessages(error).toLowerCase();
        return combined.contains("exceeded your current quota")
                || combined.contains("free_tier_requests")
                || combined.contains("generate requestsperday")
                || combined.contains("perdayperprojectpermodel");
    }

    public static long parseRetryDelayMs(Throwable error) {
        for (String message : messageChain(error)) {
            if (message == null || message.isBlank()) {
                continue;
            }
            Matcher matcher = RETRY_AFTER_SECONDS.matcher(message);
            if (matcher.find()) {
                try {
                    double seconds = Double.parseDouble(matcher.group(1));
                    return Math.max(0L, Math.round(seconds * 1000.0));
                } catch (NumberFormatException ignored) {
                    return 0L;
                }
            }
        }
        return 0L;
    }

    public static String toUserMessage(Throwable error) {
        String combined = collectMessages(error).toLowerCase();
        if (combined.contains("requires more credits") || combined.contains("can only afford")) {
            return "OpenRouter credits are too low for the configured model (check OPENROUTER_MODEL). "
                    + "Try a cheaper model such as openai/gpt-4o-mini or a free model at https://openrouter.ai/models, "
                    + "add credits at https://openrouter.ai/settings/credits, or set LLM_PROVIDER=qwen with Ollama running locally.";
        }
        if (isDailyQuotaExceeded(error)) {
            return "Gemini free-tier daily quota is exhausted for the configured model (check GOOGLE_AI_MODEL). "
                    + "Each model has its own daily limit — try gemini-3.1-flash-lite or gemini-3.5-flash, enable billing at "
                    + "https://ai.google.dev, or set LLM_PROVIDER=qwen for local Ollama.";
        }
        if (combined.contains("503") || combined.contains("authentication backend unavailable")) {
            return "Gemini API is temporarily unavailable (503). This is usually a short Google-side outage — "
                    + "wait a minute and retry the workflow. The app will auto-retry transient errors when possible.";
        }
        if (isRateLimitError(error)) {
            long retryMs = parseRetryDelayMs(error);
            if (retryMs > 0) {
                long seconds = Math.max(1L, Math.round(retryMs / 1000.0));
                return "Gemini rate limit hit. The API asked to retry in about " + seconds + " second(s). "
                        + "The workflow will retry automatically when possible.";
            }
            return "Gemini rate limit exceeded. Try again in a minute or reduce parallel workflow runs.";
        }
        for (String message : messageChain(error)) {
            if (message != null && !message.isBlank()) {
                return message;
            }
        }
        return error == null ? "Unknown Gemini error" : error.getClass().getSimpleName();
    }

    private static String collectMessages(Throwable error) {
        StringBuilder builder = new StringBuilder();
        for (String message : messageChain(error)) {
            if (message != null && !message.isBlank()) {
                if (!builder.isEmpty()) {
                    builder.append(' ');
                }
                builder.append(message);
            }
        }
        return builder.toString();
    }

    private static List<String> messageChain(Throwable error) {
        List<String> messages = new ArrayList<>();
        Throwable current = error;
        while (current != null) {
            messages.add(current.getMessage());
            current = current.getCause();
        }
        return messages;
    }
}
