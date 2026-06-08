package com.aidevassistant.config;

/**
 * Daily or billing quota exhausted — retrying will not help until quota resets or billing is enabled.
 */
public class GeminiQuotaException extends RuntimeException {

    public GeminiQuotaException(String message, Throwable cause) {
        super(message, cause);
    }
}
