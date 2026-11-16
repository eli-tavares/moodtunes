package com.tavares.recommender.prompts;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;

/** Loads a system prompt from {@code classpath:prompts/<file>}, falling back to a built-in default. */
public class PromptLoader {

    private final String filePrompt;
    private final String defaultSystemPrompt;

    public PromptLoader(String filePrompt, String defaultSystemPrompt) {
        this.filePrompt = filePrompt;
        this.defaultSystemPrompt = defaultSystemPrompt;
    }

    public String loadPromptAsString() {
        try (var in = new ClassPathResource("prompts/" + filePrompt).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return defaultSystemPrompt;
        }
    }
}
