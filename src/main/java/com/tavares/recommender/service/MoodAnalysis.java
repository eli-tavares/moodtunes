package com.tavares.recommender.service;

import java.util.List;

/** What the AI made of a sentence: emotion codes from the fixed vocabulary, and whether it looked worrying. */
public record MoodAnalysis(List<String> emotions, boolean flagged) {
}
