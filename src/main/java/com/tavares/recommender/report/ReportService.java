package com.tavares.recommender.report;

import com.tavares.recommender.checkin.CheckIn;
import com.tavares.recommender.checkin.CheckInRepository;
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

    public ReportService(CheckInRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public ReportResponse report(ReportRange range, ZoneId zone) {
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate from = today.minusDays(range.days() - 1L);
        LocalDate previousFrom = from.minusDays(range.days());

        Instant start = previousFrom.atStartOfDay(zone).toInstant();
        Instant end = today.plusDays(1).atStartOfDay(zone).toInstant();
        List<CheckIn> all = repository.findBetween(start, end);

        List<CheckIn> current = new ArrayList<>();
        List<CheckIn> previous = new ArrayList<>();
        for (CheckIn checkIn : all) {
            (localDate(checkIn, zone).isBefore(from) ? previous : current).add(checkIn);
        }

        return new ReportResponse(range.code(), from, today, range.bucketDays(),
            summary(current, previous, zone), buckets(current, range, from, zone), emotionStats(current, zone));
    }

    private static LocalDate localDate(CheckIn checkIn, ZoneId zone) {
        return checkIn.createdAt().atZone(zone).toLocalDate();
    }

    private static Double averageMood(List<CheckIn> checkIns) {
        return checkIns.stream().map(CheckIn::mood).filter(m -> m != null)
            .mapToDouble(Double::doubleValue).average().stream().boxed().findFirst().orElse(null);
    }

    /** Rounded to 2 decimals so the JSON is tidy and tests are stable. */
    private static Double round(Double value) {
        return value == null ? null : Math.round(value * 100.0) / 100.0;
    }

    private Summary summary(List<CheckIn> current, List<CheckIn> previous, ZoneId zone) {
        Set<LocalDate> days = new HashSet<>();
        current.forEach(c -> days.add(localDate(c, zone)));
        Double avg = averageMood(current);
        Double previousAvg = averageMood(previous);
        Double change = avg != null && previousAvg != null ? avg - previousAvg : null;
        return new Summary(current.size(), days.size(), round(avg), previous.size(), round(previousAvg),
            round(change));
    }

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
                round(averageMood(items)), groups, emotions));
        }
        return result;
    }

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
