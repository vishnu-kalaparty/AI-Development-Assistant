package com.aidevassistant.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeminiApiErrorsTest {

    @Test
    void detectsDailyFreeTierQuota() {
        String message = """
                code: 429, message: You exceeded your current quota...
                generativelanguage.googleapis.com/generate_content_free_tier_requests, limit: 20
                GenerateRequestsPerDayPerProjectPerModel-FreeTier
                Please retry in 37.583226157s.
                """;
        RuntimeException error = new RuntimeException(message);
        assertTrue(GeminiApiErrors.isDailyQuotaExceeded(error));
        assertTrue(GeminiApiErrors.isRateLimitError(error));
        assertTrue(GeminiApiErrors.toUserMessage(error).contains("daily quota"));
    }

    @Test
    void parsesRetryDelayFromMessage() {
        RuntimeException error = new RuntimeException("Please retry in 37.583226157s.");
        assertEquals(37_583L, GeminiApiErrors.parseRetryDelayMs(error));
    }

    @Test
    void transientRateLimitIsNotDailyQuota() {
        RuntimeException error = new RuntimeException("HTTP 429 Too Many Requests");
        assertTrue(GeminiApiErrors.isRateLimitError(error));
        assertFalse(GeminiApiErrors.isDailyQuotaExceeded(error));
    }

    @Test
    void detectsTransient503AuthBackend() {
        RuntimeException error = new RuntimeException("""
                {
                  "error": {
                    "code": 503,
                    "message": "Authentication backend unavailable.",
                    "status": "UNAVAILABLE"
                  }
                }
                """);
        assertTrue(GeminiApiErrors.isRetryableTransient(error));
        assertTrue(GeminiApiErrors.toUserMessage(error).contains("temporarily unavailable"));
    }
}
