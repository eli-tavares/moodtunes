package com.tavares.recommender.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tavares.recommender.checkin.CheckIn;
import com.tavares.recommender.checkin.CheckInService;
import com.tavares.recommender.checkin.Emotion;
import com.tavares.recommender.report.ReportRange;
import com.tavares.recommender.report.ReportResponse;
import com.tavares.recommender.report.ReportService;
import com.tavares.recommender.service.BadRequestException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CheckInController.class)
class CheckInControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CheckInService checkIns;

    @MockitoBean
    private ReportService reports;

    private static CheckIn sample() {
        return new CheckIn(7, Instant.parse("2026-11-16T10:00:00Z"), List.of(Emotion.ANXIOUS, Emotion.TIRED));
    }

    @Test
    void saveReturnsCreated() throws Exception {
        Mockito.when(checkIns.save(List.of("anxious", "tired"))).thenReturn(sample());

        mockMvc.perform(post("/api/checkins").contentType(MediaType.APPLICATION_JSON)
                .content("{\"emotions\":[\"anxious\",\"tired\"]}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(7))
            .andExpect(jsonPath("$.createdAt").value("2026-11-16T10:00:00Z"))
            .andExpect(jsonPath("$.emotions[0]").value("anxious"))
            .andExpect(jsonPath("$.emotions[1]").value("tired"));
    }

    @Test
    void saveRejectsEmptyOrTooManyEmotionsBeforeTouchingTheService() throws Exception {
        for (String body : List.of("{}", "{\"emotions\":[]}", "{\"emotions\":[\"a\",\"b\",\"c\",\"d\"]}")) {
            mockMvc.perform(post("/api/checkins").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        }
        Mockito.verifyNoInteractions(checkIns);
    }

    @Test
    void unknownEmotionIsABadRequestWithOurOwnMessage() throws Exception {
        Mockito.when(checkIns.save(List.of("calmness"))).thenThrow(new BadRequestException("Unknown emotion."));

        mockMvc.perform(post("/api/checkins").contentType(MediaType.APPLICATION_JSON)
                .content("{\"emotions\":[\"calmness\"]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Unknown emotion."));
    }

    @Test
    void recentListsCheckIns() throws Exception {
        Mockito.when(checkIns.recent(5)).thenReturn(List.of(sample()));

        mockMvc.perform(get("/api/checkins").param("limit", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(7))
            .andExpect(jsonPath("$[0].emotions[1]").value("tired"));
    }

    @Test
    void deleteReturnsNoContentOrNotFound() throws Exception {
        Mockito.when(checkIns.delete(7)).thenReturn(true);
        Mockito.when(checkIns.delete(8)).thenReturn(false);

        mockMvc.perform(delete("/api/checkins/7")).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/checkins/8")).andExpect(status().isNotFound());
    }

    @Test
    void reportPassesRangeAndTimeZoneAndSerialisesDatesAsText() throws Exception {
        var summary = new ReportResponse.Summary(0, 0, null, 0, null, null);
        Mockito.when(reports.report(ReportRange.MONTH, ZoneId.of("Europe/Dublin"))).thenReturn(
            new ReportResponse("month", LocalDate.parse("2026-10-18"), LocalDate.parse("2026-11-16"), 1, summary,
                List.of(new ReportResponse.Bucket(LocalDate.parse("2026-10-18"), LocalDate.parse("2026-10-18"), 0,
                    null, Map.of("positive", 0), Map.of())),
                List.of()));

        mockMvc.perform(get("/api/reports").param("range", "month").param("tz", "Europe/Dublin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.range").value("month"))
            .andExpect(jsonPath("$.from").value("2026-10-18"))
            .andExpect(jsonPath("$.buckets[0].start").value("2026-10-18"))
            .andExpect(jsonPath("$.summary.avgMood").value(nullValue()))
            .andExpect(jsonPath("$.buckets[0].avgMood").value(nullValue()));
    }

    @Test
    void reportRejectsBadRangeOrTimeZone() throws Exception {
        mockMvc.perform(get("/api/reports").param("range", "year")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/reports").param("range", "week").param("tz", "Mars/Olympus"))
            .andExpect(status().isBadRequest());
        Mockito.verifyNoInteractions(reports);
    }

    @Test
    void exportIsADownloadOfEverything() throws Exception {
        Mockito.when(checkIns.all()).thenReturn(List.of(sample()));

        mockMvc.perform(get("/api/export"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", containsString("attachment")))
            .andExpect(header().string("Content-Disposition", containsString("moodtunes-export-")))
            .andExpect(jsonPath("$.checkIns[0].emotions[0]").value("anxious"))
            .andExpect(content().string(not(containsString("moodText"))));
    }
}
