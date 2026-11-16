package com.tavares.recommender.service;

import java.util.Locale;

/** What kind of music to suggest. */
public enum MusicType {
    ALBUMS, SONGS;

    /**
     * The lower-case word used in the prompt.
     *
     * @return {@code albums} or {@code songs}
     */
    public String getString() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    /**
     * Parses a media type case-insensitively; unknown or missing values default to {@link #SONGS}.
     *
     * @param value the text sent by the client, or {@code null}
     * @return the matching type, or {@link #SONGS}
     */
    public static MusicType getMediaType(String value) {
        if (value == null) {
            return SONGS;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return SONGS;
        }
    }
}
