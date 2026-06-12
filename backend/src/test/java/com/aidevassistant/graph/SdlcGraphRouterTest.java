package com.aidevassistant.graph;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SdlcGraphRouterTest {

    @Test
    void testingFailureRetriesUntilLimit() {
        SdlcState retry = state(Map.of(
                SdlcState.TESTS_PASSED, false,
                SdlcState.STATUS, "running",
                SdlcState.ITERATION, 0,
                SdlcState.MAX_ROUNDS, 1,
                SdlcState.INCLUDE_REVIEWER, true
        ));
        assertEquals(SdlcGraphRouter.IMPLEMENTATION, SdlcGraphRouter.afterTesting(retry));

        SdlcState exhausted = state(Map.of(
                SdlcState.TESTS_PASSED, false,
                SdlcState.STATUS, "exhausted",
                SdlcState.ITERATION, 1,
                SdlcState.MAX_ROUNDS, 1
        ));
        assertEquals(SdlcGraphRouter.APPROVAL, SdlcGraphRouter.afterTesting(exhausted));
    }

    @Test
    void testingPassGoesToReviewOrApproval() {
        SdlcState withReview = state(Map.of(
                SdlcState.TESTS_PASSED, true,
                SdlcState.INCLUDE_REVIEWER, true
        ));
        assertEquals(SdlcGraphRouter.REVIEW, SdlcGraphRouter.afterTesting(withReview));

        SdlcState skipReview = state(Map.of(
                SdlcState.TESTS_PASSED, true,
                SdlcState.INCLUDE_REVIEWER, false
        ));
        assertEquals(SdlcGraphRouter.APPROVAL, SdlcGraphRouter.afterTesting(skipReview));
    }

    @Test
    void reviewCanRequestChangesOrAdvance() {
        SdlcState changes = state(Map.of(
                SdlcState.REVIEW_VERDICT, "CHANGES_REQUESTED",
                SdlcState.STATUS, "running"
        ));
        assertEquals(SdlcGraphRouter.IMPLEMENTATION, SdlcGraphRouter.afterReview(changes));

        SdlcState approved = state(Map.of(
                SdlcState.REVIEW_VERDICT, "APPROVE",
                SdlcState.STATUS, "running"
        ));
        assertEquals(SdlcGraphRouter.APPROVAL, SdlcGraphRouter.afterReview(approved));
    }

    @Test
    void humanChangesResumeImplementation() {
        SdlcState changes = state(Map.of(
                SdlcState.HUMAN_DECISION, "CHANGES_REQUESTED",
                SdlcState.STATUS, "running"
        ));
        assertEquals(SdlcGraphRouter.IMPLEMENTATION, SdlcGraphRouter.afterApproval(changes));

        SdlcState approved = state(Map.of(
                SdlcState.HUMAN_DECISION, "APPROVE",
                SdlcState.STATUS, "completed"
        ));
        assertEquals(SdlcGraphRouter.END, SdlcGraphRouter.afterApproval(approved));
    }

    @Test
    void errorsTerminate() {
        SdlcState error = state(Map.of(SdlcState.STATUS, "error", SdlcState.ERROR, "boom"));
        assertEquals(SdlcGraphRouter.END, SdlcGraphRouter.afterRequirements(error));
        assertEquals(SdlcGraphRouter.END, SdlcGraphRouter.afterImplementation(error));
        assertEquals(SdlcGraphRouter.END, SdlcGraphRouter.afterTesting(error));
    }

    @Test
    void looksLikePassAndVerdictHelpers() {
        org.junit.jupiter.api.Assertions.assertTrue(SdlcNodes.looksLikePass("all good\nTESTS_PASSED"));
        org.junit.jupiter.api.Assertions.assertFalse(SdlcNodes.looksLikePass("TESTS_FAILED"));
        assertEquals("APPROVE", SdlcNodes.extractVerdict("Looks fine\nAPPROVE"));
        assertEquals("CHANGES_REQUESTED", SdlcNodes.extractVerdict("Please fix\nCHANGES_REQUESTED"));
    }

    private static SdlcState state(Map<String, Object> values) {
        return new SdlcState(values);
    }
}
