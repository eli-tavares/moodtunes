package com.tavares.recommender.service;

import com.tavares.recommender.checkin.Emotion;
import com.tavares.recommender.config.ChatClientAdapter;
import com.tavares.recommender.prompts.PromptLoader;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class MoodAnalysisService {

    private static final String SYSTEM_PROMPT_FILE = "emotion-prompt.json";
    private static final int MAX_EMOTIONS = 6;

    private final ChatClientAdapter chatAdapter;
    private final String systemPrompt;

    public MoodAnalysisService(ChatClientAdapter chatAdapter) {
        this.chatAdapter = chatAdapter;
        this.systemPrompt = loadSystemPrompt();
    }

    /** Returns emotion codes from the fixed {@link Emotion} vocabulary, most likely first. */
    public List<String> extractEmotions(String moodDescription) {
        String response = chatAdapter.call(systemPrompt, moodDescription);

        // Model output is untrusted: keep only words that are in the vocabulary. This also keeps the history clean
        // ("calm" and "calmness" can never become two different lines on a graph).
        List<String> emotions = Arrays.stream(response.split("[,\\n]"))
            .map(s -> s.trim().replaceAll("^[\"'\\-*\\d.\\s]+|[\"'.\\s]+$", ""))
            .map(Emotion::fromCode)
            .flatMap(Optional::stream)
            .map(Emotion::code)
            .distinct()
            .limit(MAX_EMOTIONS)
            .toList();

        if (emotions.isEmpty()) {
            throw new AiResponseException("The AI service returned no usable emotions.");
        }
        return emotions;
    }

    private static String loadSystemPrompt() {
        String fallback = """
            You are an expert emotional analysis assistant.
            Given a user's mood description, return ONLY a comma-separated list of the emotions they might be feeling.
            Do not explain, do not add dialogue.
            """;
        String base = new PromptLoader(SYSTEM_PROMPT_FILE, fallback).loadPromptAsString();
        return base + "\n\nAllowed words (use ONLY these exact words, lower case, nothing else): "
            + Emotion.allowedList();
    }
}
