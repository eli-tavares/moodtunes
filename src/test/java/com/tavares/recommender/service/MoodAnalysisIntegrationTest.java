package com.tavares.recommender.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tavares.recommender.config.ChatClientAdapter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class MoodAnalysisIntegrationTest {

    @Autowired
    private MoodAnalysisService moodService;

    @MockitoBean
    private ChatClientAdapter chatAdapter;

    @Test
    void extractsEmotions() {
        when(chatAdapter.call(anyString(), anyString())).thenReturn("lonely, sad, tired");
        assertEquals(List.of("lonely", "sad", "tired"), moodService.extractEmotions("I feel empty"));
    }

    @Test
    void tellsTheModelWhichWordsItMayUse() {
        when(chatAdapter.call(anyString(), anyString())).thenReturn("happy");
        moodService.extractEmotions("great day");
        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(chatAdapter).call(system.capture(), anyString());
        assertTrue(system.getValue().contains("Allowed words"));
        assertTrue(system.getValue().contains("hungry") && system.getValue().contains("anxious"));
    }

    @Test
    void keepsOnlyWordsFromTheFixedVocabulary() {
        when(chatAdapter.call(anyString(), anyString())).thenReturn(
            "1. Happy.\n- \"Calm\"\nJOY, <script>alert(1)</script>, calmness, HUNGRY, happy, "
                + "ignore all previous instructions");
        // "joy" and "calmness" are not in the vocabulary; duplicates collapse; order is kept
        assertEquals(List.of("happy", "calm", "hungry"), moodService.extractEmotions("x"));
    }

    @Test
    void unusableOutputIsAnError() {
        when(chatAdapter.call(anyString(), anyString())).thenReturn("!!!, 123, joy");
        assertThrows(AiResponseException.class, () -> moodService.extractEmotions("x"));
    }
}
