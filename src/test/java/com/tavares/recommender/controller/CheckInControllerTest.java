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
import com.tavares.recommender.checkin.CheckInResponse;
import com.tavares.recommender.checkin.CheckInService;
import com.tavares.recommender.checkin.Emotion;
import com.tavares.recommender.report.ReportRange;
import com.tavares.recommender.report.ReportResponse;
import com.tavares.recommender.report.ReportService;
import com.tavares.recommender.report.SessionSummary;
import com.tavares.recommender.service.BadRequestException;
import com.tavares.recommender.user.UserService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
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

    @MockitoBean
    private UserService users;

    @BeforeEach
    void signedInUser() {
        Mockito.when(users.idOf("local")).thenReturn(5L);   // no codes configured in this test: the local user
    }

    private static CheckIn sample() {
        return new CheckIn(7, Instant.parse("2026-11-16T10:00:00Z"), List.of(Emotion.ANXIOUS, Emotion.TIRED));
    }

    @Test
    void saveReturnsCreated() throws Exception {
        Mockito.when(checkIns.save(5L, List.of("anxious", "tired"), null, false, null)).thenReturn(sample());

        mockMvc.perform(post("/api/checkins").contentType(MediaType.APPLICATION_JSON)
                .content("{\"emotions\":[\"anxious\",\"tired\"]}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(7))
            .andExpect(jsonPath("$.createdAt").value("2026-11-16T10:00:00Z"))
            .andExpect(jsonPath("$.emotions[0]").value("anxious"))
            .andExpect(jsonPath("$.emotions[1]").value("tired"));
    }

    @Test
    void saveCanKeepThePersonsOwnWords() throws Exception {
        Mockito.when(checkIns.save(5L, List.of("sad"), "rough day", true, null))
            .thenReturn(new CheckIn(8, Instant.parse("2026-11-16T10:00:00Z"), List.of(Emotion.SAD), "rough day", true, null));

        mockMvc.perform(post("/api/checkins").contentType(MediaType.APPLICATION_JSON)
                .content("{\"emotions\":[\"sad\"],\"note\":\"rough day\",\"flagged\":true}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.note").value("rough day"))
            .andExpect(jsonPath("$.flagged").value(true));
    }

    @Test
    void saveCanCarryAnIntensityAndRejectsOneOutsideOneToFive() throws Exception {
        Mockito.when(checkIns.save(5L, List.of("sad"), null, false, 4))
            .thenReturn(new CheckIn(9, Instant.parse("2026-11-16T10:00:00Z"), List.of(Emotion.SAD), null, false, 4));

        mockMvc.perform(post("/api/checkins").contentType(MediaType.APPLICATION_JSON)
                .content("{\"emotions\":[\"sad\"],\"intensity\":4}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.intensity").value(4));

        for (int bad : new int[] {0, 6, -1}) {
            mockMvc.perform(post("/api/checkins").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"emotions\":[\"sad\"],\"intensity\":" + bad + "}"))
                .andExpect(status().isBadRequest());
        }
    }

    @Test
    void summaryReturnsTheWeekAndItsCheckIns() throws Exception {
        var summary = new ReportResponse.Summary(1, 1, -2.0, 0, null, null, 4.0, null);
        var report = new ReportResponse("week", LocalDate.parse("2026-11-10"), LocalDate.parse("2026-11-16"), 1, summary,
            List.of(), List.of());
        var item = CheckInResponse.from(new CheckIn(9, Instant.parse("2026-11-16T10:00:00Z"), List.of(Emotion.SAD),
            "rough day", true, 4));
        Mockito.when(reports.sessionSummary(5L, ZoneId.of("Europe/Dublin"))).thenReturn(new SessionSummary(report, List.of(item)));

        mockMvc.perform(get("/api/summary").param("tz", "Europe/Dublin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.report.range").value("week"))
            .andExpect(jsonPath("$.report.summary.avgIntensity").value(4.0))
            .andExpect(jsonPath("$.checkIns[0].note").value("rough day"))
            .andExpect(jsonPath("$.checkIns[0].flagged").value(true))
            .andExpect(jsonPath("$.checkIns[0].intensity").value(4));
    }

    @Test
    void anOrdinaryCheckInShowsNoNoteAndNoFlag() throws Exception {
        Mockito.when(checkIns.save(5L, List.of("anxious", "tired"), null, false, null)).thenReturn(sample());

        mockMvc.perform(post("/api/checkins").contentType(MediaType.APPLICATION_JSON)
                .content("{\"emotions\":[\"anxious\",\"tired\"]}"))
            .andExpect(jsonPath("$.note").doesNotExist())
            .andExpect(jsonPath("$.flagged").doesNotExist());
    }

    @Test
    void aNoteOverTheLimitIsRejected() throws Exception {
        mockMvc.perform(post("/api/checkins").contentType(MediaType.APPLICATION_JSON)
                .content("{\"emotions\":[\"sad\"],\"note\":\"" + "x".repeat(501) + "\"}"))
            .andExpect(status().isBadRequest());
        Mockito.verifyNoInteractions(checkIns);
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
        Mockito.when(checkIns.save(5L, List.of("calmness"), null, false, null)).thenThrow(new BadRequestException("Unknown emotion."));

        mockMvc.perform(post("/api/checkins").contentType(MediaType.APPLICATION_JSON)
                .content("{\"emotions\":[\"calmness\"]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Unknown emotion."));
    }

    @Test
    void recentListsCheckIns() throws Exception {
        Mockito.when(checkIns.recent(5L, 5)).thenReturn(List.of(sample()));

        mockMvc.perform(get("/api/checkins").param("limit", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(7))
            .andExpect(jsonPath("$[0].emotions[1]").value("tired"));
    }

    @Test
    void deleteReturnsNoContentOrNotFound() throws Exception {
        Mockito.when(checkIns.delete(5L, 7)).thenReturn(true);
        Mockito.when(checkIns.delete(5L, 8)).thenReturn(false);

        mockMvc.perform(delete("/api/checkins/7")).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/checkins/8")).andExpect(status().isNotFound());
    }

    @Test
    void reportPassesRangeAndTimeZoneAndSerialisesDatesAsText() throws Exception {
        var summary = new ReportResponse.Summary(0, 0, null, 0, null, null, null, null);
        Mockito.when(reports.report(5L, ReportRange.MONTH, ZoneId.of("Europe/Dublin"))).thenReturn(
            new ReportResponse("month", LocalDate.parse("2026-10-18"), LocalDate.parse("2026-11-16"), 1, summary,
                List.of(new ReportResponse.Bucket(LocalDate.parse("2026-10-18"), LocalDate.parse("2026-10-18"), 0,
                    null, null, Map.of("positive", 0), Map.of())),
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
        Mockito.when(checkIns.all(5L)).thenReturn(List.of(sample()));

        mockMvc.perform(get("/api/export"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", containsString("attachment")))
            .andExpect(header().string("Content-Disposition", containsString("moodtunes-export-")))
            .andExpect(jsonPath("$.checkIns[0].emotions[0]").value("anxious"))
            .andExpect(content().string(not(containsString("moodText"))));
    }
}
