package com.tavares.recommender.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tavares.recommender.checkin.CheckIn;
import com.tavares.recommender.checkin.CheckInRepository;
import com.tavares.recommender.checkin.Emotion;
import com.tavares.recommender.report.ReportResponse.Bucket;
import com.tavares.recommender.service.BadRequestException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReportServiceTest {

    // "Now" is Mon 2026-11-16 12:00 UTC.
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-16T12:00:00Z"), ZoneOffset.UTC);

    private final CheckInRepository repository = mock(CheckInRepository.class);
    private final ReportService service = new ReportService(repository, CLOCK);

    private static CheckIn at(String iso, Emotion... emotions) {
        return new CheckIn(iso.hashCode(), Instant.parse(iso), List.of(emotions));
    }

    private void data(CheckIn... checkIns) {
        when(repository.findBetween(any(), any())).thenReturn(List.of(checkIns));
    }

    @Test
    void weekReportSummarisesAndBucketsPerDay() {
        data(
            at("2026-11-03T10:00:00Z", Emotion.SAD),                       // previous period: -2
            at("2026-11-05T10:00:00Z", Emotion.HAPPY),                     // previous period: +2
            at("2026-11-10T09:00:00Z", Emotion.ANXIOUS, Emotion.TIRED),    // -2 (tired has no score)
            at("2026-11-10T20:00:00Z", Emotion.CALM),                      // +1
            at("2026-11-12T08:00:00Z", Emotion.HUNGRY),                    // no mood score
            at("2026-11-16T07:00:00Z", Emotion.HAPPY, Emotion.CALM));      // +1.5

        ReportResponse report = service.report(ReportRange.WEEK, ZoneOffset.UTC);

        assertEquals("week", report.range());
        assertEquals(LocalDate.parse("2026-11-10"), report.from());
        assertEquals(LocalDate.parse("2026-11-16"), report.to());
        assertEquals(1, report.bucketDays());

        var summary = report.summary();
        assertEquals(4, summary.checkIns());
        assertEquals(3, summary.activeDays());                 // 10th, 12th, 16th
        assertEquals(0.17, summary.avgMood());                 // (-2 + 1 + 1.5) / 3
        assertEquals(2, summary.previousCheckIns());
        assertEquals(0.0, summary.previousAvgMood());          // (-2 + 2) / 2
        assertEquals(0.17, summary.moodChange());

        assertEquals(7, report.buckets().size());
        Bucket first = report.buckets().getFirst();            // Nov 10
        assertEquals(LocalDate.parse("2026-11-10"), first.start());
        assertEquals(2, first.checkIns());
        assertEquals(-0.5, first.avgMood());                   // (-2 + 1) / 2
        assertEquals(Map.of("positive", 1, "neutral", 0, "negative", 1, "body", 1), first.groups());
        assertEquals(Map.of("anxious", 1, "tired", 1, "calm", 1), first.emotions());

        Bucket bodyOnly = report.buckets().get(2);             // Nov 12: only "hungry"
        assertEquals(1, bodyOnly.checkIns());
        assertNull(bodyOnly.avgMood(), "no score is null, not zero");
        assertEquals(1, bodyOnly.groups().get("body"));

        Bucket empty = report.buckets().get(1);                // Nov 11: nothing
        assertEquals(0, empty.checkIns());
        assertNull(empty.avgMood());

        assertEquals(1.5, report.buckets().get(6).avgMood());  // Nov 16
    }

    @Test
    void emotionStatsAreRankedWithDistinctDays() {
        data(
            at("2026-11-10T09:00:00Z", Emotion.ANXIOUS, Emotion.TIRED),
            at("2026-11-10T20:00:00Z", Emotion.CALM),
            at("2026-11-12T08:00:00Z", Emotion.HUNGRY),
            at("2026-11-16T07:00:00Z", Emotion.HAPPY, Emotion.CALM));

        var stats = service.report(ReportRange.WEEK, ZoneOffset.UTC).emotions();

        assertEquals("calm", stats.getFirst().emotion());
        assertEquals(2, stats.getFirst().count());
        assertEquals(2, stats.getFirst().days());              // two different days
        assertEquals("positive", stats.getFirst().group());
        assertEquals(List.of("calm", "anxious", "happy", "hungry", "tired"),
            stats.stream().map(ReportResponse.EmotionStat::emotion).toList());   // ties alphabetical
    }

    @Test
    void monthIsThirtyDailyBuckets() {
        data(at("2026-10-18T10:00:00Z", Emotion.HAPPY),            // first day of the window
            at("2026-10-17T10:00:00Z", Emotion.SAD));              // one day earlier: previous period
        ReportResponse report = service.report(ReportRange.MONTH, ZoneOffset.UTC);

        assertEquals(LocalDate.parse("2026-10-18"), report.from());
        assertEquals(30, report.buckets().size());
        assertEquals(1, report.buckets().getFirst().checkIns());
        assertEquals(1, report.summary().checkIns());
        assertEquals(1, report.summary().previousCheckIns());
        assertEquals(4.0, report.summary().moodChange());      // +2 vs -2
    }

    @Test
    void threeMonthsIsThirteenWeeklyBuckets() {
        data(at("2026-08-18T10:00:00Z", Emotion.HAPPY),            // first day
            at("2026-11-10T10:00:00Z", Emotion.SAD),               // start of the last week
            at("2026-11-16T10:00:00Z", Emotion.CALM));             // last day

        ReportResponse report = service.report(ReportRange.THREE_MONTHS, ZoneOffset.UTC);

        assertEquals(LocalDate.parse("2026-08-18"), report.from());
        assertEquals(7, report.bucketDays());
        assertEquals(13, report.buckets().size());
        Bucket last = report.buckets().getLast();
        assertEquals(LocalDate.parse("2026-11-10"), last.start());
        assertEquals(LocalDate.parse("2026-11-16"), last.end());
        assertEquals(2, last.checkIns());
        assertEquals(-0.5, last.avgMood());
        assertEquals(1, report.buckets().getFirst().checkIns());
    }

    @Test
    void daysFollowTheViewersTimeZone() {
        // 12:30 UTC on the 16th is already 01:30 on the 17th in Auckland (UTC+13 in November).
        ZoneId auckland = ZoneId.of("Pacific/Auckland");
        data(at("2026-11-16T12:30:00Z", Emotion.HAPPY));

        ReportResponse report = service.report(ReportRange.WEEK, auckland);

        assertEquals(LocalDate.parse("2026-11-17"), report.to());
        assertEquals(LocalDate.parse("2026-11-11"), report.from());
        assertEquals(1, report.buckets().getLast().checkIns());
        assertEquals(LocalDate.parse("2026-11-17"), report.buckets().getLast().start());
    }

    @Test
    void emptyPeriodHasNoScoresAndNoStats() {
        data();
        ReportResponse report = service.report(ReportRange.WEEK, ZoneOffset.UTC);

        assertEquals(0, report.summary().checkIns());
        assertNull(report.summary().avgMood());
        assertNull(report.summary().moodChange());
        assertTrue(report.emotions().isEmpty());
        assertTrue(report.buckets().stream().allMatch(b -> b.checkIns() == 0 && b.avgMood() == null));
    }

    @Test
    void rangeParsing() {
        assertEquals(ReportRange.THREE_MONTHS, ReportRange.parse("3months"));
        assertEquals(ReportRange.WEEK, ReportRange.parse("WEEK"));
        assertThrows(BadRequestException.class, () -> ReportRange.parse("year"));
    }
}
