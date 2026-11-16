package com.tavares.recommender.checkin;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/**
 * A saved check-in as the API returns it. Optional parts are left out of the JSON when empty.
 *
 * @param id        database id
 * @param createdAt when it was saved
 * @param emotions  the emotion codes, for example {@code ["sad", "tired"]}
 * @param note      the person's own words; present only when they chose to keep them
 * @param flagged   present (as {@code true}) only when the AI flagged the sentence
 * @param intensity strength 1-5; present only when given
 */
public record CheckInResponse(long id, Instant createdAt, List<String> emotions,
                              @JsonInclude(JsonInclude.Include.NON_NULL) String note,
                              @JsonInclude(JsonInclude.Include.NON_DEFAULT) boolean flagged,
                              @JsonInclude(JsonInclude.Include.NON_NULL) Integer intensity) {

    /**
     * Converts a stored check-in into its API form.
     *
     * @param checkIn the stored check-in
     * @return the response object
     */
    public static CheckInResponse from(CheckIn checkIn) {
        return new CheckInResponse(checkIn.id(), checkIn.createdAt(),
            checkIn.emotions().stream().map(Emotion::code).toList(), checkIn.note(), checkIn.flagged(),
            checkIn.intensity());
    }
}
