package com.tavares.recommender.checkin;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Plain JDBC on purpose: two tiny tables, and no ORM means less memory and a faster start. */
@Repository
public class CheckInRepository {

    private final JdbcClient jdbc;

    public CheckInRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public CheckIn save(Instant createdAt, List<Emotion> emotions) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO check_in (created_at) VALUES (:createdAt)")
            .param("createdAt", OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC))
            .update(keys, "id");
        long id = keys.getKey().longValue();
        for (Emotion emotion : emotions) {
            jdbc.sql("INSERT INTO check_in_emotion (check_in_id, emotion) VALUES (:id, :emotion)")
                .param("id", id)
                .param("emotion", emotion.code())
                .update();
        }
        return new CheckIn(id, createdAt, List.copyOf(emotions));
    }

    /** Check-ins with {@code fromInclusive <= createdAt < toExclusive}, oldest first. */
    public List<CheckIn> findBetween(Instant fromInclusive, Instant toExclusive) {
        List<Row> rows = jdbc.sql("""
                SELECT c.id, c.created_at, e.emotion
                FROM check_in c JOIN check_in_emotion e ON e.check_in_id = c.id
                WHERE c.created_at >= :from AND c.created_at < :to
                ORDER BY c.created_at, c.id, e.emotion
                """)
            .param("from", OffsetDateTime.ofInstant(fromInclusive, ZoneOffset.UTC))
            .param("to", OffsetDateTime.ofInstant(toExclusive, ZoneOffset.UTC))
            .query(CheckInRepository::mapRow)
            .list();
        return group(rows);
    }

    /** Newest first. */
    public List<CheckIn> findRecent(int limit) {
        List<Long> ids = jdbc.sql("SELECT id FROM check_in ORDER BY created_at DESC, id DESC LIMIT :limit")
            .param("limit", limit)
            .query(Long.class)
            .list();
        if (ids.isEmpty()) {
            return List.of();
        }
        List<Row> rows = jdbc.sql("""
                SELECT c.id, c.created_at, e.emotion
                FROM check_in c JOIN check_in_emotion e ON e.check_in_id = c.id
                WHERE c.id IN (:ids)
                ORDER BY c.created_at DESC, c.id DESC, e.emotion
                """)
            .param("ids", ids)
            .query(CheckInRepository::mapRow)
            .list();
        return group(rows);
    }

    /** Everything, oldest first (for the export). */
    public List<CheckIn> findAll() {
        return findBetween(Instant.EPOCH, Instant.parse("9999-01-01T00:00:00Z"));
    }

    @Transactional
    public boolean delete(long id) {
        jdbc.sql("DELETE FROM check_in_emotion WHERE check_in_id = :id").param("id", id).update();
        return jdbc.sql("DELETE FROM check_in WHERE id = :id").param("id", id).update() > 0;
    }

    private record Row(long id, Instant createdAt, String emotion) {
    }

    private static Row mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new Row(rs.getLong("id"), rs.getObject("created_at", OffsetDateTime.class).toInstant(),
            rs.getString("emotion"));
    }

    /** Folds the joined rows back into check-ins, keeping the order of the query. */
    private static List<CheckIn> group(List<Row> rows) {
        Map<Long, Instant> times = new LinkedHashMap<>();
        Map<Long, List<Emotion>> emotions = new LinkedHashMap<>();
        for (Row row : rows) {
            Emotion.fromCode(row.emotion()).ifPresent(e -> {
                times.putIfAbsent(row.id(), row.createdAt());
                emotions.computeIfAbsent(row.id(), k -> new ArrayList<>()).add(e);
            });
        }
        List<CheckIn> result = new ArrayList<>();
        emotions.forEach((id, list) -> result.add(new CheckIn(id, times.get(id), List.copyOf(list))));
        return result;
    }
}
