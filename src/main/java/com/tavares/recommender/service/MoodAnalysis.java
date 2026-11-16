package com.tavares.recommender.service;

import java.util.List;

/**
 * What the AI made of a sentence.
 *
 * @param emotions emotion codes from the fixed vocabulary, best fit first
 * @param flagged  {@code true} if the sentence looked like it was about self-harm
 */
public record MoodAnalysis(List<String> emotions, boolean flagged) {
}
