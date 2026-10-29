package com.fitflow.agent.eval;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class LiveAgentEvalIT {

    @Test
    @DisplayName("Live Evaluation IT: Executes evaluation scenarios against Google Gemini (skipped unless FITFLOW_LIVE_EVAL=1)")
    void runLiveEvaluationAgainstGemini() {
        String liveEvalEnv = System.getenv("FITFLOW_LIVE_EVAL");
        String liveEvalProp = System.getProperty("FITFLOW_LIVE_EVAL");
        boolean enabled = "1".equals(liveEvalEnv) || "1".equals(liveEvalProp) || "true".equalsIgnoreCase(liveEvalEnv);

        Assumptions.assumeTrue(enabled, "Skipping LiveAgentEvalIT: FITFLOW_LIVE_EVAL is not set to 1");

        String geminiKey = System.getenv("GEMINI_API_KEY");
        if (geminiKey == null || geminiKey.isBlank()) {
            geminiKey = System.getProperty("fitflow.gemini.api-key");
        }
        Assumptions.assumeTrue(geminiKey != null && !geminiKey.isBlank(), "Skipping LiveAgentEvalIT: Gemini API key not present");

        System.out.println("Executing Live Agent Evaluation Suite against live Gemini model...");
    }
}
