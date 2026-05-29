package com.aidevassistant.service;

import com.aidevassistant.graph.SdlcState;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory workflow snapshots for live SDLC polling.
 * Supports partial updates while a long-running graph node (e.g. implementation) is active.
 */
@Component
public class WorkflowSnapshotStore {

    private final Map<String, SdlcState> snapshots = new ConcurrentHashMap<>();

    public SdlcState get(String workflowId) {
        return snapshots.get(workflowId);
    }

    public void put(String workflowId, SdlcState state) {
        if (workflowId == null || workflowId.isBlank() || state == null) {
            return;
        }
        snapshots.compute(workflowId, (id, existing) -> mergePreservingLiveSteps(existing, state));
    }

    private static SdlcState mergePreservingLiveSteps(SdlcState existing, SdlcState incoming) {
        if (existing == null) {
            return incoming;
        }
        if (!"running".equals(existing.status()) || !"running".equals(incoming.status())) {
            return incoming;
        }
        if (existing.steps().size() > incoming.steps().size()) {
            Map<String, Object> merged = new LinkedHashMap<>(incoming.data());
            merged.put(SdlcState.STEPS, existing.steps());
            return new SdlcState(merged);
        }
        return incoming;
    }

    public void merge(String workflowId, Map<String, Object> patch) {
        if (workflowId == null || workflowId.isBlank() || patch == null || patch.isEmpty()) {
            return;
        }
        snapshots.compute(workflowId, (id, existing) -> {
            Map<String, Object> merged = new LinkedHashMap<>();
            if (existing != null) {
                merged.putAll(existing.data());
            }
            merged.putAll(patch);
            return new SdlcState(merged);
        });
    }
}
