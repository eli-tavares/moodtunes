package com.tavares.recommender.checkin;

import com.tavares.recommender.service.BadRequestException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The fixed vocabulary of emotions and body states. Keeping it closed means the AI cannot invent words ("calmness"
 * vs "calm"), so history aggregates cleanly into graphs.
 *
 * <p>Each emotion has a mood score (-2..+2) used for the mood-over-time line. Body states (tired, hungry, ...) have
 * no score: they are tracked, but do not move the mood line.
 */
public enum Emotion {
    // positive
    HAPPY(Group.POSITIVE, 2), EXCITED(Group.POSITIVE, 2), PROUD(Group.POSITIVE, 2), GRATEFUL(Group.POSITIVE, 2),
    LOVED(Group.POSITIVE, 2), INSPIRED(Group.POSITIVE, 2), HOPEFUL(Group.POSITIVE, 1), RELIEVED(Group.POSITIVE, 1),
    CALM(Group.POSITIVE, 1), CONTENT(Group.POSITIVE, 1), RELAXED(Group.POSITIVE, 1), CURIOUS(Group.POSITIVE, 1),
    MOTIVATED(Group.POSITIVE, 1),
    // neutral
    NEUTRAL(Group.NEUTRAL, 0), NOSTALGIC(Group.NEUTRAL, 0), REFLECTIVE(Group.NEUTRAL, 0),
    SURPRISED(Group.NEUTRAL, 0),
    // negative
    ANXIOUS(Group.NEGATIVE, -2), STRESSED(Group.NEGATIVE, -2), OVERWHELMED(Group.NEGATIVE, -2),
    SAD(Group.NEGATIVE, -2), LONELY(Group.NEGATIVE, -2), ANGRY(Group.NEGATIVE, -2),
    FRUSTRATED(Group.NEGATIVE, -1), IRRITABLE(Group.NEGATIVE, -1), DISAPPOINTED(Group.NEGATIVE, -1),
    GUILTY(Group.NEGATIVE, -1), BORED(Group.NEGATIVE, -1),
    // body states (no mood score)
    TIRED(Group.BODY, null), HUNGRY(Group.BODY, null), SLEEPY(Group.BODY, null), RESTLESS(Group.BODY, null),
    SORE(Group.BODY, null), UNWELL(Group.BODY, null);

    public enum Group {
        POSITIVE, NEUTRAL, NEGATIVE, BODY;

        public String code() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final int MAX_PER_CHECK_IN = 3;

    private final Group group;
    private final Integer valence;

    Emotion(Group group, Integer valence) {
        this.group = group;
        this.valence = valence;
    }

    public Group group() {
        return group;
    }

    /** Mood score -2..+2, or {@code null} for body states. */
    public Integer valence() {
        return valence;
    }

    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<Emotion> fromCode(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** All codes, comma separated; used to tell the AI which words it may use. */
    public static String allowedList() {
        return Arrays.stream(values()).map(Emotion::code).collect(Collectors.joining(", "));
    }

    /**
     * Validates client input: 1 to {@value #MAX_PER_CHECK_IN} known emotions. Duplicates are dropped and the result
     * is in a stable (enum) order, so the same selection always gives the same cache key.
     */
    public static List<Emotion> parseAll(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            throw new BadRequestException("Pick at least one emotion.");
        }
        LinkedHashSet<Emotion> unique = new LinkedHashSet<>();
        for (String code : codes) {
            unique.add(fromCode(code).orElseThrow(() -> new BadRequestException("Unknown emotion.")));
        }
        if (unique.size() > MAX_PER_CHECK_IN) {
            throw new BadRequestException("Pick at most " + MAX_PER_CHECK_IN + " emotions.");
        }
        return unique.stream().sorted(Comparator.naturalOrder()).toList();
    }
}
