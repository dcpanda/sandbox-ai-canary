package com.codetoculture.canary.tools;

import java.util.Optional;

/**
 * Shared mock data store — each method returns a hardcoded JSON string.
 * This avoids Map.ofEntries null-value issues and ensures deterministic output.
 */
public class MockTicketDatabase {

    private static final String TK_TKT1001 = "{\"ticketId\":\"TKT-1001\",\"customerId\":\"CUST-4401\",\"subject\":\"Cannot access account after password reset\",\"status\":\"OPEN\",\"priority\":\"HIGH\",\"category\":\"AUTH\",\"createdAt\":\"2026-05-20T09:15:00Z\",\"description\":\"User reports being locked out after password reset flow.\"}";
    private static final String TK_TKT1002 = "{\"ticketId\":\"TKT-1002\",\"customerId\":\"CUST-7723\",\"subject\":\"Refund request for duplicate charge\",\"status\":\"IN_PROGRESS\",\"priority\":\"MEDIUM\",\"category\":\"BILLING\",\"createdAt\":\"2026-05-19T14:30:00Z\",\"description\":\"Customer was charged twice for subscription renewal.\"}";
    private static final String TK_TKT1003 = "{\"ticketId\":\"TKT-1003\",\"customerId\":\"CUST-2290\",\"subject\":\"Feature request: export data to CSV\",\"status\":\"NEW\",\"priority\":\"LOW\",\"category\":\"FEATURE_REQUEST\",\"createdAt\":\"2026-05-21T11:00:00Z\",\"description\":\"User wants ability to export dashboard data in CSV.\"}";
    private static final String TK_TKT1004 = "{\"ticketId\":\"TKT-1004\",\"customerId\":\"CUST-5512\",\"subject\":\"API returning 500 errors on /v2/orders endpoint\",\"status\":\"OPEN\",\"priority\":\"CRITICAL\",\"category\":\"BUG\",\"createdAt\":\"2026-05-21T16:45:00Z\",\"description\":\"Production API consistently returns 500 for order lookups.\"}";
    private static final String TK_TKT1005 = "{\"ticketId\":\"TKT-1005\",\"customerId\":\"CUST-3380\",\"subject\":\"Update billing address for corporate account\",\"status\":\"RESOLVED\",\"priority\":\"LOW\",\"category\":\"ACCOUNT_UPDATE\",\"createdAt\":\"2026-05-18T08:00:00Z\",\"description\":\"Corporate account needs billing address updated.\"}";

    private static final String CU_CUST4401 = "{\"customerId\":\"CUST-4401\",\"name\":\"Acme Corp\",\"tier\":\"ENTERPRISE\",\"contractValue\":120000}";
    private static final String CU_CUST7723 = "{\"customerId\":\"CUST-7723\",\"name\":\"Globex Inc\",\"tier\":\"PROFESSIONAL\",\"contractValue\":24000}";
    private static final String CU_CUST2290 = "{\"customerId\":\"CUST-2290\",\"name\":\"Wayne Enterprises\",\"tier\":\"ENTERPRISE\",\"contractValue\":250000}";
    private static final String CU_CUST5512 = "{\"customerId\":\"CUST-5512\",\"name\":\"Stark Industries\",\"tier\":\"ENTERPRISE\",\"contractValue\":500000}";
    private static final String CU_CUST3380 = "{\"customerId\":\"CUST-3380\",\"name\":\"Umbrella Corp\",\"tier\":\"PROFESSIONAL\",\"contractValue\":18000}";

    public Optional<String> findTicketById(String ticketId) {
        switch (ticketId) {
            case "TKT-1001": return Optional.of(TK_TKT1001);
            case "TKT-1002": return Optional.of(TK_TKT1002);
            case "TKT-1003": return Optional.of(TK_TKT1003);
            case "TKT-1004": return Optional.of(TK_TKT1004);
            case "TKT-1005": return Optional.of(TK_TKT1005);
            default: return Optional.empty();
        }
    }

    public Optional<String> findTicketsByCustomer(String customerId) {
        if (customerId.equals("CUST-4401")) return Optional.of("[" + TK_TKT1001 + "]");
        if (customerId.equals("CUST-7723")) return Optional.of("[" + TK_TKT1002 + "]");
        if (customerId.equals("CUST-2290")) return Optional.of("[" + TK_TKT1003 + "]");
        if (customerId.equals("CUST-5512")) return Optional.of("[" + TK_TKT1004 + "]");
        if (customerId.equals("CUST-3380")) return Optional.empty();
        return Optional.empty();
    }

    public Optional<String> findCustomerById(String customerId) {
        switch (customerId) {
            case "CUST-4401": return Optional.of(CU_CUST4401);
            case "CUST-7723": return Optional.of(CU_CUST7723);
            case "CUST-2290": return Optional.of(CU_CUST2290);
            case "CUST-5512": return Optional.of(CU_CUST5512);
            case "CUST-3380": return Optional.of(CU_CUST3380);
            default: return Optional.empty();
        }
    }

    public Optional<String> listActiveTickets(String filterPriority) {
        String active = "[" + TK_TKT1001 + ", " + TK_TKT1004;
        if (filterPriority == null || filterPriority.isBlank() || filterPriority.equals("CRITICAL")) {
            return Optional.of(active + " " + TK_TKT1004.substring(0, TK_TKT1004.lastIndexOf("}") + 1) + "]");
        }
        if (filterPriority.equals("HIGH")) return Optional.of("[" + TK_TKT1001 + "]");
        return Optional.empty();
    }

    public Optional<String> updateTicketStatus(String ticketId, String newStatus) {
        String json = findTicketById(ticketId).orElse("{\"error\":\"Not found\"}");
        return Optional.of(json.replace("\"status\":\"", "\"status\":\"" + newStatus + "\""));
    }
}
