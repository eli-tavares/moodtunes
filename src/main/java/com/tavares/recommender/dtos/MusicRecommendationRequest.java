package com.tavares.recommender.dtos;

import com.tavares.recommender.checkin.Emotion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MusicRecommendationRequest {

    /** One to three emotions from the fixed vocabulary (checked again by {@code Emotion.parseAll}). */
    @NotEmpty(message = "emotions must not be empty")
    @Size(max = Emotion.MAX_PER_CHECK_IN, message = "emotions must have at most 3 entries")
    private List<@NotBlank String> emotions;

    @NotBlank(message = "mediaType must not be blank")
    @Pattern(regexp = "(?i)^(songs|albums)$", message = "mediaType must be SONGS or ALBUMS")
    private String mediaType;
}
