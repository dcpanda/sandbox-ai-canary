package com.codetoculture.canary.agent;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import com.codetoculture.canary.model.TriageResult;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Simulates a multi-turn LangChain4j agent loop with deterministic, tool-aware responses.
 *
 * Phase 1: Detects tool-calling intent and returns AiMessage with toolExecutionRequests.
 * Phase 2: After tool results return, produces a final JSON response for structured output.
 */
public class FakeChatModel implements ChatModel {

    private List<ChatModelListener> listeners = new java.util.ArrayList<>();
    private final List<String> invocationLog = new CopyOnWriteArrayList<>();

    private static final Pattern CUST_ID = Pattern.compile("CUST-[A-Z0-9]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern TICKET_ID = Pattern.compile("TKT-[0-9]+", Pattern.CASE_INSENSITIVE);

    public void setListeners(List<ChatModelListener> listeners) {
        this.listeners = listeners;
    }

    public List<String> getInvocationLog() {
        return List.copyOf(invocationLog);
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        notifyRequest(request);
        String lastText = extractLastUserText(request.messages());

        if (hasToolCallingIntent(lastText)) {
            ToolExecutionRequest toolReq = buildToolExecutionRequest(lastText);
            invocationLog.add("TOOL:" + toolReq.name() + "(" + toolReq.arguments() + ")");
            AiMessage aiMessage = AiMessage.from(List.of(toolReq));
            ChatResponse response = ChatResponse.builder()
                    .aiMessage(aiMessage)
                    .metadata(ChatResponseMetadata.builder().modelName("fake-multi-turn").build())
                    .build();
            notifyResponse(response, request);
            return response;
        }

        String jsonResponse = generateFinalResponse(lastText);
        invocationLog.add("FINAL:" + truncate(jsonResponse, 50));
        AiMessage aiMessage = AiMessage.from(jsonResponse);
        ChatResponse chatResponse = ChatResponse.builder()
                .aiMessage(aiMessage)
                .metadata(ChatResponseMetadata.builder().modelName("fake-multi-turn").build())
                .build();
        notifyResponse(chatResponse, request);
        return chatResponse;
    }

    @Override
    public ChatResponse chat(ChatMessage... messages) {
        return chat(ChatRequest.builder().messages(messages).build());
    }

    @Override
    public ChatResponse chat(List<ChatMessage> messages) {
        return chat(ChatRequest.builder().messages(messages).build());
    }

    private String extractLastUserText(List<ChatMessage> messages) {
        ChatMessage last = messages.get(messages.size() - 1);
        if (last instanceof UserMessage um) {
            return um.singleText().toLowerCase();
        }
        if (last instanceof ToolExecutionResultMessage tm) {
            return "tool_result:" + tm.toolName() + ":" + tm.text();
        }
        return "";
    }

    private boolean hasToolCallingIntent(String prompt) {
        if (prompt.startsWith("tool_result:")) return false;
        if (CUST_ID.matcher(prompt).find()) return true;
        if (TICKET_ID.matcher(prompt).find()) return true;
        if (prompt.contains("triage") || prompt.contains("active")) return true;
        return false;
    }

    private ToolExecutionRequest buildToolExecutionRequest(String prompt) {
        if (prompt.contains("cust-") && prompt.contains("triage")) {
            return ToolExecutionRequest.builder()
                    .id("tool-3").name("listActiveTickets")
                    .arguments("{\"filterPriority\":\"CRITICAL\"}")
                    .build();
        }
        if (TICKET_ID.matcher(prompt).find()) {
            String ticketId = extractValue(prompt, TICKET_ID);
            return ToolExecutionRequest.builder()
                    .id("tool-1").name("lookupTicket")
                    .arguments("{\"ticketId\":\"" + ticketId + "\"}")
                    .build();
        }
        if (CUST_ID.matcher(prompt).find()) {
            String custId = extractValue(prompt, CUST_ID);
            return ToolExecutionRequest.builder()
                    .id("tool-2").name("lookupCustomer")
                    .arguments("{\"customerId\":\"" + custId + "\"}")
                    .build();
        }
        return ToolExecutionRequest.builder()
                .id("tool-4").name("listActiveTickets")
                .arguments("{\"filterPriority\":\"CRITICAL\"}")
                .build();
    }

    private String generateFinalResponse(String prompt) {
        boolean hadToolResult = prompt.startsWith("tool_result:");
        String userPart = hadToolResult ? prompt.substring("tool_result:".length()) : prompt;

        // Separate tool name from tool body when there's a tool result.
        // The tool body should NOT be searched for entity IDs from the original query.
        String toolName = null;
        String bodyPart = null;
        if (hadToolResult) {
            int colonIdx = userPart.indexOf(':');
            if (colonIdx > 0) {
                toolName = userPart.substring(0, colonIdx);
                bodyPart = userPart.substring(colonIdx + 1);
            }
        }

        // Extract entity IDs ONLY from direct queries (no tool results).
        // For tool results, the "original query" is the tool name, not the tool body.
        String origCust = null;
        String origTicket = null;
        if (!hadToolResult) {
            origCust = extractValue(userPart, CUST_ID);
            origTicket = extractValue(userPart, TICKET_ID);
        }

        // Original query had both customer and ticket IDs
        if (origCust != null && origTicket != null) {
            return json("ticketId", origTicket, "category", "BUG", "urgency", "IMMEDIATE",
                    "routing", "Escalate to engineering on-call",
                    "summary", "ENTERPRISE customer with CRITICAL ticket.");
        }

        // Original query was just about a customer lookup
        if (hadToolResult && origCust != null && origTicket == null) {
            return json("ticketId", origCust, "category", "ACCOUNT_LOOKUP", "urgency", "MEDIUM",
                    "routing", "Review account tier",
                    "summary", "Account lookup completed for " + origCust + ".");
        }

        // Original query was just about a ticket lookup
        if (hadToolResult && origCust == null && origTicket != null) {
            return json("ticketId", origTicket, "category", "BILLING", "urgency", "MEDIUM",
                    "routing", "Assign to billing team",
                    "summary", "Ticket " + origTicket + " details retrieved.");
        }

        // Check tool result body for context-specific responses
        if (hadToolResult && bodyPart != null) {
            if ("lookupCustomer".equalsIgnoreCase(toolName)) {
                String custInBody = extractValue(bodyPart, CUST_ID);
                if (custInBody != null) {
                    return json("ticketId", custInBody, "category", "ACCOUNT_LOOKUP", "urgency", "MEDIUM",
                            "routing", "Review account tier",
                            "summary", "Account lookup completed for " + custInBody + ".");
                }
            }

            if ("lookupTicket".equalsIgnoreCase(toolName)) {
                String ticketInBody = extractValue(bodyPart, TICKET_ID);
                if (ticketInBody != null) {
                    return json("ticketId", ticketInBody, "category", "BILLING", "urgency", "MEDIUM",
                            "routing", "Assign to billing team",
                            "summary", "Ticket " + ticketInBody + " details retrieved.");
                }
            }

            // List/dashboard queries — check priority in body
            if (bodyPart.contains("\"priority\":\"CRITICAL\"")) {
                return json("ticketId", "TKT-1004", "category", "BUG",
                        "urgency", TriageResult.Urgency.IMMEDIATE,
                        "routing", "Escalate to engineering on-call",
                        "summary", "Based on tool results: critical ticket identified.");
            }
            if (bodyPart.contains("\"priority\":\"HIGH\"")) {
                return json("ticketId", "TKT-1001", "category", "AUTH",
                        "urgency", TriageResult.Urgency.HIGH,
                        "routing", "Assign to authentication team",
                        "summary", "High priority ticket found.");
            }
            return json("ticketId", "TKT-1004", "category", "BUG",
                    "urgency", TriageResult.Urgency.IMMEDIATE,
                    "routing", "Escalate to on-call engineering",
                    "summary", "Based on tool results: critical ticket identified.");
        }

        // Direct query (no tool results)
        if (prompt.contains("triage") || prompt.contains("active")) {
            return json("ticketId", "TKT-1004", "category", "BUG",
                    "urgency", TriageResult.Urgency.IMMEDIATE,
                    "routing", "Escalate to engineering on-call",
                    "summary", "CRITICAL ticket TKT-1004.");
        }

        return json("ticketId", "", "category", "UNKNOWN", "urgency", "LOW",
                "routing", "No action needed", "summary", "Awaiting customer or ticket ID.");
    }

    private String json(Object... pairs) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < pairs.length; i += 2) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(pairs[i]).append("\":");
            Object val = pairs[i + 1];
            if (val instanceof String s) sb.append("\"").append(s).append("\"");
            else if (val instanceof Enum<?> e) sb.append("\"").append(e.name()).append("\"");
            else if (val instanceof Number n) sb.append(n);
            else sb.append("\"").append(val).append("\"");
        }
        return sb.append("}").toString();
    }

    private String extractValue(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(0) : null;
    }

    private String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) + ".." : s;
    }

    private void notifyRequest(ChatRequest request) {
        if (!listeners.isEmpty()) {
            ChatModelRequestContext ctx = new ChatModelRequestContext(
                    request, dev.langchain4j.model.ModelProvider.OTHER, Map.of());
            listeners.forEach(l -> l.onRequest(ctx));
        }
    }

    private void notifyResponse(ChatResponse response, ChatRequest request) {
        if (!listeners.isEmpty()) {
            ChatModelResponseContext ctx = new ChatModelResponseContext(
                    response, request, dev.langchain4j.model.ModelProvider.OTHER, Map.of());
            listeners.forEach(l -> l.onResponse(ctx));
        }
    }
}
