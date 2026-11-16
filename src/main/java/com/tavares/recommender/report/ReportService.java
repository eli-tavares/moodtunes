package com.tavares.recommender.report;

import com.tavares.recommender.checkin.CheckIn;
import com.tavares.recommender.checkin.CheckInRepository;
import com.tavares.recommender.checkin.CheckInResponse;
import com.tavares.recommender.checkin.Emotion;
import com.tavares.recommender.report.ReportResponse.Bucket;
import com.tavares.recommender.report.ReportResponse.EmotionStat;
import com.tavares.recommender.report.ReportResponse.Summary;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.stereotype.Service;

/**
 * Builds week / month / 3-month reports. The data is tiny (a handful of check-ins a day), so rows are read for the
 * period and aggregated here rather than in SQL: the same code then behaves identically on H2 and PostgreSQL.
 */
@Service
public class ReportService {

    private final CheckInRepository repository;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param repository where check-ins are read from
     * @param clock      the time source for "today"
     */
    public ReportService(CheckInRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Builds a report for one person and period, compared with the period just before it.
     *
     * @param userId the owner
     * @param range  week, month or 3 months
     * @param zone   the viewer's time zone, which decides what "today" and each day boundary mean
     * @return the report
     */
    public ReportResponse report(long userId, ReportRange range, ZoneId zone) {
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate from = today.minusDays(range.days() - 1L);
        LocalDate previousFrom = from.minusDays(range.days());

        Instant start = previousFrom.atStartOfDay(zone).toInstant();
        Instant end = today.plusDays(1).atStartOfDay(zone).toInstant();
        List<CheckIn> all = repository.findBetween(userId, start, end);

        List<CheckIn> current = new ArrayList<>();
        List<CheckIn> previous = new ArrayList<>();
        for (CheckIn checkIn : all) {
            (localDate(checkIn, zone).isBefore(from) ? previous : current).add(checkIn);
        }

        return new ReportResponse(range.code(), from, today, range.bucketDays(),
            summary(current, previous, zone), buckets(current, range, from, zone), emotionStats(current, zone));
    }

    /**
     * Builds everything needed for a therapy session in one go: the last-7-days report plus every check-in in that window,
     * oldest first, with the person's kept words and the "sounded heavy" markers.
     *
     * @param userId the owner
     * @param zone   the viewer's time zone
     * @return the report and the check-ins behind it
     */
    public SessionSummary sessionSummary(long userId, ZoneId zone) {
        ReportResponse week = report(userId, ReportRange.WEEK, zone);
        Instant start = week.from().atStartOfDay(zone).toInstant();
        Instant end = week.to().plusDays(1).atStartOfDay(zone).toInstant();
        return new SessionSummary(week, repository.findBetween(userId, start, end).stream()
            .map(CheckInResponse::from).toList());
    }

    /**
     * The calendar day of a check-in in the viewer's time zone.
     *
     * @param checkIn the check-in
     * @param zone    the viewer's time zone
     * @return its local date
     */
    private static LocalDate localDate(CheckIn checkIn, ZoneId zone) {
        return checkIn.createdAt().atZone(zone).toLocalDate();
    }

    /**
     * Averages the mood scores of some check-ins.
     *
     * @param checkIns the check-ins
     * @return the average, or {@code null} when none has a score
     */
    private static Double averageMood(List<CheckIn> checkIns) {
        return checkIns.stream().map(CheckIn::mood).filter(m -> m != null)
            .mapToDouble(Double::doubleValue).average().stream().boxed().findFirst().orElse(null);
    }

    /**
     * Averages the strength of some check-ins, skipping those that gave none.
     *
     * @param checkIns the check-ins
     * @return the average, or {@code null} when none gave a strength
     */
    private static Double averageIntensity(List<CheckIn> checkIns) {
        return checkIns.stream().map(CheckIn::intensity).filter(i -> i != null)
            .mapToInt(Integer::intValue).average().stream().boxed().findFirst().orElse(null);
    }

    /**
     * Rounds to 2 decimals so the JSON is tidy and tests are stable.
     *
     * @param value the number, or {@code null}
     * @return the rounded number, or {@code null} when {@code value} is {@code null}
     */
    private static Double round(Double value) {
        return value == null ? null : Math.round(value * 100.0) / 100.0;
    }

    /**
     * Totals for the current period, compared with the previous one.
     *
     * @param current  check-ins in the period
     * @param previous check-ins in the period before
     * @param zone     the viewer's time zone
     * @return the summary
     */
    private Summary summary(List<CheckIn> current, List<CheckIn> previous, ZoneId zone) {
        Set<LocalDate> days = new HashSet<>();
        current.forEach(c -> days.add(localDate(c, zone)));
        Double avg = averageMood(current);
        Double previousAvg = averageMood(previous);
        Double change = avg != null && previousAvg != null ? avg - previousAvg : null;
        return new Summary(current.size(), days.size(), round(avg), previous.size(), round(previousAvg),
            round(change), round(averageIntensity(current)), round(averageIntensity(previous)));
    }

    /**
     * Splits the period into days (or weeks) and counts what happened in each, including empty ones.
     *
     * @param current check-ins in the period
     * @param range   the period, which decides the bucket size
     * @param from    the first day of the period
     * @param zone    the viewer's time zone
     * @return one bucket per day or week, oldest first
     */
    private List<Bucket> buckets(List<CheckIn> current, ReportRange range, LocalDate from, ZoneId zone) {
        int count = range.days() / range.bucketDays();
        List<List<CheckIn>> perBucket = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            perBucket.add(new ArrayList<>());
        }
        for (CheckIn checkIn : current) {
            long offset = ChronoUnit.DAYS.between(from, localDate(checkIn, zone));
            perBucket.get((int) (offset / range.bucketDays())).add(checkIn);
        }

        List<Bucket> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            LocalDate bucketStart = from.plusDays((long) i * range.bucketDays());
            List<CheckIn> items = perBucket.get(i);

            Map<String, Integer> groups = new LinkedHashMap<>();
            for (Emotion.Group group : Emotion.Group.values()) {
                groups.put(group.code(), 0);
            }
            Map<String, Integer> emotions = new TreeMap<>();
            for (CheckIn checkIn : items) {
                for (Emotion emotion : checkIn.emotions()) {
                    groups.merge(emotion.group().code(), 1, Integer::sum);
                    emotions.merge(emotion.code(), 1, Integer::sum);
                }
            }
            result.add(new Bucket(bucketStart, bucketStart.plusDays(range.bucketDays() - 1L), items.size(),
                round(averageMood(items)), round(averageIntensity(items)), groups, emotions));
        }
        return result;
    }

    /**
     * Counts how often each emotion was picked, and on how many days.
     *
     * @param current check-ins in the period
     * @param zone    the viewer's time zone
     * @return the emotions, most frequent first (ties by name)
     */
    private List<EmotionStat> emotionStats(List<CheckIn> current, ZoneId zone) {
        Map<Emotion, Integer> counts = new EnumMap<>(Emotion.class);
        Map<Emotion, Set<LocalDate>> days = new EnumMap<>(Emotion.class);
        for (CheckIn checkIn : current) {
            for (Emotion emotion : checkIn.emotions()) {
                counts.merge(emotion, 1, Integer::sum);
                days.computeIfAbsent(emotion, k -> new HashSet<>()).add(localDate(checkIn, zone));
            }
        }
        return counts.entrySet().stream()
            .map(e -> new EmotionStat(e.getKey().code(), e.getKey().group().code(), e.getValue(),
                days.get(e.getKey()).size()))
            .sorted(Comparator.comparingInt(EmotionStat::count).reversed().thenComparing(EmotionStat::emotion))
            .toList();
    }
}
