package com.tavares.recommender.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tavares.recommender.controller.ApiController;
import com.tavares.recommender.service.MoodAnalysisService;
import com.tavares.recommender.service.MusicService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(value = ApiController.class,
    properties = {"app.access-token=s3cret", "app.rate-limit.per-client-per-minute=3"})
class ApiGuardFilterTest {

    private static final String BODY = "{\"moodText\":\"happy\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MoodAnalysisService moodAnalysisService;

    @MockitoBean
    private MusicService musicService;

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
        Mockito.when(moodAnalysisService.extractEmotions("happy")).thenReturn(List.of("joy"));
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
        Mockito.when(moodAnalysisService.extractEmotions("happy")).thenReturn(List.of("joy"));
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
}
