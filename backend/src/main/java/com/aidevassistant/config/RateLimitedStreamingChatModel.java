package com.aidevassistant.config;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

public class RateLimitedStreamingChatModel implements StreamingChatModel {

    private static final Logger log = LoggerFactory.getLogger(RateLimitedStreamingChatModel.class);

    private final StreamingChatModel delegate;
    private final long minIntervalMs;
    private final int maxRetries;
    private final long retryBackoffMs;
    private final Object lock = new Object();
    private long lastCallAt;

    public RateLimitedStreamingChatModel(
            StreamingChatModel delegate,
            long minIntervalMs,
            int maxRetries,
            long retryBackoffMs
    ) {
        this.delegate = delegate;
        this.minIntervalMs = Math.max(0, minIntervalMs);
        this.maxRetries = Math.max(0, maxRetries);
        this.retryBackoffMs = Math.max(0, retryBackoffMs);
    }

    @Override
    public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
        synchronized (lock) {
            waitForSlot();
            callWithRetry(request, handler);
        }
    }

    private void waitForSlot() {
        if (minIntervalMs <= 0) {
            return;
        }
        long elapsed = System.currentTimeMillis() - lastCallAt;
        long waitMs = minIntervalMs - elapsed;
        if (waitMs > 0) {
            log.debug("Gemini streaming rate limit: sleeping {} ms before next request", waitMs);
            sleep(waitMs);
        }
    }

    private void callWithRetry(ChatRequest request, StreamingChatResponseHandler handler) {
        RuntimeException lastError = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                delegate.chat(request, handler);
                lastCallAt = System.currentTimeMillis();
                return;
            } catch (RuntimeException e) {
                lastError = e;
                if (attempt >= maxRetries || !GeminiApiErrors.isRetryableTransient(e)) {
                    if (GeminiApiErrors.isDailyQuotaExceeded(e)) {
                        throw new GeminiQuotaException(GeminiApiErrors.toUserMessage(e), e);
                    }
                    throw e;
                }
                long backoff = retryBackoffMs * (attempt + 1L);
                log.warn("Gemini streaming transient error (attempt {}), retrying in {} ms", attempt + 1, backoff);
                sleep(backoff);
            }
        }
        throw lastError != null ? lastError : new IllegalStateException("Gemini streaming call failed");
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return delegate.defaultRequestParameters();
    }

    @Override
    public List<ChatModelListener> listeners() {
        return delegate.listeners();
    }

    @Override
    public ModelProvider provider() {
        return delegate.provider();
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return delegate.supportedCapabilities();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for Gemini rate limit", e);
        }
    }
}
