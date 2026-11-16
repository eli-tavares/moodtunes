package com.tavares.recommender.service;

import com.tavares.recommender.config.ChatClientAdapter;
import com.tavares.recommender.dtos.MusicRecommendationRequest;
import com.tavares.recommender.prompts.PromptLoader;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class MoodAnalysisService {

    private static final String SYSTEM_PROMPT_FILE = "emotion-prompt.json";
    private static final int MAX_EMOTIONS = 8;
    private static final Pattern VALID_EMOTION = Pattern.compile(MusicRecommendationRequest.EMOTION_REGEX);

    private final ChatClientAdapter chatAdapter;
    private final String systemPrompt;

    public MoodAnalysisService(ChatClientAdapter chatAdapter) {
        this.chatAdapter = chatAdapter;
        this.systemPrompt = loadSystemPrompt();
    }

    public List<String> extractEmotions(String moodDescription) {
        String response = chatAdapter.call(systemPrompt, moodDescription);

        // Model output is untrusted: keep only short, plain-word entries so that whatever we hand to the
        // client (and later accept back as an "emotion") is well-formed.
        List<String> emotions = Arrays.stream(response.split("[,\\n]"))
            .map(s -> s.trim().replaceAll("^[\"'\\-*\\d.\\s]+|[\"'.\\s]+$", ""))
            .filter(s -> VALID_EMOTION.matcher(s).matches())
            .map(String::toLowerCase)
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
            Given a user's mood description, return ONLY a comma-separated list
            of emotions they might be feeling. Keep it short, 3 to 6 emotions.
            Do not explain, do not add dialogue.
            """;
        return new PromptLoader(SYSTEM_PROMPT_FILE, fallback).loadPromptAsString();
    }
}
