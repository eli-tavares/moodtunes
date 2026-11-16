package com.tavares.recommender.user;

import jakarta.validation.constraints.NotNull;

public record MusicSettingRequest(@NotNull(message = "enabled must be true or false") Boolean enabled) {
}
