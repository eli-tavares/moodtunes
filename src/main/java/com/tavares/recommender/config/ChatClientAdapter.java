package com.tavares.recommender.config;

import com.tavares.recommender.service.AiResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class ChatClientAdapter {

    private static final Logger log = LoggerFactory.getLogger(ChatClientAdapter.class);

    private final ChatClient chatClient;

    public ChatClientAdapter(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * Calls the model. Any upstream failure is logged here (with detail) and rethrown as an
     * {@link AiResponseException} carrying no upstream detail, so nothing sensitive reaches API clients.
     */
    public String call(String systemPrompt, String userPrompt) {
        String content;
        try {
            content = chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();
        } catch (RuntimeException e) {
            log.error("AI call failed", e);
            throw new AiResponseException("The AI service is currently unavailable.", e);
        }
        if (content == null || content.isBlank()) {
            throw new AiResponseException("The AI service returned an empty response.");
        }
        return content;
    }
}
