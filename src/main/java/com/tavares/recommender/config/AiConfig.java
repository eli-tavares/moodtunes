package com.tavares.recommender.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provider-neutral wiring: {@code spring.ai.model.chat} (see application.yaml / the "claude" profile) decides
 * which {@link ChatModel} is auto-configured, and the rest of the app only ever sees a {@link ChatClient}.
 */
@Configuration
public class AiConfig {

    /**
     * Exposes the single {@link ChatClient} the application uses to talk to the model.
     *
     * @param chatModel the model auto-configured for the active provider (OpenAI, or Anthropic with the {@code claude}
     *                  profile)
     * @return a client bound to that model
     */
    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.create(chatModel);
    }
}
