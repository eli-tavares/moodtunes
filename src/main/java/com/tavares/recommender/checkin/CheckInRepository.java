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

    public CheckIn save(long userId, Instant createdAt, List<Emotion> emotions) {
        return save(userId, createdAt, emotions, null, false);
    }

    @Transactional
    public CheckIn save(long userId, Instant createdAt, List<Emotion> emotions, String note, boolean flagged) {
        return save(userId, createdAt, emotions, note, flagged, null);
    }

    @Transactional
    public CheckIn save(long userId, Instant createdAt, List<Emotion> emotions, String note, boolean flagged,
                        Integer intensity) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO check_in (user_id, created_at, note, flagged, intensity)
                VALUES (:userId, :createdAt, :note, :flagged, :intensity)""")
            .param("userId", userId)
            .param("createdAt", OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC))
            .param("note", note)
            .param("flagged", flagged)
            .param("intensity", intensity)
            .update(keys, "id");
        long id = keys.getKey().longValue();
        for (Emotion emotion : emotions) {
            jdbc.sql("INSERT INTO check_in_emotion (check_in_id, emotion) VALUES (:id, :emotion)")
                .param("id", id)
                .param("emotion", emotion.code())
                .update();
        }
        return new CheckIn(id, createdAt, List.copyOf(emotions), note, flagged, intensity);
    }

    /** One user's check-ins with {@code fromInclusive <= createdAt < toExclusive}, oldest first. */
    public List<CheckIn> findBetween(long userId, Instant fromInclusive, Instant toExclusive) {
        List<Row> rows = jdbc.sql("""
                SELECT c.id, c.created_at, c.note, c.flagged, c.intensity, e.emotion
                FROM check_in c JOIN check_in_emotion e ON e.check_in_id = c.id
                WHERE c.user_id = :userId AND c.created_at >= :from AND c.created_at < :to
                ORDER BY c.created_at, c.id, e.emotion
                """)
            .param("userId", userId)
            .param("from", OffsetDateTime.ofInstant(fromInclusive, ZoneOffset.UTC))
            .param("to", OffsetDateTime.ofInstant(toExclusive, ZoneOffset.UTC))
            .query(CheckInRepository::mapRow)
            .list();
        return group(rows);
    }

    /** One user's check-ins, newest first. */
    public List<CheckIn> findRecent(long userId, int limit) {
        List<Long> ids = jdbc.sql("""
                SELECT id FROM check_in WHERE user_id = :userId ORDER BY created_at DESC, id DESC LIMIT :limit
                """)
            .param("userId", userId)
            .param("limit", limit)
            .query(Long.class)
            .list();
        if (ids.isEmpty()) {
            return List.of();
        }
        List<Row> rows = jdbc.sql("""
                SELECT c.id, c.created_at, c.note, c.flagged, c.intensity, e.emotion
                FROM check_in c JOIN check_in_emotion e ON e.check_in_id = c.id
                WHERE c.user_id = :userId AND c.id IN (:ids)
                ORDER BY c.created_at DESC, c.id DESC, e.emotion
                """)
            .param("userId", userId)
            .param("ids", ids)
            .query(CheckInRepository::mapRow)
            .list();
        return group(rows);
    }

    /** One user's everything, oldest first (for the export). */
    public List<CheckIn> findAll(long userId) {
        return findBetween(userId, Instant.EPOCH, Instant.parse("9999-01-01T00:00:00Z"));
    }

    /** Deletes the check-in only if it belongs to {@code userId}; its emotions go with it (ON DELETE CASCADE). */
    @Transactional
    public boolean delete(long userId, long id) {
        return jdbc.sql("DELETE FROM check_in WHERE id = :id AND user_id = :userId")
            .param("id", id).param("userId", userId).update() > 0;
    }

    private record Row(long id, Instant createdAt, String note, boolean flagged, Integer intensity, String emotion) {
    }

    private static Row mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new Row(rs.getLong("id"), rs.getObject("created_at", OffsetDateTime.class).toInstant(),
            rs.getString("note"), rs.getBoolean("flagged"), (Integer) rs.getObject("intensity"),
            rs.getString("emotion"));
    }

    /** Folds the joined rows back into check-ins, keeping the order of the query. */
    private static List<CheckIn> group(List<Row> rows) {
        Map<Long, Row> heads = new LinkedHashMap<>();
        Map<Long, List<Emotion>> emotions = new LinkedHashMap<>();
        for (Row row : rows) {
            Emotion.fromCode(row.emotion()).ifPresent(e -> {
                heads.putIfAbsent(row.id(), row);
                emotions.computeIfAbsent(row.id(), k -> new ArrayList<>()).add(e);
            });
        }
        List<CheckIn> result = new ArrayList<>();
        emotions.forEach((id, list) -> {
            Row head = heads.get(id);
            result.add(new CheckIn(id, head.createdAt(), List.copyOf(list), head.note(), head.flagged(), head.intensity()));
        });
        return result;
    }
}
