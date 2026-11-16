package com.tavares.recommender.checkin;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/** {@code note} is only present when the person chose to keep their words; {@code flagged} only when true. */
public record CheckInResponse(long id, Instant createdAt, List<String> emotions,
                              @JsonInclude(JsonInclude.Include.NON_NULL) String note,
                              @JsonInclude(JsonInclude.Include.NON_DEFAULT) boolean flagged,
                              @JsonInclude(JsonInclude.Include.NON_NULL) Integer intensity) {

    public static CheckInResponse from(CheckIn checkIn) {
        return new CheckInResponse(checkIn.id(), checkIn.createdAt(),
            checkIn.emotions().stream().map(Emotion::code).toList(), checkIn.note(), checkIn.flagged(),
            checkIn.intensity());
    }
}
