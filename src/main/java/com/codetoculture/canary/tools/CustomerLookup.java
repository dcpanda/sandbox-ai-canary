package com.codetoculture.canary.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

/**
 * Simulates the customer account lookup microservice.
 */
@Component
public class CustomerLookup {

    @Tool("""
            Look up a customer account by customer ID.
            Returns JSON with: customerId, name, tier (ENTERPRISE/PROFESSIONAL/STANDARD), contractValue.
            Use when the user provides a customer ID (e.g. CUST-XXXX) or asks about account details.
            """)
    public String lookupCustomer(@P("customerId") String customerId) {
        if (customerId == null) return "{\"error\":\"Customer ID required\"}";
        String key = customerId.toUpperCase();
        switch (key) {
            case "CUST-4401": return "{\"customerId\":\"CUST-4401\",\"name\":\"Acme Corp\",\"tier\":\"ENTERPRISE\",\"contractValue\":120000}";
            case "CUST-7723": return "{\"customerId\":\"CUST-7723\",\"name\":\"Globex Inc\",\"tier\":\"PROFESSIONAL\",\"contractValue\":24000}";
            case "CUST-2290": return "{\"customerId\":\"CUST-2290\",\"name\":\"Wayne Enterprises\",\"tier\":\"ENTERPRISE\",\"contractValue\":250000}";
            case "CUST-5512": return "{\"customerId\":\"CUST-5512\",\"name\":\"Stark Industries\",\"tier\":\"ENTERPRISE\",\"contractValue\":500000}";
            case "CUST-3380": return "{\"customerId\":\"CUST-3380\",\"name\":\"Umbrella Corp\",\"tier\":\"PROFESSIONAL\",\"contractValue\":18000}";
            default: return "{\"error\":\"Customer not found\",\"customerId\":\"" + customerId + "\"}";
        }
    }

    @Tool("""
            Look up a support ticket by its ticket ID.
            Returns JSON with: ticketId, customerId, subject, status, priority, category, createdAt, description.
            Use when the user provides a ticket ID (e.g. TKT-XXXX) or asks about a specific ticket.
            """)
    public String lookupTicket(@P("ticketId") String ticketId) {
        if (ticketId == null) return "{\"error\":\"Ticket ID required\"}";
        String key = ticketId.toUpperCase();
        switch (key) {
            case "TKT-1001": return "{\"ticketId\":\"TKT-1001\",\"customerId\":\"CUST-4401\",\"subject\":\"Cannot access account after password reset\",\"status\":\"OPEN\",\"priority\":\"HIGH\",\"category\":\"AUTH\"}";
            case "TKT-1002": return "{\"ticketId\":\"TKT-1002\",\"customerId\":\"CUST-7723\",\"subject\":\"Refund request for duplicate charge\",\"status\":\"IN_PROGRESS\",\"priority\":\"MEDIUM\",\"category\":\"BILLING\"}";
            case "TKT-1003": return "{\"ticketId\":\"TKT-1003\",\"customerId\":\"CUST-2290\",\"subject\":\"Feature request: export data to CSV\",\"status\":\"NEW\",\"priority\":\"LOW\",\"category\":\"FEATURE_REQUEST\"}";
            case "TKT-1004": return "{\"ticketId\":\"TKT-1004\",\"customerId\":\"CUST-5512\",\"subject\":\"API returning 500 errors on /v2/orders endpoint\",\"status\":\"OPEN\",\"priority\":\"CRITICAL\",\"category\":\"BUG\"}";
            case "TKT-1005": return "{\"ticketId\":\"TKT-1005\",\"customerId\":\"CUST-3380\",\"subject\":\"Update billing address for corporate account\",\"status\":\"RESOLVED\",\"priority\":\"LOW\",\"category\":\"ACCOUNT_UPDATE\"}";
            default: return "{\"error\":\"Ticket not found\",\"ticketId\":\"" + ticketId + "\"}";
        }
    }

    @Tool("""
            List all active (non-resolved) support tickets for a customer by their customer ID.
            Returns a JSON array of ticket objects. Each ticket has: ticketId, subject, status, priority.
            Use when the user asks what tickets a customer currently has open.
            """)
    public String listCustomerTickets(@P("customerId") String customerId) {
        if (customerId == null) return "[]";
        String key = customerId.toUpperCase();
        switch (key) {
            case "CUST-4401": return "[{\"ticketId\":\"TKT-1001\",\"subject\":\"Cannot access account\",\"status\":\"OPEN\",\"priority\":\"HIGH\"}]";
            case "CUST-7723": return "[{\"ticketId\":\"TKT-1002\",\"subject\":\"Refund request for duplicate charge\",\"status\":\"IN_PROGRESS\",\"priority\":\"MEDIUM\"}]";
            case "CUST-5512": return "[{\"ticketId\":\"TKT-1004\",\"subject\":\"API returning 500 errors\",\"status\":\"OPEN\",\"priority\":\"CRITICAL\"}]";
            default: return "[]";
        }
    }
}
