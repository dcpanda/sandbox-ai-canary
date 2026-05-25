package com.codetoculture.canary.agent;

import com.codetoculture.canary.model.TriageResult;
import com.codetoculture.canary.tools.CustomerLookup;
import com.codetoculture.canary.tools.TicketUpdater;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import org.springframework.stereotype.Service;

/**
 * The Canary Agent — an AI service that exercises the same tool-calling patterns
 * as your production app. Its system prompt mirrors the real business domain:
 * customer support ticket triage.
 *
 * Switch between models (Ollama, OpenAI, etc.) via AgentConfig to detect
 * which provider handles this domain correctly.
 */
@Service
public class CanaryAgent {

    public interface CanaryAssistant {
        @SystemMessage("""
                You are a customer support triage engineer.
                Your job is to use the available tools to look up customer accounts,
                retrieve ticket information, list active tickets, and update ticket statuses.

                After using any tools, you must produce a final response in the required JSON format.
                ALWAYS output the required JSON — never explain why the format doesn't fit.

                Rules for mapping queries to the JSON format:
                - If the user asks about a TICKET (ticketId present), populate all fields from the ticket data.
                - If the user asks ONLY about a CUSTOMER (no ticketId), set:
                  ticketId = the customer ID,
                  category = "ACCOUNT_LOOKUP",
                  urgency = "MEDIUM",
                  routing = "Review account tier",
                  summary = a brief description of the account details found.
                - If the user asks a general question (no customer or ticket), set:
                  ticketId = "",
                  category = "UNKNOWN",
                  urgency = "LOW",
                  routing = "No action needed",
                  summary = "No customer or ticket information available."
                - When listing active tickets, pick the highest priority ticket for the result.
                """)
        TriageResult chat(String userMessage);
    }

    private final CanaryAssistant assistant;

    public CanaryAgent(
            ChatModel model,
            CustomerLookup customerLookup,
            TicketUpdater ticketUpdater
    ) {
        this.assistant = AiServices.builder(CanaryAssistant.class)
                .chatModel(model)
                .tools(customerLookup, ticketUpdater)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build();
    }

    public TriageResult ask(String userMessage) {
        return assistant.chat(userMessage);
    }
}
