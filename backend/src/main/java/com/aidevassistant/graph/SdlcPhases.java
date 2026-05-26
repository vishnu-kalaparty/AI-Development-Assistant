package com.aidevassistant.graph;

import com.aidevassistant.agent.AgentStep;
import com.aidevassistant.dto.WorkflowPhaseDTO;
import com.aidevassistant.dto.WorkflowRunResponse;

import java.util.ArrayList;
import java.util.List;

/** Shared SDLC phase timeline logic for real and mock workflow paths. */
public final class SdlcPhases {

    public static final List<String> ORDER = List.of(
            "requirements", "architecture", "implementation", "testing", "review", "approval", "complete"
    );

    private SdlcPhases() {
    }

    public static List<WorkflowPhaseDTO> forState(SdlcState state) {
        return build(
                state.status(),
                state.currentPhase(),
                state.requirementsDoc(),
                state.architectureDoc(),
                implementationSummary(state),
                state.testReport(),
                state.reviewNotes(),
                approvalSummary(state.status(), state.reviewNotes()),
                state.steps(),
                false
        );
    }

    public static List<WorkflowPhaseDTO> forMock(WorkflowRunResponse run, String currentPhase) {
        String implSummary = run.getFilesWritten().isEmpty()
                ? ""
                : "Files: " + String.join(", ", run.getFilesWritten());
        return build(
                run.getStatus(),
                currentPhase,
                run.getRequirementsDoc(),
                run.getArchitectureDoc(),
                implSummary,
                run.getTestReport(),
                run.getReviewNotes(),
                approvalSummary(run.getStatus(), null),
                run.getSteps(),
                true
        );
    }

    private static List<WorkflowPhaseDTO> build(
            String status,
            String currentPhase,
            String requirementsDoc,
            String architectureDoc,
            String implementationSummary,
            String testReport,
            String reviewNotes,
            String approvalSummary,
            List<?> steps,
            boolean mock
    ) {
        List<WorkflowPhaseDTO> phases = new ArrayList<>();
        phases.add(phase("requirements", "Requirements", requirementsDoc, status, currentPhase, steps, mock));
        phases.add(phase("architecture", "Architecture", architectureDoc, status, currentPhase, steps, mock));
        phases.add(phase("implementation", "Implementation", implementationSummary, status, currentPhase, steps, mock));
        phases.add(phase("testing", "Testing", testReport, status, currentPhase, steps, mock));
        phases.add(phase("review", "Review", reviewNotes, status, currentPhase, steps, mock));
        phases.add(phase("approval", "Approval", approvalSummary, status, currentPhase, steps, mock));
        return phases;
    }

    private static WorkflowPhaseDTO phase(
            String id,
            String label,
            String summary,
            String status,
            String currentPhase,
            List<?> steps,
            boolean mock
    ) {
        return new WorkflowPhaseDTO(id, label, phaseStatus(id, summary, status, currentPhase, steps, mock), summary);
    }

    private static String phaseStatus(
            String phaseId,
            String summary,
            String status,
            String currentPhase,
            List<?> steps,
            boolean mock
    ) {
        if ("error".equals(status) && phaseId.equals(currentPhase)) {
            return "error";
        }
        if ("awaiting_human".equals(status) && "approval".equals(phaseId)) {
            return "waiting";
        }
        if (mock) {
            if ("completed".equals(status) || "exhausted".equals(status) || "needs_changes".equals(status)
                    || "complete".equals(currentPhase)) {
                return "done";
            }
            if (phaseId.equals(currentPhase) && "running".equals(status)) {
                return "active";
            }
            if (hasContent(summary) || laterThan(currentPhase, phaseId)) {
                return "done";
            }
            return "pending";
        }
        if ("complete".equals(currentPhase) || "completed".equals(status) || "exhausted".equals(status)
                || "needs_changes".equals(status)) {
            if (hasContent(summary) || laterThan(currentPhase, phaseId) || "complete".equals(currentPhase)) {
                return "done";
            }
        }
        if (phaseId.equals(currentPhase)) {
            return "active";
        }
        if (hasContent(summary)) {
            return "done";
        }
        if (laterThan(currentPhase, phaseId) && phaseHasSteps(steps, phaseId)) {
            return "done";
        }
        return "pending";
    }

    private static boolean phaseHasSteps(List<?> steps, String phaseId) {
        if (steps == null) {
            return false;
        }
        for (Object step : steps) {
            String stage = stageOf(step);
            if (phaseId.equalsIgnoreCase(stage)) {
                return true;
            }
        }
        return false;
    }

    private static String stageOf(Object step) {
        if (step instanceof AgentStep agentStep) {
            return agentStep.getStage();
        }
        if (step instanceof com.aidevassistant.dto.AgentStepDTO dto) {
            return dto.getStage();
        }
        return "";
    }

    static boolean laterThan(String current, String phaseId) {
        int currentIdx = ORDER.indexOf(current);
        if ("complete".equals(current)) {
            currentIdx = ORDER.indexOf("approval") + 1;
        }
        int phaseIdx = ORDER.indexOf(phaseId);
        return currentIdx > phaseIdx && currentIdx >= 0 && phaseIdx >= 0;
    }

    static boolean hasContent(String summary) {
        return summary != null && !summary.isBlank();
    }

    static String approvalSummary(String status, String reviewNotes) {
        if ("awaiting_human".equals(status)) {
            return "Waiting for approval";
        }
        if ("completed".equals(status)) {
            return "Approved";
        }
        if ("exhausted".equals(status) || "needs_changes".equals(status)) {
            return reviewNotes == null ? "" : reviewNotes;
        }
        return "";
    }

    private static String implementationSummary(SdlcState state) {
        if (hasContent(state.engineerSummary())) {
            return state.engineerSummary();
        }
        if (!state.filesWritten().isEmpty()) {
            return "Files written: " + String.join(", ", state.filesWritten());
        }
        if (SdlcGraphRouter.IMPLEMENTATION.equals(state.currentPhase()) && "running".equals(state.status())) {
            return state.summary().isBlank() ? "Running implementation…" : state.summary();
        }
        return "";
    }
}
