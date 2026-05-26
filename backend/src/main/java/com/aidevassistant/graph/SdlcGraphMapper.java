package com.aidevassistant.graph;

import com.aidevassistant.agent.AgentRun;
import com.aidevassistant.agent.AgentStep;
import com.aidevassistant.dto.AgentStepDTO;
import com.aidevassistant.dto.WorkflowRunResponse;
import com.aidevassistant.service.AgentToolsService;
import com.aidevassistant.service.WorkflowDraftStore;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class SdlcGraphMapper {

    private final AgentToolsService agentToolsService;
    private final WorkflowDraftStore draftStore;

    public SdlcGraphMapper(AgentToolsService agentToolsService, WorkflowDraftStore draftStore) {
        this.agentToolsService = agentToolsService;
        this.draftStore = draftStore;
    }

    public WorkflowRunResponse toResponse(SdlcState state) {
        WorkflowRunResponse response = new WorkflowRunResponse();
        response.setWorkflowId(state.workflowId());
        response.setStatus(state.status());
        response.setCurrentPhase(state.currentPhase());
        response.setSummary(state.summary());
        response.setRequirementsDoc(state.requirementsDoc());
        response.setArchitectureDoc(state.architectureDoc());
        response.setTestReport(state.testReport());
        response.setReviewVerdict(state.reviewVerdict());
        response.setReviewNotes(state.reviewNotes());
        response.setTestsPassed(state.testsPassed());
        response.setAwaitingHuman("awaiting_human".equals(state.status()));
        response.setFilesWritten(new ArrayList<>(new LinkedHashSet<>(state.filesWritten())));
        response.setSandboxPath(state.sandboxPath());
        response.setSandboxFiles(sandboxFilesForResponse(state));
        response.setSteps(toStepDtos(state.steps()));
        response.setPhases(SdlcPhases.forState(state));
        return response;
    }

    public AgentRun toAgentRun(SdlcState state) {
        Set<String> files = new LinkedHashSet<>(state.filesWritten());
        String status = state.status();
        if ("awaiting_human".equals(status)) {
            status = "completed";
        }
        return new AgentRun(status, state.summary(), state.steps(), new ArrayList<>(files));
    }

    public AgentRun toAgentRun(WorkflowRunResponse response) {
        if (response == null) {
            return AgentRun.error("Empty workflow response");
        }
        String status = response.getStatus();
        if ("awaiting_human".equals(status)) {
            status = "completed";
        }
        List<AgentStep> steps = new ArrayList<>();
        if (response.getSteps() != null) {
            for (var dto : response.getSteps()) {
                steps.add(new AgentStep(
                        dto.getStage(),
                        dto.getStep(),
                        dto.getThought(),
                        dto.getToolName(),
                        dto.getToolArgs(),
                        dto.getObservation()
                ));
            }
        }
        List<String> files = response.getFilesWritten() == null ? List.of() : new ArrayList<>(response.getFilesWritten());
        return new AgentRun(status, response.getSummary(), steps, files);
    }

    public WorkflowRunResponse toAgentRunResponse(AgentRun run) {
        WorkflowRunResponse response = new WorkflowRunResponse();
        response.setStatus(run.getStatus());
        response.setSummary(run.getSummary());
        response.setSteps(toStepDtos(run.getSteps()));
        response.setFilesWritten(run.getFilesWritten() == null ? new ArrayList<>() : new ArrayList<>(run.getFilesWritten()));
        response.setSandboxFiles(agentToolsService.listFilePaths(true));
        return response;
    }

    public static List<AgentStepDTO> toStepDtos(List<AgentStep> steps) {
        List<AgentStepDTO> dtos = new ArrayList<>();
        if (steps == null) {
            return dtos;
        }
        for (AgentStep step : steps) {
            dtos.add(new AgentStepDTO(
                    step.getStage(),
                    step.getStep(),
                    step.getThought(),
                    step.getToolName(),
                    step.getToolArgs(),
                    step.getObservation()
            ));
        }
        return dtos;
    }

    private List<String> sandboxFilesForResponse(SdlcState state) {
        if (draftStore.isActive(state.workflowId())) {
            return draftStore.mergedFilePaths(state.workflowId(), agentToolsService);
        }
        if ("awaiting_human".equals(state.status()) && !state.filesWritten().isEmpty()) {
            return new ArrayList<>(new LinkedHashSet<>(state.filesWritten()));
        }
        return agentToolsService.listFilePaths(state.sandboxPath(), true);
    }
}
