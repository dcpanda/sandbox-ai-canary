package com.codetoculture.canary.model;

/**
 * The agent's triage output — mirrors the production business-domain schema.
 *
 * If LangChain4j changes how structured outputs are serialized, this record's
 * field names and types form the contract that must remain stable.
 */
public record TriageResult(
        String ticketId,
        String category,
        Urgency urgency,
        String routing,
        String summary
) {
    public enum Urgency {
        IMMEDIATE, HIGH, MEDIUM, LOW
    }
}
