package com.aidevassistant.tools;

/**
 * Thread-local workflow id for live SDLC progress updates during tool calls.
 */
public final class WorkflowRunContext {

    private static final ThreadLocal<String> WORKFLOW_ID = new ThreadLocal<>();

    private WorkflowRunContext() {
    }

    public static void setWorkflowId(String workflowId) {
        if (workflowId == null || workflowId.isBlank()) {
            WORKFLOW_ID.remove();
        } else {
            WORKFLOW_ID.set(workflowId);
        }
    }

    public static String workflowId() {
        return WORKFLOW_ID.get();
    }

    public static void clear() {
        WORKFLOW_ID.remove();
    }

    public static Scope scope(String workflowId) {
        return new Scope(workflowId);
    }

    public static final class Scope implements AutoCloseable {
        private final String previous;

        Scope(String workflowId) {
            previous = workflowId();
            setWorkflowId(workflowId);
        }

        @Override
        public void close() {
            if (previous == null) {
                clear();
            } else {
                setWorkflowId(previous);
            }
        }
    }
}
