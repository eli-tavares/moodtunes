package com.tavares.recommender.dtos;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** The answer to {@code POST /api/analyze}. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MoodResponse {
    /** Emotion codes from the fixed vocabulary, best fit first. May be empty only when {@code flagged} is true. */
    private List<String> emotions;
    /** {@code true} when the AI thought the sentence may be about self-harm; the emotions are still returned. */
    private boolean flagged;
}
