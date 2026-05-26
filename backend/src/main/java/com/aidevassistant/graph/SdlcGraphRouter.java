package com.aidevassistant.graph;

public final class SdlcGraphRouter {

    public static final String REQUIREMENTS = "requirements";
    public static final String ARCHITECTURE = "architecture";
    public static final String IMPLEMENTATION = "implementation";
    public static final String TESTING = "testing";
    public static final String REVIEW = "review";
    public static final String APPROVAL = "approval";
    public static final String END = "end";

    private SdlcGraphRouter() {}

    public static String afterRequirements(SdlcState state) {
        return state.hasError() ? END : ARCHITECTURE;
    }

    public static String afterArchitecture(SdlcState state) {
        return state.hasError() ? END : IMPLEMENTATION;
    }

    public static String afterImplementation(SdlcState state) {
        return state.hasError() ? END : TESTING;
    }

    public static String afterTesting(SdlcState state) {
        if (state.hasError()) {
            return END;
        }
        if (state.testsPassed()) {
            return state.includeReviewer() ? REVIEW : APPROVAL;
        }
        if ("exhausted".equals(state.status())) {
            return APPROVAL;
        }
        return IMPLEMENTATION;
    }

    public static String afterReview(SdlcState state) {
        if (state.hasError()) {
            return END;
        }
        if ("CHANGES_REQUESTED".equalsIgnoreCase(state.reviewVerdict())
                && !"exhausted".equals(state.status())) {
            return IMPLEMENTATION;
        }
        return APPROVAL;
    }

    public static String afterApproval(SdlcState state) {
        if (state.hasError()) {
            return END;
        }
        if ("CHANGES_REQUESTED".equalsIgnoreCase(state.humanDecision())
                && !"exhausted".equals(state.status())) {
            return IMPLEMENTATION;
        }
        return END;
    }
}
