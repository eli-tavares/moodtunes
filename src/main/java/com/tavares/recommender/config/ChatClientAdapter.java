package com.tavares.recommender.config;

import com.tavares.recommender.service.AiResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * The one place that calls the AI model. It hides the provider behind a plain "system prompt in, text out" method and
 * makes sure failures never leak upstream detail (such as part of an API key) to the browser.
 */
@Component
public class ChatClientAdapter {

    private static final Logger log = LoggerFactory.getLogger(ChatClientAdapter.class);

    private final ChatClient chatClient;

    /**
     * Creates the adapter.
     *
     * @param chatClient the provider-neutral client built in {@link AiConfig}
     */
    public ChatClientAdapter(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * Sends one request to the model and returns its text answer.
     *
     * <p>Any upstream failure is logged here with full detail and rethrown as an {@link AiResponseException} with a
     * generic message, so nothing sensitive reaches API clients.
     *
     * @param systemPrompt the instructions for the model
     * @param userPrompt   the user's part of the conversation
     * @return the model's answer, never blank
     * @throws AiResponseException if the call fails or the answer is empty
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
