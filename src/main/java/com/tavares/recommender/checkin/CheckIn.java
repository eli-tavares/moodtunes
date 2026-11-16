package com.tavares.recommender.checkin;

import java.time.Instant;
import java.util.List;

/**
 * One saved moment: when, which emotions the user picked and, only if they chose to keep it, their own words
 * ({@code note}), whether the AI flagged the sentence as worrying and how strong the feeling was
 * ({@code intensity} 1-5, or {@code null} when not given).
 */
public record CheckIn(long id, Instant createdAt, List<Emotion> emotions, String note, boolean flagged,
                      Integer intensity) {

    public CheckIn(long id, Instant createdAt, List<Emotion> emotions) {
        this(id, createdAt, emotions, null, false, null);
    }

    public CheckIn(long id, Instant createdAt, List<Emotion> emotions, String note, boolean flagged) {
        this(id, createdAt, emotions, note, flagged, null);
    }

    /**
     * Mood score of this check-in: the average score of its emotions, ignoring body states.
     * {@code null} when it only contains body states (e.g. just "hungry").
     */
    public Double mood() {
        return emotions.stream()
            .map(Emotion::valence)
            .filter(v -> v != null)
            .mapToInt(Integer::intValue)
            .average()
            .stream()
            .boxed()
            .findFirst()
            .orElse(null);
    }
}
