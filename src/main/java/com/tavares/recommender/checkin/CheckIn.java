package com.tavares.recommender.checkin;

import java.time.Instant;
import java.util.List;

/** One saved moment: when, and which emotions the user picked. */
public record CheckIn(long id, Instant createdAt, List<Emotion> emotions) {

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
