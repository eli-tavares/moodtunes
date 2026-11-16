package com.tavares.recommender.report;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * A mood report for one period. All dates are in the viewer's time zone. Mood scores run from -2 (low) to +2 (high);
 * {@code null} means "no data" (never zero, which is a real, neutral score).
 */
public record ReportResponse(
    String range,
    LocalDate from,
    LocalDate to,
    int bucketDays,
    Summary summary,
    List<Bucket> buckets,
    List<EmotionStat> emotions
) {

    /**
     * @param avgMood         average mood score of the period's check-ins
     * @param previousAvgMood the same for the period just before
     * @param moodChange      avgMood - previousAvgMood, when both exist
     * @param avgIntensity    average strength (1-5) over the check-ins that gave one, {@code null} when none did
     */
    public record Summary(int checkIns, int activeDays, Double avgMood, int previousCheckIns,
                          Double previousAvgMood, Double moodChange, Double avgIntensity,
                          Double previousAvgIntensity) {
    }

    /** One day (or one week) of the chart. {@code groups} always lists all four groups; {@code emotions} only seen ones. */
    public record Bucket(LocalDate start, LocalDate end, int checkIns, Double avgMood, Double avgIntensity,
                         Map<String, Integer> groups, Map<String, Integer> emotions) {
    }

    /** How often an emotion appeared in the period, and on how many different days. */
    public record EmotionStat(String emotion, String group, int count, int days) {
    }
}
