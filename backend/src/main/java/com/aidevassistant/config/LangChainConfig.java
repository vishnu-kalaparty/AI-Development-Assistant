package com.aidevassistant.config;

import com.aidevassistant.tools.StepRecorder;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.googleai.GeminiThinkingConfig;
import dev.langchain4j.model.googleai.GoogleAiGeminiStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Single {@link ChatModel} bean for LangChain4j agents and {@link com.aidevassistant.service.LLMService}.
 * Switch providers via {@code llm.provider} (ollama | qwen | gemini | openrouter).
 */
@Configuration
public class LangChainConfig {

    private static final Logger log = LoggerFactory.getLogger(LangChainConfig.class);

    @Bean
    public ChatModel chatModel(
            @Value("${llm.provider:gemini}") String provider,
            @Value("${google.ai.api.key:}") String googleApiKey,
            @Value("${google.ai.api.model:gemini-3.1-flash-lite}") String googleModel,
            @Value("${ollama.base-url:http://localhost:11434}") String ollamaBaseUrl,
            @Value("${ollama.model:llama3.2}") String ollamaModel,
            @Value("${qwen.model:qwen2.5:7b-instruct}") String qwenModel,
            @Value("${ollama.timeout-seconds:300}") int ollamaTimeoutSeconds,
            @Value("${openrouter.api-key:}") String openRouterApiKey,
            @Value("${openrouter.base-url:https://openrouter.ai/api/v1}") String openRouterBaseUrl,
            @Value("${openrouter.model:openai/gpt-4o-mini}") String openRouterModel,
            @Value("${openrouter.timeout-seconds:300}") int openRouterTimeoutSeconds,
            @Value("${gemini.rate-limit-ms:13000}") long geminiRateLimitMs,
            @Value("${gemini.retry-max:3}") int geminiRetryMax,
            @Value("${gemini.retry-backoff-ms:10000}") long geminiRetryBackoffMs,
            StepRecorder stepRecorder
    ) {
        String normalized = effectiveProvider(provider, googleApiKey, openRouterApiKey);
        Duration ollamaTimeout = Duration.ofSeconds(Math.max(60, ollamaTimeoutSeconds));
        Duration openRouterTimeout = Duration.ofSeconds(Math.max(60, openRouterTimeoutSeconds));
        return switch (normalized) {
            case "gemini" -> {
                StreamingChatModel streaming = rateLimitedStreaming(
                        geminiStreamingModel(googleApiKey, googleModel),
                        geminiRateLimitMs,
                        geminiRetryMax,
                        geminiRetryBackoffMs
                );
                yield new WorkflowRecordingChatModel(
                        streaming,
                        stepRecorder,
                        geminiRetryMax,
                        geminiRetryBackoffMs
                );
            }
            case "ollama" -> {
                ChatModel blocking = ollamaModel(ollamaBaseUrl, ollamaModel, ollamaTimeout);
                StreamingChatModel streaming = ollamaStreamingModel(ollamaBaseUrl, ollamaModel, ollamaTimeout);
                yield new WorkflowRecordingChatModel(blocking, streaming, stepRecorder);
            }
            case "qwen" -> {
                ChatModel blocking = ollamaModel(ollamaBaseUrl, qwenModel, ollamaTimeout);
                StreamingChatModel streaming = ollamaStreamingModel(ollamaBaseUrl, qwenModel, ollamaTimeout);
                yield new WorkflowRecordingChatModel(blocking, streaming, stepRecorder);
            }
            case "openrouter" -> new WorkflowRecordingChatModel(
                    openRouterModel(openRouterBaseUrl, openRouterApiKey, openRouterModel, openRouterTimeout),
                    stepRecorder
            );
            default -> throw new IllegalArgumentException(
                    "Unknown llm.provider: " + provider + " (use ollama, qwen, gemini, or openrouter)");
        };
    }

    private static StreamingChatModel rateLimitedStreaming(
            StreamingChatModel delegate,
            long minIntervalMs,
            int maxRetries,
            long retryBackoffMs
    ) {
        return new RateLimitedStreamingChatModel(delegate, minIntervalMs, maxRetries, retryBackoffMs);
    }

    private static GoogleAiGeminiStreamingChatModel geminiStreamingModel(String apiKey, String modelName) {
        String key = apiKey == null ? "" : apiKey.trim();
        if (key.isBlank()) {
            throw new IllegalArgumentException(
                    "google.ai.api.key (GOOGLE_AI_API_KEY) is required when llm.provider=gemini");
        }
        String model = modelName == null || modelName.isBlank() ? "gemini-3.1-flash-lite" : modelName.trim();
        ChatRequestParameters defaults = ChatRequestParameters.builder()
                .modelName(model)
                .temperature(0.3)
                .maxOutputTokens(8192)
                .build();
        var builder = GoogleAiGeminiStreamingChatModel.builder()
                .apiKey(key)
                .modelName(model)
                .defaultRequestParameters(defaults)
                .temperature(0.3)
                .maxOutputTokens(8192)
                .timeout(Duration.ofSeconds(120));
        if (supportsThinking(model)) {
            builder.thinkingConfig(GeminiThinkingConfig.builder()
                            .includeThoughts(true)
                            .thinkingBudget(1024)
                            .build())
                    .returnThinking(true)
                    .sendThinking(true);
        }
        return builder.build();
    }

    /** Thinking is supported on Gemini 2.5+ / 3.x — not on 2.0 Flash or older. */
    static boolean supportsThinking(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return true;
        }
        String model = modelName.trim().toLowerCase();
        return model.contains("2.5") || model.contains("gemini-3");
    }

    private static ChatModel openRouterModel(String baseUrl, String apiKey, String modelName, Duration timeout) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("openrouter.api-key is required when llm.provider=openrouter");
        }
        String url = baseUrl == null || baseUrl.isBlank() ? "https://openrouter.ai/api/v1" : baseUrl.trim();
        String model = modelName == null || modelName.isBlank() ? "openai/gpt-4o-mini" : modelName.trim();
        return OpenAiChatModel.builder()
                .baseUrl(url)
                .apiKey(apiKey.trim())
                .modelName(model)
                .temperature(0.3)
                .timeout(timeout)
                .build();
    }

    private static ChatModel ollamaModel(String baseUrl, String modelName, Duration timeout) {
        String url = baseUrl == null || baseUrl.isBlank() ? "http://localhost:11434" : baseUrl.trim();
        String model = modelName == null || modelName.isBlank() ? "llama3.2" : modelName.trim();
        return OllamaChatModel.builder()
                .baseUrl(url)
                .modelName(model)
                .temperature(0.3)
                .timeout(timeout)
                .build();
    }

    private static StreamingChatModel ollamaStreamingModel(String baseUrl, String modelName, Duration timeout) {
        String url = baseUrl == null || baseUrl.isBlank() ? "http://localhost:11434" : baseUrl.trim();
        String model = modelName == null || modelName.isBlank() ? "llama3.2" : modelName.trim();
        return OllamaStreamingChatModel.builder()
                .baseUrl(url)
                .modelName(model)
                .temperature(0.3)
                .timeout(timeout)
                .build();
    }

    private static String effectiveProvider(String provider, String googleApiKey, String openRouterApiKey) {
        String normalized = provider == null || provider.isBlank() ? "qwen" : provider.trim().toLowerCase();
        if ("gemini".equals(normalized) && (googleApiKey == null || googleApiKey.isBlank())) {
            log.warn("llm.provider=gemini but GOOGLE_AI_API_KEY is missing; falling back to qwen (Ollama)");
            return "qwen";
        }
        if ("openrouter".equals(normalized) && (openRouterApiKey == null || openRouterApiKey.isBlank())) {
            log.warn("llm.provider=openrouter but OPENROUTER_API_KEY is missing; falling back to qwen (Ollama)");
            return "qwen";
        }
        return normalized;
    }
}
