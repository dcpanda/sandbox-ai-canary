package com.codetoculture.canary.evals;

import com.codetoculture.canary.agent.CanaryAgent;
import com.codetoculture.canary.model.TriageResult;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runs a complete canary check against the customer support triage domain.
 *
 * This is the entry point for switching between model providers:
 *   mvn spring-boot:run -Dspring-boot.run.arguments="--canary.model.provider=ollama --ollama.model.name=llama3"
 *
 * To test OpenAI:
 *   OPENAI_API_KEY=sk-xxx mvn spring-boot:run -Dspring-boot.run.arguments="--canary.model.provider=openai"
 */
@Service
public class CanaryEvaluator {

    private final CanaryAgent agent;

    public CanaryEvaluator(CanaryAgent agent) {
        this.agent = agent;
    }

    /**
     * Run the canary: ask about a CRITICAL customer ticket and verify the agent produces a triage result.
     */
    public TriageResult runCanaryCheck() {
        return agent.ask("Triage for customer CUST-5512, ticket TKT-1004. What is the urgency and routing?");
    }

    /**
     * Run a customer account lookup check.
     */
    public TriageResult runCustomerLookup() {
        return agent.ask("Look up customer CUST-4401 account details.");
    }

    /**
     * Run a ticket detail check.
     */
    public TriageResult runTicketLookup() {
        return agent.ask("Get details for ticket TKT-1002.");
    }

    /**
     * Run a triage dashboard check.
     */
    public TriageResult runTriageDashboard() {
        return agent.ask("Show me the active triage dashboard. What are the critical issues?");
    }

    /**
     * Run all scenarios and return a labeled report for model comparison.
     */
    public Map<String, TriageResult> runFullSuite() {
        Map<String, TriageResult> results = new LinkedHashMap<>();
        results.put("canaryCheck", runCanaryCheck());
        results.put("customerLookup", runCustomerLookup());
        results.put("ticketLookup", runTicketLookup());
        results.put("triageDashboard", runTriageDashboard());
        return results;
    }

    /**
     * Produce a formatted comparison string for the full suite.
     */
    public String runFullSuiteReport() {
        Map<String, TriageResult> results = runFullSuite();
        StringBuilder sb = new StringBuilder("=== Canary Evaluation Report ===\n");
        for (Map.Entry<String, TriageResult> entry : results.entrySet()) {
            sb.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        sb.append("================================");
        return sb.toString();
    }
}
