package com.tavares.recommender.dtos;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** The answer to {@code POST /api/recommend}. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MusicRecommendationResponse {
    /** The suggestions, at most five. */
    private List<MusicRecommendation> items;
}
