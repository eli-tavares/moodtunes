package com.tavares.recommender.controller;

import com.tavares.recommender.dtos.MoodRequest;
import com.tavares.recommender.dtos.MoodResponse;
import com.tavares.recommender.dtos.MusicRecommendationRequest;
import com.tavares.recommender.dtos.MusicRecommendationResponse;
import com.tavares.recommender.service.MoodAnalysisService;
import com.tavares.recommender.service.MusicService;
import com.tavares.recommender.service.MusicType;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final MoodAnalysisService moodService;
    private final MusicService musicService;

    public ApiController(MoodAnalysisService moodService, MusicService musicService) {
        this.moodService = moodService;
        this.musicService = musicService;
    }

    @PostMapping(value = "/analyze", consumes = "application/json", produces = "application/json")
    public MoodResponse analyze(@Valid @RequestBody MoodRequest request) {
        return new MoodResponse(moodService.extractEmotions(request.getMoodText().trim()));
    }

    @PostMapping(value = "/recommend", consumes = "application/json", produces = "application/json")
    public MusicRecommendationResponse recommend(@Valid @RequestBody MusicRecommendationRequest request) {
        MusicType type = MusicType.getMediaType(request.getMediaType());
        return new MusicRecommendationResponse(musicService.recommend(request.getEmotion(), type));
    }
}
