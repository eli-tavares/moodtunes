package com.tavares.recommender.checkin;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CheckInRequest(
    @NotEmpty(message = "emotions must not be empty")
    @Size(max = Emotion.MAX_PER_CHECK_IN, message = "emotions must have at most 3 entries")
    List<String> emotions
) {
}
