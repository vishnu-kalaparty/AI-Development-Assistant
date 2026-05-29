package com.aidevassistant.service;

/**
 * Raised when a user stops an in-progress SDLC workflow run.
 */
public class WorkflowCancelledException extends RuntimeException {

    public WorkflowCancelledException() {
        super("Workflow cancelled");
    }
}
