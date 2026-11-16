package com.tavares.recommender.dtos;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** {@code flagged} is true when the AI thought the sentence may be about self-harm; the emotions are still returned. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MoodResponse {
    private List<String> emotions;
    private boolean flagged;
}
