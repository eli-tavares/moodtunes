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

    public String code() {
        return code;
    }

    public int days() {
        return days;
    }

    public int bucketDays() {
        return bucketDays;
    }

    public static ReportRange parse(String value) {
        return Arrays.stream(values())
            .filter(r -> r.code.equalsIgnoreCase(value))
            .findFirst()
            .orElseThrow(() -> new BadRequestException("range must be week, month or 3months."));
    }
}
