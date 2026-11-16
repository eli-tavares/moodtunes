package com.tavares.recommender.user;

import jakarta.validation.constraints.NotNull;

/**
 * The body of {@code PUT /api/me/music}.
 *
 * @param enabled {@code true} to turn music suggestions on for the signed-in person, {@code false} to turn them off
 */
public record MusicSettingRequest(@NotNull(message = "enabled must be true or false") Boolean enabled) {
}
