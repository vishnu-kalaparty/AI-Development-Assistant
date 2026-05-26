package com.aidevassistant.service;

import com.aidevassistant.dto.DraftFileEntryDTO;
import com.aidevassistant.dto.FileResponse;
import com.aidevassistant.dto.WorkflowDecisionRequest;
import com.aidevassistant.dto.WorkflowRunResponse;
import com.aidevassistant.dto.WorkflowStartRequest;

import java.util.List;

/**
 * Pluggable SDLC workflow backend (mock timer vs LangGraph).
 */
public interface WorkflowEngine {

    boolean knows(String workflowId);

    WorkflowRunResponse start(WorkflowStartRequest request, boolean awaitHuman);

    WorkflowRunResponse startSync(WorkflowStartRequest request, boolean awaitHuman);

    WorkflowRunResponse get(String workflowId);

    WorkflowRunResponse decide(String workflowId, WorkflowDecisionRequest request);

    WorkflowRunResponse cancel(String workflowId);

    FileResponse readDraftCompare(String workflowId, String filePath);

    List<DraftFileEntryDTO> draftManifest(String workflowId);
}
