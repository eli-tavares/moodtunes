package com.tavares.recommender.report;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * A mood report for one period. All dates are in the viewer's time zone. Mood scores run from -2 (low) to +2 (high);
 * {@code null} means "no data" (never zero, which is a real, neutral score).
 *
 * @param range      the period asked for: {@code week}, {@code month} or {@code 3months}
 * @param from       first day of the period
 * @param to         last day of the period (today)
 * @param bucketDays days covered by each entry in {@code buckets}
 * @param summary    totals for the period compared with the period before
 * @param buckets    one entry per day (or per week), including empty ones
 * @param emotions   every emotion that appeared, most frequent first
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
     * Totals for the period.
     *
     * @param checkIns             number of check-ins in the period
     * @param activeDays           number of different days with at least one check-in
     * @param avgMood              average mood score of the period's check-ins
     * @param previousCheckIns     number of check-ins in the period just before
     * @param previousAvgMood      the average mood score of that earlier period
     * @param moodChange           avgMood - previousAvgMood, when both exist
     * @param avgIntensity         average strength (1-5) over the check-ins that gave one, {@code null} when none did
     * @param previousAvgIntensity the same for the period just before
     */
    public record Summary(int checkIns, int activeDays, Double avgMood, int previousCheckIns,
                          Double previousAvgMood, Double moodChange, Double avgIntensity,
                          Double previousAvgIntensity) {
    }

    /**
     * One day (or one week) of the chart.
     *
     * @param start        first day covered
     * @param end          last day covered
     * @param checkIns     check-ins in this bucket
     * @param avgMood      average mood score, or {@code null} when there is none
     * @param avgIntensity average strength 1-5, or {@code null} when none was given
     * @param groups       how many emotions fell in each group; always lists all four groups
     * @param emotions     how often each emotion appeared; only emotions that were seen
     */
    public record Bucket(LocalDate start, LocalDate end, int checkIns, Double avgMood, Double avgIntensity,
                         Map<String, Integer> groups, Map<String, Integer> emotions) {
    }

    /**
     * How often an emotion appeared in the period, and on how many different days.
     *
     * @param emotion the emotion code
     * @param group   its group code
     * @param count   how many times it was picked
     * @param days    on how many different days
     */
    public record EmotionStat(String emotion, String group, int count, int days) {
    }
}
