package com.codetoculture.canary.agent;

import dev.langchain4j.model.chat.listener.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * A minimalist LangSmith integration listener for LangChain4j.
 * Demonstrates how to capture LLM traces and forward them to LangSmith (simulated).
 */
@Component
public class LangSmithChatModelListener implements ChatModelListener {

    private static final Logger log = LoggerFactory.getLogger(LangSmithChatModelListener.class);

    private final boolean tracingEnabled;
    private final String projectName;

    public LangSmithChatModelListener(
            @Value("${langsmith.tracing:false}") boolean tracingEnabled,
            @Value("${langsmith.project:canary-sandbox}") String projectName) {
        this.tracingEnabled = tracingEnabled;
        this.projectName = projectName;
        
        if (tracingEnabled) {
            log.info("[LANGSMITH] Observability enabled for project: {}", projectName);
        }
    }

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        if (!tracingEnabled) return;

        log.info("[LANGSMITH] Trace Started | Request: {}", 
                requestContext.chatRequest().messages());
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        if (!tracingEnabled) return;

        log.info("[LANGSMITH] Trace Finished | Response: {} | Tokens: {}", 
                responseContext.chatResponse().aiMessage().text(),
                responseContext.chatResponse().metadata().tokenUsage());
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        if (!tracingEnabled) return;

        log.error("[LANGSMITH] Trace Error | Message: {}", 
                errorContext.error().getMessage());
    }
}
