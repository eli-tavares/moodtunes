package com.tavares.recommender.checkin;

import java.time.Instant;
import java.util.List;

/**
 * One saved moment: when, which emotions the user picked and, only if they chose to keep it, their own words, whether
 * the AI flagged the sentence as worrying and how strong the feeling was.
 *
 * @param id        database id
 * @param createdAt when the check-in was saved
 * @param emotions  the emotions the person picked (1 to 3), in vocabulary order
 * @param note      the person's own words, or {@code null} when they chose not to keep them
 * @param flagged   {@code true} if the AI judged the sentence as possibly about self-harm (only stored together with a
 *                  note)
 * @param intensity how strong the feeling was, 1 (barely) to 5 (very strong), or {@code null} when not given
 */
public record CheckIn(long id, Instant createdAt, List<Emotion> emotions, String note, boolean flagged,
                      Integer intensity) {

    /**
     * Creates a check-in with only emotions: no kept words, no flag, no strength.
     *
     * @param id        database id
     * @param createdAt when it was saved
     * @param emotions  the emotions picked
     */
    public CheckIn(long id, Instant createdAt, List<Emotion> emotions) {
        this(id, createdAt, emotions, null, false, null);
    }

    /**
     * Creates a check-in with kept words and a flag but no strength.
     *
     * @param id        database id
     * @param createdAt when it was saved
     * @param emotions  the emotions picked
     * @param note      the person's own words, or {@code null}
     * @param flagged   whether the AI flagged the sentence
     */
    public CheckIn(long id, Instant createdAt, List<Emotion> emotions, String note, boolean flagged) {
        this(id, createdAt, emotions, note, flagged, null);
    }

    /**
     * Mood score of this check-in: the average score of its emotions, ignoring body states.
     *
     * @return a value from -2 (low) to +2 (high), or {@code null} when the check-in only contains body states (for
     *         example just "hungry")
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
