package com.aidevassistant.config;

import com.aidevassistant.tools.StepRecorder;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Records model turns for live SDLC polling. Uses streaming when available so thought/output
 * chunks reach the UI during generation, not only after the full response completes.
 */
public class WorkflowRecordingChatModel implements ChatModel {

    private static final Logger log = LoggerFactory.getLogger(WorkflowRecordingChatModel.class);
    private static final long STREAM_WAIT_SECONDS = 600;
    private static final int DEFAULT_TRANSIENT_RETRIES = 3;
    private static final long DEFAULT_TRANSIENT_BACKOFF_MS = 10_000L;

    private final ChatModel blockingDelegate;
    private final StreamingChatModel streamingDelegate;
    private final StepRecorder stepRecorder;
    private final int maxTransientRetries;
    private final long transientRetryBackoffMs;

    public WorkflowRecordingChatModel(ChatModel blockingDelegate, StepRecorder stepRecorder) {
        this(blockingDelegate, null, stepRecorder);
    }

    public WorkflowRecordingChatModel(StreamingChatModel streamingDelegate, StepRecorder stepRecorder) {
        this(null, streamingDelegate, stepRecorder);
    }

    public WorkflowRecordingChatModel(
            ChatModel blockingDelegate,
            StreamingChatModel streamingDelegate,
            StepRecorder stepRecorder
    ) {
        this(
                blockingDelegate,
                streamingDelegate,
                stepRecorder,
                DEFAULT_TRANSIENT_RETRIES,
                DEFAULT_TRANSIENT_BACKOFF_MS
        );
    }

    public WorkflowRecordingChatModel(
            StreamingChatModel streamingDelegate,
            StepRecorder stepRecorder,
            int maxTransientRetries,
            long transientRetryBackoffMs
    ) {
        this(null, streamingDelegate, stepRecorder, maxTransientRetries, transientRetryBackoffMs);
    }

    public WorkflowRecordingChatModel(
            ChatModel blockingDelegate,
            StreamingChatModel streamingDelegate,
            StepRecorder stepRecorder,
            int maxTransientRetries,
            long transientRetryBackoffMs
    ) {
        this.blockingDelegate = blockingDelegate;
        this.streamingDelegate = streamingDelegate;
        this.stepRecorder = stepRecorder;
        this.maxTransientRetries = Math.max(0, maxTransientRetries);
        this.transientRetryBackoffMs = Math.max(0, transientRetryBackoffMs);
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        if (streamingDelegate != null) {
            return chatViaStreaming(request);
        }
        if (blockingDelegate == null) {
            throw new IllegalStateException("No chat model delegate configured");
        }
        ChatResponse response = blockingDelegate.chat(request);
        capture(response);
        return response;
    }

    @Override
    public ChatResponse doChat(ChatRequest request) {
        return chat(request);
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        if (streamingDelegate != null) {
            return streamingDelegate.defaultRequestParameters();
        }
        if (blockingDelegate != null) {
            return blockingDelegate.defaultRequestParameters();
        }
        throw new IllegalStateException("No chat model delegate configured");
    }

    @Override
    public List<ChatModelListener> listeners() {
        if (streamingDelegate != null) {
            return streamingDelegate.listeners();
        }
        return blockingDelegate.listeners();
    }

    @Override
    public ModelProvider provider() {
        if (streamingDelegate != null) {
            return streamingDelegate.provider();
        }
        return blockingDelegate.provider();
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        if (streamingDelegate != null) {
            return streamingDelegate.supportedCapabilities();
        }
        return blockingDelegate.supportedCapabilities();
    }

    private ChatResponse chatViaStreaming(ChatRequest request) {
        ChatRequest finalRequest = mergeStreamingDefaults(request);
        RuntimeException lastError = null;
        for (int attempt = 0; attempt <= maxTransientRetries; attempt++) {
            try {
                return chatViaStreamingOnce(finalRequest);
            } catch (RuntimeException e) {
                lastError = e;
                if (attempt >= maxTransientRetries || !GeminiApiErrors.isRetryableTransient(e)) {
                    throw e;
                }
                long backoff = transientRetryBackoffMs * (attempt + 1L);
                log.warn(
                        "Gemini transient error during workflow stream (attempt {}), retrying in {} ms: {}",
                        attempt + 1,
                        backoff,
                        e.getMessage()
                );
                sleep(backoff);
            }
        }
        throw lastError != null ? lastError : new IllegalStateException("Streaming model failed after retries");
    }

    private ChatResponse chatViaStreamingOnce(ChatRequest finalRequest) {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<ChatResponse> responseRef = new AtomicReference<>();
        AtomicReference<RuntimeException> errorRef = new AtomicReference<>();
        StringBuilder thinking = new StringBuilder();
        StringBuilder text = new StringBuilder();
        stepRecorder.beginModelStream();

        // Use chat(), not doChat(), so LangChain4j merges defaultRequestParameters (model name, etc.).
        streamingDelegate.chat(finalRequest, new StreamingChatResponseHandler() {
            @Override
            public void onPartialThinking(PartialThinking partialThinking) {
                if (partialThinking == null || partialThinking.text() == null) {
                    return;
                }
                thinking.append(partialThinking.text());
                stepRecorder.recordModelPartial(thinking.toString(), text.toString(), null);
            }

            @Override
            public void onPartialResponse(String partialResponse) {
                if (partialResponse == null || partialResponse.isEmpty()) {
                    return;
                }
                text.append(partialResponse);
                stepRecorder.recordModelPartial(thinking.toString(), text.toString(), null);
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                responseRef.set(response);
                capture(response);
                stepRecorder.endModelStream();
                done.countDown();
            }

            @Override
            public void onError(Throwable error) {
                stepRecorder.endModelStream();
                if (error instanceof RuntimeException runtime) {
                    errorRef.set(runtime);
                } else {
                    errorRef.set(new RuntimeException(error));
                }
                done.countDown();
            }
        });

        try {
            if (!done.await(STREAM_WAIT_SECONDS, TimeUnit.SECONDS)) {
                stepRecorder.endModelStream();
                throw new IllegalStateException("Streaming model timed out after " + STREAM_WAIT_SECONDS + "s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            stepRecorder.endModelStream();
            throw new IllegalStateException("Interrupted while waiting for streaming model", e);
        }

        RuntimeException error = errorRef.get();
        if (error != null) {
            throw error;
        }
        ChatResponse response = responseRef.get();
        if (response == null) {
            throw new IllegalStateException("Streaming model completed without a response");
        }
        return response;
    }

    private void capture(ChatResponse response) {
        if (response == null || response.aiMessage() == null) {
            return;
        }
        AiMessage message = response.aiMessage();
        String text = message.text();
        String toolHint = null;
        if ((text == null || text.isBlank()) && message.hasToolExecutionRequests()) {
            toolHint = "Calling tools: " + message.toolExecutionRequests().size() + " request(s)";
        }
        stepRecorder.recordModelTurn(message.thinking(), text, toolHint);
    }

    private ChatRequest mergeStreamingDefaults(ChatRequest request) {
        ChatRequestParameters merged = streamingDelegate.defaultRequestParameters()
                .overrideWith(request.parameters());
        if (merged.modelName() == null || merged.modelName().isBlank()) {
            throw new IllegalStateException(
                    "Gemini model name is not configured (set GOOGLE_AI_MODEL in .env)");
        }
        return ChatRequest.builder()
                .messages(request.messages())
                .parameters(merged)
                .build();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to retry Gemini call", e);
        }
    }
}
