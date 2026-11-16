package com.tavares.recommender.checkin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tavares.recommender.service.BadRequestException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class EmotionTest {

    @Test
    void everyEmotionHasAGroupAndOnlyBodyStatesHaveNoMoodScore() {
        for (Emotion e : Emotion.values()) {
            if (e.group() == Emotion.Group.BODY) {
                assertNull(e.valence(), e + " is a body state");
            } else {
                assertTrue(e.valence() >= -2 && e.valence() <= 2, e + " score in range");
            }
        }
    }

    @Test
    void scoresMatchTheirGroup() {
        Arrays.stream(Emotion.values()).filter(e -> e.group() == Emotion.Group.POSITIVE)
            .forEach(e -> assertTrue(e.valence() > 0, e.toString()));
        Arrays.stream(Emotion.values()).filter(e -> e.group() == Emotion.Group.NEGATIVE)
            .forEach(e -> assertTrue(e.valence() < 0, e.toString()));
        Arrays.stream(Emotion.values()).filter(e -> e.group() == Emotion.Group.NEUTRAL)
            .forEach(e -> assertEquals(0, e.valence(), e.toString()));
    }

    @Test
    void parsesCodesCaseInsensitively() {
        assertEquals(Emotion.ANXIOUS, Emotion.fromCode(" Anxious ").orElseThrow());
        assertTrue(Emotion.fromCode("calmness").isEmpty());
        assertTrue(Emotion.fromCode(null).isEmpty());
    }

    @Test
    void parseAllDropsDuplicatesAndSortsIntoAStableOrder() {
        assertEquals(List.of(Emotion.HAPPY, Emotion.TIRED),
            Emotion.parseAll(List.of("Tired", "happy", "TIRED")));
        assertEquals(Emotion.parseAll(List.of("sad", "happy")), Emotion.parseAll(List.of("happy", "sad")));
    }

    @Test
    void parseAllRejectsBadInput() {
        assertThrows(BadRequestException.class, () -> Emotion.parseAll(null));
        assertThrows(BadRequestException.class, () -> Emotion.parseAll(List.of()));
        assertThrows(BadRequestException.class, () -> Emotion.parseAll(List.of("happy", "bogus")));
        assertThrows(BadRequestException.class, () -> Emotion.parseAll(List.of("happy", "sad", "calm", "tired")));
    }

    @Test
    void allowedListNamesEveryEmotion() {
        String list = Emotion.allowedList();
        for (Emotion e : Emotion.values()) {
            assertTrue(list.contains(e.code()), e.code());
        }
    }

    @Test
    void checkInMoodIgnoresBodyStates() {
        Instant now = Instant.now();
        assertEquals(-2.0, new CheckIn(1, now, List.of(Emotion.ANXIOUS, Emotion.TIRED)).mood());
        assertEquals(1.5, new CheckIn(2, now, List.of(Emotion.HAPPY, Emotion.CALM)).mood());
        assertNull(new CheckIn(3, now, List.of(Emotion.HUNGRY)).mood());
    }

    @Test
    void toneTellsGoodFromLowFromMixed() {
        assertEquals("good", Emotion.tone(List.of(Emotion.HAPPY, Emotion.CALM)));
        assertEquals("low", Emotion.tone(List.of(Emotion.SAD, Emotion.TIRED)));
        assertEquals("mixed", Emotion.tone(List.of(Emotion.EXCITED, Emotion.ANXIOUS)));
        assertEquals("good", Emotion.tone(List.of(Emotion.NEUTRAL)));
        assertEquals("good", Emotion.tone(List.of(Emotion.HUNGRY)));
    }
}
