package com.tavares.recommender.checkin;

import com.tavares.recommender.service.BadRequestException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CheckInService {

    private final CheckInRepository repository;
    private final Clock clock;

    public CheckInService(CheckInRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Saves a check-in for "now". {@code note} (the person's own words) and {@code intensity} (1-5) are optional.
     * Throws {@code BadRequestException} for unknown or too many emotions, or an intensity outside 1-5.
     */
    public CheckIn save(long userId, List<String> emotionCodes, String note, boolean flagged, Integer intensity) {
        if (intensity != null && (intensity < 1 || intensity > 5)) {
            throw new BadRequestException("intensity must be between 1 and 5.");
        }
        String kept = note == null || note.isBlank() ? null : note.trim();
        return repository.save(userId, Instant.now(clock), Emotion.parseAll(emotionCodes), kept,
            kept != null && flagged, intensity);
    }

    public List<CheckIn> recent(long userId, int limit) {
        return repository.findRecent(userId, Math.max(1, Math.min(limit, 100)));
    }

    public boolean delete(long userId, long id) {
        return repository.delete(userId, id);
    }

    public List<CheckIn> all(long userId) {
        return repository.findAll(userId);
    }
}
