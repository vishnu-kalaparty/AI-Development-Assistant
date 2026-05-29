package com.aidevassistant.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class WorkflowRunTracker {

    private final Map<String, Future<?>> futures = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> cancelFlags = new ConcurrentHashMap<>();

    public void register(String workflowId, Future<?> future) {
        if (workflowId == null || workflowId.isBlank() || future == null) {
            return;
        }
        futures.put(workflowId, future);
        cancelFlags.putIfAbsent(workflowId, new AtomicBoolean(false));
    }

    public void unregister(String workflowId) {
        if (workflowId == null || workflowId.isBlank()) {
            return;
        }
        futures.remove(workflowId);
        cancelFlags.remove(workflowId);
    }

    public boolean cancel(String workflowId) {
        if (workflowId == null || workflowId.isBlank()) {
            return false;
        }
        AtomicBoolean flag = cancelFlags.computeIfAbsent(workflowId, id -> new AtomicBoolean(false));
        flag.set(true);
        Future<?> future = futures.remove(workflowId);
        if (future != null) {
            future.cancel(true);
            return true;
        }
        return flag.get();
    }

    public boolean isCancelled(String workflowId) {
        if (workflowId == null || workflowId.isBlank()) {
            return false;
        }
        AtomicBoolean flag = cancelFlags.get(workflowId);
        return flag != null && flag.get();
    }

    public void checkCancelled(String workflowId) {
        if (isCancelled(workflowId)) {
            throw new WorkflowCancelledException();
        }
    }
}
