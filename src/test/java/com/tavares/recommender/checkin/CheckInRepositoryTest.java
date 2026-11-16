package com.tavares.recommender.checkin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/** Runs the real Flyway migration and real SQL against an in-memory H2 database. */
@SpringBootTest
@ActiveProfiles("test")
class CheckInRepositoryTest {

    @Autowired
    private CheckInRepository repository;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void clean() {
        jdbc.sql("DELETE FROM check_in_emotion").update();
        jdbc.sql("DELETE FROM check_in").update();
    }

    @Test
    void savesAndReadsBack() {
        Instant t = Instant.parse("2026-11-16T10:15:30Z");
        CheckIn saved = repository.save(t, List.of(Emotion.ANXIOUS, Emotion.TIRED));

        List<CheckIn> found = repository.findRecent(10);
        assertEquals(1, found.size());
        assertEquals(saved.id(), found.getFirst().id());
        assertEquals(t, found.getFirst().createdAt());
        assertEquals(List.of(Emotion.ANXIOUS, Emotion.TIRED), found.getFirst().emotions());
    }

    @Test
    void recentIsNewestFirstAndLimited() {
        repository.save(Instant.parse("2026-11-14T08:00:00Z"), List.of(Emotion.SAD));
        repository.save(Instant.parse("2026-11-16T08:00:00Z"), List.of(Emotion.HAPPY));
        repository.save(Instant.parse("2026-11-15T08:00:00Z"), List.of(Emotion.CALM));

        List<CheckIn> recent = repository.findRecent(2);
        assertEquals(List.of(Emotion.HAPPY), recent.get(0).emotions());
        assertEquals(List.of(Emotion.CALM), recent.get(1).emotions());
        assertEquals(2, recent.size());
    }

    @Test
    void betweenIncludesStartAndExcludesEnd() {
        repository.save(Instant.parse("2026-11-10T00:00:00Z"), List.of(Emotion.SAD));    // exactly at start
        repository.save(Instant.parse("2026-11-12T12:00:00Z"), List.of(Emotion.CALM));
        repository.save(Instant.parse("2026-11-14T00:00:00Z"), List.of(Emotion.HAPPY));  // exactly at end

        List<CheckIn> found = repository.findBetween(Instant.parse("2026-11-10T00:00:00Z"),
            Instant.parse("2026-11-14T00:00:00Z"));
        assertEquals(List.of(List.of(Emotion.SAD), List.of(Emotion.CALM)),
            found.stream().map(CheckIn::emotions).toList());
    }

    @Test
    void deleteRemovesTheCheckInAndItsEmotions() {
        CheckIn saved = repository.save(Instant.parse("2026-11-16T08:00:00Z"), List.of(Emotion.SAD, Emotion.TIRED));

        assertTrue(repository.delete(saved.id()));
        assertTrue(repository.findAll().isEmpty());
        assertEquals(0, jdbc.sql("SELECT COUNT(*) FROM check_in_emotion").query(Integer.class).single());
        assertFalse(repository.delete(saved.id()), "deleting twice reports not found");
    }

    @Test
    void findAllIsOldestFirst() {
        repository.save(Instant.parse("2026-11-16T08:00:00Z"), List.of(Emotion.HAPPY));
        repository.save(Instant.parse("2026-11-01T08:00:00Z"), List.of(Emotion.SAD));
        assertEquals(List.of(Emotion.SAD), repository.findAll().getFirst().emotions());
        assertEquals(2, repository.findAll().size());
    }
}
