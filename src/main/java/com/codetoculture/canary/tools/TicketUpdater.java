package com.codetoculture.canary.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

/**
 * Simulates the ticket management microservice.
 */
@Component
public class TicketUpdater {

    private final MockTicketDatabase db;

    public TicketUpdater(MockTicketDatabase db) {
        this.db = db;
    }

    @Tool("""
            Update the status of a support ticket.
            Accepts: ticketId (e.g. TKT-XXXX) and newStatus (OPEN, IN_PROGRESS, RESOLVED, CLOSED).
            Returns the updated ticket JSON with the new status and a lastUpdated timestamp.
            Use when the agent needs to escalate, assign, or close a ticket as part of triage.
            """)
    public String updateTicketStatus(@P("ticketId") String ticketId, @P("newStatus") String newStatus) {
        if (ticketId == null || newStatus == null) {
            return "{\"error\":\"ticketId and newStatus are required\"}";
        }
        return db.updateTicketStatus(ticketId.toUpperCase(), newStatus.toUpperCase())
                .orElse("{\"error\":\"Ticket not found\",\"ticketId\":\"" + ticketId + "\"}");
    }

    @Tool("""
            List all active tickets, optionally filtered by priority (CRITICAL, HIGH, MEDIUM, LOW).
            Returns a JSON array of ticket objects showing ticketId, subject, status, priority.
            Use when the agent needs a dashboard view of open work, especially to identify critical items.
            """)
    public String listActiveTickets(@P("filterPriority") String filterPriority) {
        return db.listActiveTickets(filterPriority)
                .orElse("[]");
    }
}
