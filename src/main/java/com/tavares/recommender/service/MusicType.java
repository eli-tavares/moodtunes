package com.tavares.recommender.service;

import java.util.Locale;

public enum MusicType {
    ALBUMS, SONGS;

    public String getString() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    /** Parses a media type case-insensitively; unknown or missing values default to {@link #SONGS}. */
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
