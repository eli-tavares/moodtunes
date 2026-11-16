package com.tavares.recommender.report;

import com.tavares.recommender.service.BadRequestException;
import java.util.Arrays;

/**
 * The report periods: rolling windows that end today. Short windows are drawn per day; the 3-month window is
 * 13 whole weeks, drawn per week, so a chart never has more than 30 points.
 */
public enum ReportRange {
    WEEK("week", 7, 1),
    MONTH("month", 30, 1),
    THREE_MONTHS("3months", 91, 7);

    private final String code;
    private final int days;
    private final int bucketDays;

    ReportRange(String code, int days, int bucketDays) {
        this.code = code;
        this.days = days;
        this.bucketDays = bucketDays;
    }

    /**
     * The value used in the {@code range} query parameter.
     *
     * @return {@code week}, {@code month} or {@code 3months}
     */
    public String code() {
        return code;
    }

    /**
     * How long the period is.
     *
     * @return the number of days, ending today
     */
    public int days() {
        return days;
    }

    /**
     * How many days each chart point covers.
     *
     * @return 1 for per-day points, 7 for per-week points
     */
    public int bucketDays() {
        return bucketDays;
    }

    /**
     * Reads a range from the query string, ignoring case.
     *
     * @param value the text sent by the client
     * @return the matching range
     * @throws com.tavares.recommender.service.BadRequestException if it is not a known range
     */
    public static ReportRange parse(String value) {
        return Arrays.stream(values())
            .filter(r -> r.code.equalsIgnoreCase(value))
            .findFirst()
            .orElseThrow(() -> new BadRequestException("range must be week, month or 3months."));
    }
}
