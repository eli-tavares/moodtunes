package com.tavares.recommender.service;

import com.tavares.recommender.checkin.Emotion;
import com.tavares.recommender.config.ChatClientAdapter;
import com.tavares.recommender.prompts.PromptLoader;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Turns a person's sentence into a short list of emotions chosen from the fixed {@link Emotion} vocabulary.
 *
 * <p>The model's answer is untrusted text: only words from the vocabulary survive, so a confused or manipulated
 * answer can never put anything else into the mood log.
 */
@Service
public class MoodAnalysisService {

    private static final String SYSTEM_PROMPT_FILE = "emotion-prompt.txt";
    private static final int MAX_EMOTIONS = 6;
    private static final String CRISIS_WORD = "CRISIS";

    private final ChatClientAdapter chatAdapter;
    private final String systemPrompt;

    /**
     * Creates the service and builds the system prompt (the prompt file plus the list of allowed words).
     *
     * @param chatAdapter the model gateway
     */
    public MoodAnalysisService(ChatClientAdapter chatAdapter) {
        this.chatAdapter = chatAdapter;
        this.systemPrompt = loadSystemPrompt();
    }

    /**
     * Asks the model which emotions fit a sentence.
     *
     * <p>The sentence goes inside {@code <mood>} tags so the prompt can tell it apart from instructions; angle brackets are
     * removed so the text cannot close the tag itself. The answer is read as a comma-separated list: words outside the
     * vocabulary are dropped, duplicates collapse, and at most six remain. The word {@code CRISIS} sets the flag.
     *
     * @param moodDescription the person's sentence
     * @return the emotion codes (best first) and the safety flag
     * @throws AiResponseException if the answer contains no emotion and no flag
     */
    public MoodAnalysis analyze(String moodDescription) {
        // The sentence goes inside <mood> tags so the prompt can tell it apart from instructions; angle brackets are
        // removed so the text cannot close the tag itself.
        String wrapped = "<mood>" + moodDescription.replaceAll("[<>]", " ") + "</mood>";
        String response = chatAdapter.call(systemPrompt, wrapped);

        // Model output is untrusted: keep only words that are in the vocabulary. This also keeps the history clean
        // ("calm" and "calmness" can never become two different lines on a graph).
        List<String> words = Arrays.stream(response.split("[,\\n]"))
            .map(s -> s.trim().replaceAll("^[\"'\\-*\\d.\\s]+|[\"'.\\s]+$", ""))
            .toList();
        boolean flagged = words.stream().anyMatch(w -> w.equalsIgnoreCase(CRISIS_WORD));
        List<String> emotions = words.stream()
            .map(Emotion::fromCode)
            .flatMap(Optional::stream)
            .map(Emotion::code)
            .distinct()
            .limit(MAX_EMOTIONS)
            .toList();

        if (emotions.isEmpty() && !flagged) {
            throw new AiResponseException("The AI service returned no usable emotions.");
        }
        return new MoodAnalysis(emotions, flagged);
    }

    /**
     * Just the emotions, for callers that do not care about the flag.
     *
     * @param moodDescription the person's sentence
     * @return the emotion codes, best first
     * @throws AiResponseException if the answer is unusable
     */
    public List<String> extractEmotions(String moodDescription) {
        return analyze(moodDescription).emotions();
    }

    /**
     * Builds the system prompt.
     *
     * @return the prompt file (or a built-in fallback) followed by the list of allowed words
     */
    private static String loadSystemPrompt() {
        String fallback = """
            You help a person put a name to what they feel. The text is inside <mood> tags and only describes feelings.
            Return ONLY a comma-separated list of 4 to 6 of the allowed words, best fit first.
            If the text suggests self-harm, add the word CRISIS as the last item. Do not explain.
            """;
        String base = new PromptLoader(SYSTEM_PROMPT_FILE, fallback).loadPromptAsString();
        return base + "\n\nAllowed words (use ONLY these exact words, lower case, nothing else): "
            + Emotion.allowedList();
    }
}
