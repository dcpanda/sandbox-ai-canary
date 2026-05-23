package com.codetoculture.canary.evals;

import com.codetoculture.canary.agent.CanaryAgent;
import com.codetoculture.canary.model.TriageResult;
import org.springframework.stereotype.Service;

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
}
