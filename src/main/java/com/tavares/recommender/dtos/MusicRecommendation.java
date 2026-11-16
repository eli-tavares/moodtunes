package com.tavares.recommender.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One suggested song or album, as the model describes it. Treated as untrusted text and only ever shown as plain text. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MusicRecommendation {
    /** The song or album title. */
    private String title;
    /** The main artist. */
    private String artist;
    /** One short sentence on why it fits. */
    private String description;
}
