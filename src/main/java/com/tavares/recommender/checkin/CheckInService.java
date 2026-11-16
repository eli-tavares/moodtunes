package com.tavares.recommender.checkin;

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

    /** Saves a check-in for "now". Throws {@code BadRequestException} for unknown or too many emotions. */
    public CheckIn save(List<String> emotionCodes) {
        return repository.save(Instant.now(clock), Emotion.parseAll(emotionCodes));
    }

    public List<CheckIn> recent(int limit) {
        return repository.findRecent(Math.max(1, Math.min(limit, 100)));
    }

    public boolean delete(long id) {
        return repository.delete(id);
    }

    public List<CheckIn> all() {
        return repository.findAll();
    }
}
