package com.tavares.recommender.checkin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/** {@code note} and {@code flagged} are optional: the person's own words, kept only if they ticked "keep my words". */
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

    public boolean isFlagged() {
        return Boolean.TRUE.equals(flagged);
    }
}
