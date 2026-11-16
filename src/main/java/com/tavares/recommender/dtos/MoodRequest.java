package com.tavares.recommender.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** The body of {@code POST /api/analyze}: the person's own words about how they feel. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MoodRequest {

    /** Longest sentence accepted, in characters; keeps model cost and abuse in check. */
    public static final int MAX_LENGTH = 500;

    /** What the person typed; required and at most {@value #MAX_LENGTH} characters. */
    @NotBlank(message = "moodText must not be blank")
    @Size(max = MAX_LENGTH, message = "moodText must be at most 500 characters")
    private String moodText;
}
