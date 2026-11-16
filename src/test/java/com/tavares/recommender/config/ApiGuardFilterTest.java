package com.tavares.recommender.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tavares.recommender.controller.ApiController;
import com.tavares.recommender.service.MoodAnalysis;
import com.tavares.recommender.service.MoodAnalysisService;
import com.tavares.recommender.service.MusicService;
import com.tavares.recommender.user.UserService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(value = ApiController.class,
    properties = {"app.access-token=s3cret", "app.rate-limit.per-client-per-minute=3",
        "app.rate-limit.auth-failures-per-minute=3"})
class ApiGuardFilterTest {

    private static final String BODY = "{\"moodText\":\"happy\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MoodAnalysisService moodAnalysisService;

    @MockitoBean
    private MusicService musicService;

    @MockitoBean
    private UserService users;

    @Test
    void missingOrWrongTokenIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/analyze").contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/analyze").header(ApiGuardFilter.TOKEN_HEADER, "wrong")
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isUnauthorized());
        Mockito.verifyNoInteractions(moodAnalysisService);
    }

    @Test
    void correctTokenIsAccepted() throws Exception {
        Mockito.when(moodAnalysisService.analyze("happy")).thenReturn(new MoodAnalysis(List.of("joy"), false));
        mockMvc.perform(post("/api/analyze").header(ApiGuardFilter.TOKEN_HEADER, "s3cret")
                .with(req -> {
                    req.setRemoteAddr("10.0.0.50");
                    return req;
                })
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isOk());
    }

    @Test
    void clientIsRateLimited() throws Exception {
        Mockito.when(moodAnalysisService.analyze("happy")).thenReturn(new MoodAnalysis(List.of("joy"), false));
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/analyze").header(ApiGuardFilter.TOKEN_HEADER, "s3cret")
                    .with(req -> {
                        req.setRemoteAddr("10.0.0.99");
                        return req;
                    })
                    .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/analyze").header(ApiGuardFilter.TOKEN_HEADER, "s3cret")
                .with(req -> {
                    req.setRemoteAddr("10.0.0.99");
                    return req;
                })
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().string("Retry-After", "60"));
    }

    @Test
    void oversizedBodyIsRejectedBeforeReachingController() throws Exception {
        String huge = "{\"moodText\":\"" + "x".repeat(10_000) + "\"}";
        mockMvc.perform(post("/api/analyze").header(ApiGuardFilter.TOKEN_HEADER, "s3cret")
                .with(req -> {
                    req.setRemoteAddr("10.0.0.77");
                    return req;
                })
                .contentType(MediaType.APPLICATION_JSON).content(huge))
            .andExpect(status().isPayloadTooLarge());
        Mockito.verifyNoInteractions(moodAnalysisService);
    }

    @Test
    void authCheckNeedsTheRightTokenAndCallsNoAi() throws Exception {
        mockMvc.perform(get("/api/auth/check").with(from("10.0.1.1")))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/check").header(ApiGuardFilter.TOKEN_HEADER, "wrong").with(from("10.0.1.2")))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/check").header(ApiGuardFilter.TOKEN_HEADER, "s3cret").with(from("10.0.1.3")))
            .andExpect(status().isNoContent());
        Mockito.verifyNoInteractions(moodAnalysisService, musicService);
    }

    @Test
    void guessingTheAccessCodeIsThrottled() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/auth/check").header(ApiGuardFilter.TOKEN_HEADER, "guess" + i)
                    .with(from("10.0.2.1")))
                .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(get("/api/auth/check").header(ApiGuardFilter.TOKEN_HEADER, "guess4").with(from("10.0.2.1")))
            .andExpect(status().isTooManyRequests());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor from(String address) {
        return req -> {
            req.setRemoteAddr(address);
            return req;
        };
    }

    @Test
    void cheapDataEndpointsAreNotLimitedByTheTightAiBudget() throws Exception {
        for (int i = 0; i < 10; i++) {   // the AI budget in this test is 3 per minute
            mockMvc.perform(get("/api/auth/check").header(ApiGuardFilter.TOKEN_HEADER, "s3cret")
                    .with(from("10.0.3.1")))
                .andExpect(status().isNoContent());
        }
    }
}
