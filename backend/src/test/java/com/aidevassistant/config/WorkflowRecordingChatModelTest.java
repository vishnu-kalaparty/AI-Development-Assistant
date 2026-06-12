package com.aidevassistant.config;

import com.aidevassistant.service.WorkflowSnapshotStore;
import com.aidevassistant.tools.StepRecorder;
import com.aidevassistant.tools.WorkflowRunContext;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowRecordingChatModelTest {

    private StepRecorder recorder;

    @BeforeEach
    void setUp() {
        recorder = new StepRecorder(new WorkflowSnapshotStore());
        WorkflowRunContext.setWorkflowId("wf-record");
        recorder.begin("wf-record", "implementation");
    }

    @AfterEach
    void tearDown() {
        recorder.clear();
        WorkflowRunContext.clear();
    }

    @Test
    void streamingChatAppliesDefaultModelName() {
        AtomicReference<String> modelSeen = new AtomicReference<>();
        StreamingChatModel streaming = new StubStreamingChatModel("gemini-3.5-flash-lite", modelSeen);
        ChatModel model = new WorkflowRecordingChatModel(streaming, recorder);

        model.chat(ChatRequest.builder().messages(UserMessage.from("hello")).build());

        assertEquals("gemini-3.5-flash-lite", modelSeen.get());
    }

    @Test
    void chatRecordsModelTurnAfterDelegateReturns() {
        ChatModel model = new WorkflowRecordingChatModel(new StubChatModel(
                AiMessage.builder()
                        .text("I will write src/app.py")
                        .thinking("Plan the module layout")
                        .build()
        ), recorder);

        model.chat("Build the app");

        assertEquals(1, recorder.steps().size());
        assertTrue(recorder.steps().get(0).getObservation().contains("write src/app.py"));
        assertTrue(recorder.steps().get(0).getThought().contains("module layout"));
    }

    private static final class StubStreamingChatModel implements StreamingChatModel {
        private final ChatRequestParameters defaults;
        private final AtomicReference<String> modelSeen;

        private StubStreamingChatModel(String modelName, AtomicReference<String> modelSeen) {
            this.defaults = ChatRequestParameters.builder().modelName(modelName).build();
            this.modelSeen = modelSeen;
        }

        @Override
        public ChatRequestParameters defaultRequestParameters() {
            return defaults;
        }

        @Override
        public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
            modelSeen.set(request.modelName());
            handler.onCompleteResponse(ChatResponse.builder()
                    .aiMessage(AiMessage.from("ok"))
                    .build());
        }

        @Override
        public List<dev.langchain4j.model.chat.listener.ChatModelListener> listeners() {
            return List.of();
        }

        @Override
        public ModelProvider provider() {
            return ModelProvider.OTHER;
        }
    }

    private static final class StubChatModel implements ChatModel {
        private final AiMessage response;

        private StubChatModel(AiMessage response) {
            this.response = response;
        }

        @Override
        public ChatResponse chat(ChatRequest request) {
            return ChatResponse.builder().aiMessage(response).build();
        }
    }
}
