package com.tavares.recommender.checkin;

import java.time.Instant;
import java.util.List;

public record CheckInResponse(long id, Instant createdAt, List<String> emotions) {

    public static CheckInResponse from(CheckIn checkIn) {
        return new CheckInResponse(checkIn.id(), checkIn.createdAt(),
            checkIn.emotions().stream().map(Emotion::code).toList());
    }
}
