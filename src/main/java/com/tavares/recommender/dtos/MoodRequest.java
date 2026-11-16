package com.tavares.recommender.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MoodRequest {

    public static final int MAX_LENGTH = 500;

    @NotBlank(message = "moodText must not be blank")
    @Size(max = MAX_LENGTH, message = "moodText must be at most 500 characters")
    private String moodText;
}
