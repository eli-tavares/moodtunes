package com.tavares.recommender.checkin;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** The page explains every emotion when tapped; adding an emotion without a description must fail the build. */
class EmotionHelpTest {

    @Test
    void everyEmotionHasADescriptionInThePage() throws Exception {
        String js = new String(new ClassPathResource("static/app.js").getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
        String table = js.substring(js.indexOf("const EMOTION_HELP = {"), js.indexOf("};", js.indexOf("const EMOTION_HELP")));
        for (Emotion e : Emotion.values()) {
            assertTrue(table.contains("    " + e.code() + ": '"), "missing description for " + e.code());
        }
    }
}
