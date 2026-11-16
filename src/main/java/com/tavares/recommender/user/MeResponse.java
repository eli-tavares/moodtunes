package com.tavares.recommender.user;

/**
 * The signed-in person and their settings.
 *
 * @param name         the person's user name
 * @param musicEnabled whether music suggestions are turned on for them
 */
public record MeResponse(String name, boolean musicEnabled) {
}
