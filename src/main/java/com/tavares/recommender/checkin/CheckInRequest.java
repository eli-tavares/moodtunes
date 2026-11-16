package com.tavares.recommender.checkin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * The body of {@code POST /api/checkins}.
 *
 * @param emotions the emotions the person picked: 1 to {@value Emotion#MAX_PER_CHECK_IN} words from the fixed vocabulary
 * @param note     the person's own words, kept only if they ticked "keep my words" (at most 500 characters)
 * @param flagged  whether the AI flagged the sentence; only stored when a note is kept
 * @param intensity strength from 1 (barely) to 5 (very strong), or {@code null} when not given
 */
public record CheckInRequest(
    @NotEmpty(message = "emotions must not be empty")
    @Size(max = Emotion.MAX_PER_CHECK_IN, message = "emotions must have at most 3 entries")
    List<String> emotions,
    @Size(max = 500, message = "note must be at most 500 characters")
    String note,
    Boolean flagged,
    @Min(value = 1, message = "intensity must be between 1 and 5")
    @Max(value = 5, message = "intensity must be between 1 and 5")
    Integer intensity
) {

    /**
     * Reads the optional flag.
     *
     * @return {@code true} only when the client sent {@code flagged: true}
     */
    public boolean isFlagged() {
        return Boolean.TRUE.equals(flagged);
    }
}
