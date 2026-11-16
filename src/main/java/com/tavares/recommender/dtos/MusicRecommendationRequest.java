package com.tavares.recommender.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MusicRecommendationRequest {

    /** An emotion is a short word or phrase: letters, spaces, apostrophes and hyphens only. */
    public static final String EMOTION_REGEX = "^\\p{L}[\\p{L} '\\-]{0,39}$";

    @NotBlank(message = "emotion must not be blank")
    @Pattern(regexp = EMOTION_REGEX, message = "emotion must be 1-40 letters (spaces, ' and - allowed)")
    private String emotion;

    @NotBlank(message = "mediaType must not be blank")
    @Pattern(regexp = "(?i)^(songs|albums)$", message = "mediaType must be SONGS or ALBUMS")
    private String mediaType;
}
