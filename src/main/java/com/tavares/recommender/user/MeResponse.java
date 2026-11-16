package com.tavares.recommender.user;

/** The signed-in person and their settings. */
public record MeResponse(String name, boolean musicEnabled) {
}
