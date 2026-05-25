package com.codetoculture.canary.agent;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class AgentConfig {

    @Value("${openai.api.key:demo}")
    private String openAiApiKey;

    private final LangSmithChatModelListener langSmithListener;

    public AgentConfig(LangSmithChatModelListener langSmithListener) {
        this.langSmithListener = langSmithListener;
    }

    // ---- LLM Provider beans ----

    @Bean
    @ConditionalOnProperty(name = "canary.model.provider", havingValue = "openai")
    public ChatModel openAiChatModel() {
        return OpenAiChatModel.builder()
                .apiKey(openAiApiKey)
                .listeners(java.util.Collections.singletonList(langSmithListener))
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "canary.model.provider", havingValue = "ollama")
    public ChatModel ollamaChatModel(
            @Value("${ollama.base.url:http://localhost:11434}") String baseUrl,
            @Value("${ollama.model.name:llama3}") String modelName
    ) {
        return OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .responseFormat(ResponseFormat.JSON)
                .build();
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "canary.model.provider", havingValue = "fake", matchIfMissing = true)
    public ChatModel fakeChatModel() {
        FakeChatModel model = new FakeChatModel();
        model.setListeners(java.util.Collections.singletonList(langSmithListener));
        return model;
    }

    // ---- Agent bean ----

    @Bean
    public CanaryAgent canaryAgent(ChatModel model,
                                   com.codetoculture.canary.tools.CustomerLookup customerLookup,
                                   com.codetoculture.canary.tools.TicketUpdater ticketUpdater) {
        return new CanaryAgent(model, customerLookup, ticketUpdater);
    }
}
