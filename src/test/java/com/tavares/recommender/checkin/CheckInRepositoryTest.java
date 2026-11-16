package com.tavares.recommender.checkin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tavares.recommender.user.UserService;
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

    @Autowired
    private UserService users;

    private long elit;
    private long ainek;

    @BeforeEach
    void clean() {
        jdbc.sql("DELETE FROM check_in_emotion").update();
        jdbc.sql("DELETE FROM check_in").update();
        elit = users.idOf("elit");
        ainek = users.idOf("ainek");
    }

    @Test
    void savesAndReadsBack() {
        Instant t = Instant.parse("2026-11-16T10:15:30Z");
        CheckIn saved = repository.save(elit, t, List.of(Emotion.ANXIOUS, Emotion.TIRED));

        List<CheckIn> found = repository.findRecent(elit, 10);
        assertEquals(1, found.size());
        assertEquals(saved.id(), found.getFirst().id());
        assertEquals(t, found.getFirst().createdAt());
        assertEquals(List.of(Emotion.ANXIOUS, Emotion.TIRED), found.getFirst().emotions());
    }

    @Test
    void recentIsNewestFirstAndLimited() {
        repository.save(elit, Instant.parse("2026-11-14T08:00:00Z"), List.of(Emotion.SAD));
        repository.save(elit, Instant.parse("2026-11-16T08:00:00Z"), List.of(Emotion.HAPPY));
        repository.save(elit, Instant.parse("2026-11-15T08:00:00Z"), List.of(Emotion.CALM));

        List<CheckIn> recent = repository.findRecent(elit, 2);
        assertEquals(List.of(Emotion.HAPPY), recent.get(0).emotions());
        assertEquals(List.of(Emotion.CALM), recent.get(1).emotions());
        assertEquals(2, recent.size());
    }

    @Test
    void betweenIncludesStartAndExcludesEnd() {
        repository.save(elit, Instant.parse("2026-11-10T00:00:00Z"), List.of(Emotion.SAD));    // exactly at start
        repository.save(elit, Instant.parse("2026-11-12T12:00:00Z"), List.of(Emotion.CALM));
        repository.save(elit, Instant.parse("2026-11-14T00:00:00Z"), List.of(Emotion.HAPPY));  // exactly at end

        List<CheckIn> found = repository.findBetween(elit, Instant.parse("2026-11-10T00:00:00Z"),
            Instant.parse("2026-11-14T00:00:00Z"));
        assertEquals(List.of(List.of(Emotion.SAD), List.of(Emotion.CALM)),
            found.stream().map(CheckIn::emotions).toList());
    }

    @Test
    void deleteRemovesTheCheckInAndItsEmotions() {
        CheckIn saved = repository.save(elit, Instant.parse("2026-11-16T08:00:00Z"), List.of(Emotion.SAD, Emotion.TIRED));

        assertTrue(repository.delete(elit, saved.id()));
        assertTrue(repository.findAll(elit).isEmpty());
        assertEquals(0, jdbc.sql("SELECT COUNT(*) FROM check_in_emotion").query(Integer.class).single());
        assertFalse(repository.delete(elit, saved.id()), "deleting twice reports not found");
    }

    @Test
    void findAllIsOldestFirst() {
        repository.save(elit, Instant.parse("2026-11-16T08:00:00Z"), List.of(Emotion.HAPPY));
        repository.save(elit, Instant.parse("2026-11-01T08:00:00Z"), List.of(Emotion.SAD));
        assertEquals(List.of(Emotion.SAD), repository.findAll(elit).getFirst().emotions());
        assertEquals(2, repository.findAll(elit).size());
    }

    @Test
    void usersNeverSeeEachOthersCheckIns() {
        repository.save(elit, Instant.parse("2026-11-16T08:00:00Z"), List.of(Emotion.SAD));
        repository.save(ainek, Instant.parse("2026-11-16T09:00:00Z"), List.of(Emotion.HAPPY));

        assertEquals(List.of(Emotion.SAD), repository.findRecent(elit, 10).getFirst().emotions());
        assertEquals(1, repository.findRecent(elit, 10).size());
        assertEquals(List.of(Emotion.HAPPY), repository.findRecent(ainek, 10).getFirst().emotions());
        assertEquals(1, repository.findAll(ainek).size());
        assertEquals(1, repository.findBetween(elit, Instant.EPOCH, Instant.parse("2027-01-01T00:00:00Z")).size());
    }

    @Test
    void youCannotDeleteSomeoneElsesCheckIn() {
        CheckIn theirs = repository.save(ainek, Instant.parse("2026-11-16T09:00:00Z"), List.of(Emotion.HAPPY));

        assertFalse(repository.delete(elit, theirs.id()), "elit must not be able to delete ainek's check-in");
        assertEquals(1, repository.findAll(ainek).size(), "it is still there");
        assertTrue(repository.delete(ainek, theirs.id()), "the owner can");
    }

    @Test
    void keepsTheNoteAndFlagOnlyWhenGiven() {
        repository.save(elit, Instant.parse("2026-11-16T08:00:00Z"), List.of(Emotion.SAD), "rough day", true);
        repository.save(elit, Instant.parse("2026-11-16T09:00:00Z"), List.of(Emotion.HAPPY));

        List<CheckIn> all = repository.findAll(elit);
        assertEquals("rough day", all.get(0).note());
        assertTrue(all.get(0).flagged());
        assertNull(all.get(1).note());
        assertFalse(all.get(1).flagged());
        assertEquals("rough day", repository.findRecent(elit, 10).get(1).note());
    }

    @Test
    void keepsTheIntensityAndRefusesOneOutsideOneToFive() {
        repository.save(elit, Instant.parse("2026-11-16T08:00:00Z"), List.of(Emotion.SAD), null, false, 4);
        repository.save(elit, Instant.parse("2026-11-16T09:00:00Z"), List.of(Emotion.HAPPY));

        List<CheckIn> all = repository.findAll(elit);
        assertEquals(4, all.get(0).intensity());
        assertNull(all.get(1).intensity());
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
            () -> repository.save(elit, Instant.parse("2026-11-16T10:00:00Z"), List.of(Emotion.SAD), null, false, 9));
    }
}
