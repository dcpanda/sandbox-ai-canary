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

import static java.util.regex.Pattern.CASE_INSENSITIVE;

/**
 * Simulates a multi-turn LangChain4j agent loop with deterministic, tool-aware responses.
 *
 * Phase 1: Detects tool-calling intent and returns AiMessage with toolExecutionRequests.
 * Phase 2: After tool results return, produces a final JSON response for structured output.
 */
public class FakeChatModel implements ChatModel {

    private List<ChatModelListener> listeners = new java.util.ArrayList<>();
    private final List<String> invocationLog = new CopyOnWriteArrayList<>();

    private static final String TOOL_RESULT_MARKER = "\u0000";
    private static final Pattern CUST_ID = Pattern.compile("CUST-[A-Z0-9]+", CASE_INSENSITIVE);
    private static final Pattern TICKET_ID = Pattern.compile("TKT-[0-9]+", CASE_INSENSITIVE);
    private static final Pattern TRIAGE_KEYWORDS = Pattern.compile(
            "\\b(triage|active|critical|dashboard|escalat|prioritize)\\b", CASE_INSENSITIVE);

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
            return um.singleText();
        }
        if (last instanceof ToolExecutionResultMessage tm) {
            return TOOL_RESULT_MARKER + tm.toolName() + TOOL_RESULT_MARKER + tm.text();
        }
        return "";
    }

    private boolean hasToolCallingIntent(String prompt) {
        if (prompt.startsWith(TOOL_RESULT_MARKER)) return false;
        if (CUST_ID.matcher(prompt).find()) return true;
        if (TICKET_ID.matcher(prompt).find()) return true;
        if (TRIAGE_KEYWORDS.matcher(prompt).find()) return true;
        return false;
    }

    private ToolExecutionRequest buildToolExecutionRequest(String prompt) {
        boolean hasTriage = TRIAGE_KEYWORDS.matcher(prompt).find();
        boolean hasTicket = TICKET_ID.matcher(prompt).find();
        boolean hasCustomer = CUST_ID.matcher(prompt).find();

        // If both a ticket ID and triage intent are present, look up the ticket first
        if (hasTicket) {
            String ticketId = extractValue(prompt, TICKET_ID);
            return ToolExecutionRequest.builder()
                    .id("tool-1").name("lookupTicket")
                    .arguments("{\"ticketId\":\"" + ticketId + "\"}")
                    .build();
        }
        if (hasCustomer) {
            String custId = extractValue(prompt, CUST_ID);
            // If both customer and triage intent, list active tickets for that customer priority
            if (hasTriage) {
                return ToolExecutionRequest.builder()
                        .id("tool-3").name("listActiveTickets")
                        .arguments("{\"filterPriority\":\"CRITICAL\"}")
                        .build();
            }
            return ToolExecutionRequest.builder()
                    .id("tool-2").name("lookupCustomer")
                    .arguments("{\"customerId\":\"" + custId + "\"}")
                    .build();
        }
        if (hasTriage) {
            return ToolExecutionRequest.builder()
                    .id("tool-4").name("listActiveTickets")
                    .arguments("{\"filterPriority\":\"CRITICAL\"}")
                    .build();
        }
        return ToolExecutionRequest.builder()
                .id("tool-5").name("listActiveTickets")
                .arguments("{\"filterPriority\":\"CRITICAL\"}")
                .build();
    }

    private String generateFinalResponse(String prompt) {
        boolean hadToolResult = prompt.startsWith(TOOL_RESULT_MARKER);
        String userPart = prompt;

        String toolName = null;
        String bodyPart = null;
        if (hadToolResult) {
            int firstSep = prompt.indexOf(TOOL_RESULT_MARKER);
            int secondSep = prompt.indexOf(TOOL_RESULT_MARKER, firstSep + 1);
            if (firstSep >= 0 && secondSep > firstSep) {
                toolName = prompt.substring(firstSep + 1, secondSep);
                bodyPart = prompt.substring(secondSep + 1);
                userPart = toolName + " " + bodyPart;
            }
        }

        // For tool results, check body for context-specific responses.
        // For direct queries (no tool results), extract entity IDs to tailor the response.
        if (!hadToolResult) {
            String origCust = extractValue(userPart, CUST_ID);
            String origTicket = extractValue(userPart, TICKET_ID);

            if (origCust != null && origTicket != null) {
                return json("ticketId", origTicket, "category", "BUG", "urgency", "IMMEDIATE",
                        "routing", "Escalate to engineering on-call",
                        "summary", "ENTERPRISE customer with CRITICAL ticket.");
            }
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

        // Direct query (no tool results) — match triage-related keywords
        if (TRIAGE_KEYWORDS.matcher(prompt).find()) {
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
