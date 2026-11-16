package com.tavares.recommender.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tavares.recommender.config.ChatClientAdapter;
import com.tavares.recommender.dtos.MusicRecommendation;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class MusicServiceIntegrationTest {

    private static final String JSON = """
        [{"title":"Lonely Song","artist":"Artist","description":"Sad vibes"}]""";

    @Autowired
    private MusicService musicService;

    @Autowired
    private CacheManager cacheManager;

    @MockitoBean
    private ChatClientAdapter chatAdapter;

    @BeforeEach
    void setUp() {
        Cache cache = cacheManager.getCache("musicRecommendations");
        if (cache != null) cache.clear();
    }

    @Test
    void parsedResultIsCachedCaseInsensitively() {
        when(chatAdapter.call(anyString(), anyString())).thenReturn(JSON);

        List<MusicRecommendation> first = musicService.recommend("loneliness", MusicType.SONGS);
        List<MusicRecommendation> second = musicService.recommend("  Loneliness ", MusicType.SONGS);

        assertEquals("Lonely Song", first.getFirst().getTitle());
        assertEquals(first, second);
        verify(chatAdapter, times(1)).call(anyString(), anyString());
    }

    @Test
    void differentMediaTypesAreCachedSeparately() {
        when(chatAdapter.call(anyString(), anyString())).thenReturn(JSON);
        musicService.recommend("joy", MusicType.SONGS);
        musicService.recommend("joy", MusicType.ALBUMS);
        verify(chatAdapter, times(2)).call(anyString(), anyString());
    }

    @Test
    void unreadableAnswerIsNotCachedSoNextCallRetries() {
        when(chatAdapter.call(anyString(), anyString())).thenReturn("Sorry, I can't help with that.", JSON);

        assertThrows(AiResponseException.class, () -> musicService.recommend("joy", MusicType.SONGS));
        assertEquals(1, musicService.recommend("joy", MusicType.SONGS).size());
        verify(chatAdapter, times(2)).call(anyString(), anyString());
    }

    @Test
    void parseToleratesMarkdownFencesAndSurroundingText() {
        String wrapped = "Here you go:\n```json\n" + JSON + "\n```\nEnjoy!";
        assertEquals("Artist", musicService.parse(wrapped).getFirst().getArtist());
    }

    @Test
    void parseRejectsEmptyAndMalformedArrays() {
        assertThrows(AiResponseException.class, () -> musicService.parse("[]"));
        assertThrows(AiResponseException.class, () -> musicService.parse("[{\"artist\":\"x\"}]"));
        assertThrows(AiResponseException.class, () -> musicService.parse("[{broken"));
    }
}
