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
                You are a customer support triage engineer. Your job is to:
                1. Look up customer account details when given a customer ID
                2. Retrieve support ticket information when given a ticket ID
                3. List active tickets to identify critical issues
                4. Update ticket status (escalate, resolve, or close) as part of triage
                5. Produce a prioritized TriageResult with urgency, routing recommendation, and summary
                Prioritize by: CRITICAL/HIGH priority > ENTERPRISE tier > older tickets.
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
