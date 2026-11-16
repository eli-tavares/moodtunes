package com.tavares.recommender.checkin;

import com.tavares.recommender.service.BadRequestException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Saving, listing and deleting a person's check-ins. All methods are scoped to one user id, so one person can never
 * see or change another's log.
 */
@Service
public class CheckInService {

    private final CheckInRepository repository;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param repository where check-ins are stored
     * @param clock      the time source for "now"
     */
    public CheckInService(CheckInRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Saves a check-in for the current moment.
     *
     * @param userId       the owner
     * @param emotionCodes the emotions picked; 1 to {@value Emotion#MAX_PER_CHECK_IN} codes from the fixed vocabulary
     * @param note         the person's own words, or {@code null}/blank for none (trimmed before saving)
     * @param flagged      whether the AI flagged the sentence; ignored when there is no note
     * @param intensity    strength 1-5, or {@code null}
     * @return the saved check-in
     * @throws BadRequestException for unknown or too many emotions, or an intensity outside 1-5
     */
    public CheckIn save(long userId, List<String> emotionCodes, String note, boolean flagged, Integer intensity) {
        if (intensity != null && (intensity < 1 || intensity > 5)) {
            throw new BadRequestException("intensity must be between 1 and 5.");
        }
        String kept = note == null || note.isBlank() ? null : note.trim();
        return repository.save(userId, Instant.now(clock), Emotion.parseAll(emotionCodes), kept,
            kept != null && flagged, intensity);
    }

    /**
     * Lists a person's latest check-ins.
     *
     * @param userId the owner
     * @param limit  how many to return; clamped to 1-100
     * @return the check-ins, newest first
     */
    public List<CheckIn> recent(long userId, int limit) {
        return repository.findRecent(userId, Math.max(1, Math.min(limit, 100)));
    }

    /**
     * Deletes one of the person's check-ins.
     *
     * @param userId the person asking
     * @param id     the check-in to delete
     * @return {@code true} if it was deleted, {@code false} if it was not theirs or did not exist
     */
    public boolean delete(long userId, long id) {
        return repository.delete(userId, id);
    }

    /**
     * Returns everything a person has saved, for the export.
     *
     * @param userId the owner
     * @return all their check-ins, oldest first
     */
    public List<CheckIn> all(long userId) {
        return repository.findAll(userId);
    }
}
