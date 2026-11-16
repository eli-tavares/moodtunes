package com.tavares.recommender.service;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.tavares.recommender.checkin.Emotion;
import com.tavares.recommender.config.ChatClientAdapter;
import com.tavares.recommender.dtos.MusicRecommendation;
import com.tavares.recommender.prompts.PromptLoader;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Suggests songs or albums for a selection of emotions. Results are cached for a while, because the same selection
 * gives an equally good answer and every model call costs money.
 */
@Service
public class MusicService {

    private static final String SYSTEM_PROMPT_FILE = "music-prompt.txt";
    private static final int MAX_ITEMS = 5;

    private final ChatClientAdapter chatAdapter;
    private final ObjectMapper objectMapper;
    private final String systemPrompt;

    /**
     * Creates the service and loads the system prompt.
     *
     * @param chatAdapter  the model gateway
     * @param objectMapper reads the model's JSON answer
     */
    public MusicService(ChatClientAdapter chatAdapter, ObjectMapper objectMapper) {
        this.chatAdapter = chatAdapter;
        this.objectMapper = objectMapper;
        this.systemPrompt = loadSystemPrompt();
    }

    /**
     * Returns recommendations for a selection of emotions.
     *
     * <p>The prompt includes a mood of {@code good}, {@code low} or {@code mixed}, worked out in Java from the emotion
     * scores. The <em>parsed</em> list is cached, so a malformed model answer is never cached: it raises
     * {@link AiResponseException} and the next call retries.
     *
     * @param emotions 1-3 emotions, already validated and in stable order (see {@link Emotion#parseAll})
     * @param type     songs or albums
     * @return at most five suggestions
     * @throws AiResponseException if the model's answer cannot be used
     */
    @Cacheable(value = "musicRecommendations", key = "#emotions.toString() + '_' + #type")
    public List<MusicRecommendation> recommend(List<Emotion> emotions, MusicType type) {
        String emotionList = emotions.stream().map(Emotion::code).collect(Collectors.joining(", "));
        String userPrompt = "Emotions: " + emotionList + "\nMood: " + Emotion.tone(emotions)
            + "\nType: " + type.getString();
        return parse(chatAdapter.call(systemPrompt, userPrompt));
    }

    /**
     * Reads the model's answer.
     *
     * <p>Models like to wrap JSON in markdown fences or add a sentence around it, so the outermost array is taken. Items
     * without a title are dropped and the list is cut to the maximum.
     *
     * @param modelOutput the raw text the model returned
     * @return the valid suggestions
     * @throws AiResponseException if no array can be read or none of its items is usable
     */
    List<MusicRecommendation> parse(String modelOutput) {
        // Models like to wrap JSON in markdown fences or add a sentence around it; take the outermost array.
        int start = modelOutput.indexOf('[');
        int end = modelOutput.lastIndexOf(']');
        if (start < 0 || end <= start) {
            throw new AiResponseException("The AI service returned an unreadable answer.");
        }
        try {
            List<MusicRecommendation> items = objectMapper.readValue(
                modelOutput.substring(start, end + 1), new TypeReference<List<MusicRecommendation>>() {
                });
            List<MusicRecommendation> valid = items.stream()
                .filter(i -> i != null && i.getTitle() != null && !i.getTitle().isBlank())
                .limit(MAX_ITEMS)
                .toList();
            if (valid.isEmpty()) {
                throw new AiResponseException("The AI service returned no recommendations.");
            }
            return valid;
        } catch (JacksonException e) {
            throw new AiResponseException("The AI service returned an unreadable answer.", e);
        }
    }

    /**
     * Loads the music prompt.
     *
     * @return the prompt file, or a built-in fallback if it cannot be read
     */
    private static String loadSystemPrompt() {
        String fallback = """
            You are a helpful music recommendation assistant.
            Suggest exactly 5 real songs or albums that fit the user's emotions and mood (good, low or mixed).
            Return ONLY a JSON array of objects with "title", "artist" and "description" properties.
            """;
        return new PromptLoader(SYSTEM_PROMPT_FILE, fallback).loadPromptAsString();
    }
}
