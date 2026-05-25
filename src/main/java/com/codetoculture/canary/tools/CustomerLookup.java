package com.codetoculture.canary.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

/**
 * Simulates the customer account lookup microservice.
 */
@Component
public class CustomerLookup {

    private final MockTicketDatabase db;

    public CustomerLookup(MockTicketDatabase db) {
        this.db = db;
    }

    @Tool("""
            Look up a customer account by customer ID.
            Returns JSON with: customerId, name, tier (ENTERPRISE/PROFESSIONAL/STANDARD), contractValue.
            Use when the user provides a customer ID (e.g. CUST-XXXX) or asks about account details.
            """)
    public String lookupCustomer(@P("customerId") String customerId) {
        if (customerId == null) return "{\"error\":\"Customer ID required\"}";
        return db.findCustomerById(customerId.toUpperCase())
                .orElse("{\"error\":\"Customer not found\",\"customerId\":\"" + customerId + "\"}");
    }

    @Tool("""
            Look up a support ticket by its ticket ID.
            Returns JSON with: ticketId, customerId, subject, status, priority, category, createdAt, description.
            Use when the user provides a ticket ID (e.g. TKT-XXXX) or asks about a specific ticket.
            """)
    public String lookupTicket(@P("ticketId") String ticketId) {
        if (ticketId == null) return "{\"error\":\"Ticket ID required\"}";
        return db.findTicketById(ticketId.toUpperCase())
                .orElse("{\"error\":\"Ticket not found\",\"ticketId\":\"" + ticketId + "\"}");
    }

    @Tool("""
            List all active (non-resolved) support tickets for a customer by their customer ID.
            Returns a JSON array of ticket objects. Each ticket has: ticketId, subject, status, priority.
            Use when the user asks what tickets a customer currently has open.
            """)
    public String listCustomerTickets(@P("customerId") String customerId) {
        if (customerId == null) return "[]";
        return db.findTicketsByCustomer(customerId.toUpperCase())
                .orElse("[]");
    }
}
