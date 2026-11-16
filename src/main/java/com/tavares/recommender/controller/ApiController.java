package com.tavares.recommender.controller;

import com.tavares.recommender.checkin.Emotion;
import com.tavares.recommender.config.ApiGuardFilter;
import com.tavares.recommender.dtos.MoodRequest;
import com.tavares.recommender.dtos.MoodResponse;
import com.tavares.recommender.dtos.MusicRecommendationRequest;
import com.tavares.recommender.dtos.MusicRecommendationResponse;
import com.tavares.recommender.service.MoodAnalysis;
import com.tavares.recommender.service.MoodAnalysisService;
import com.tavares.recommender.service.MusicService;
import com.tavares.recommender.service.ForbiddenException;
import com.tavares.recommender.service.MusicType;
import com.tavares.recommender.user.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The AI-backed endpoints: turning a sentence into emotions and emotions into music. Both are rate limited by
 * {@link com.tavares.recommender.config.ApiGuardFilter} before they run, because each costs a model call.
 */
@RestController
@RequestMapping("/api")
public class ApiController {

    private final MoodAnalysisService moodService;
    private final MusicService musicService;
    private final UserService users;

    /**
     * Creates the controller.
     *
     * @param moodService  turns a sentence into emotions
     * @param musicService turns emotions into songs or albums
     * @param users        looks up the signed-in person's settings
     */
    public ApiController(MoodAnalysisService moodService, MusicService musicService, UserService users) {
        this.moodService = moodService;
        this.musicService = musicService;
        this.users = users;
    }

    /**
     * Lets the UI verify the access code before showing the app. {@link com.tavares.recommender.config.ApiGuardFilter}
     * has already rejected the request (401) if a code is required and wrong, so reaching this method means access is
     * granted. No AI call is made.
     *
     * @return {@code 204 No Content}
     */
    @GetMapping("/auth/check")
    public ResponseEntity<Void> checkAccess() {
        return ResponseEntity.noContent().build();
    }

    /**
     * Suggests emotions for a sentence.
     *
     * @param request the person's sentence (up to 500 characters)
     * @return 4 to 6 emotion codes, best fit first, and whether the sentence was flagged as worrying
     */
    @PostMapping(value = "/analyze", consumes = "application/json", produces = "application/json")
    public MoodResponse analyze(@Valid @RequestBody MoodRequest request) {
        MoodAnalysis analysis = moodService.analyze(request.getMoodText().trim());
        return new MoodResponse(analysis.emotions(), analysis.flagged());
    }

    /**
     * Suggests songs or albums for a selection of emotions.
     *
     * @param user    the signed-in person (set by the access filter, never by the client)
     * @param request 1 to 3 emotions and {@code SONGS} or {@code ALBUMS}
     * @return the suggestions
     * @throws ForbiddenException if the person has turned music off; no model call is made then
     */
    @PostMapping(value = "/recommend", consumes = "application/json", produces = "application/json")
    public MusicRecommendationResponse recommend(@RequestAttribute(ApiGuardFilter.USER_ATTRIBUTE) String user,
                                                 @Valid @RequestBody MusicRecommendationRequest request) {
        // Music is a per-person switch. Enforced here, not just hidden in the page, so no AI call is made when off.
        if (!users.musicEnabled(users.idOf(user))) {
            throw new ForbiddenException("Music suggestions are turned off for you. Turn them on with the Music button.");
        }
        MusicType type = MusicType.getMediaType(request.getMediaType());
        return new MusicRecommendationResponse(musicService.recommend(Emotion.parseAll(request.getEmotions()), type));
    }
}
