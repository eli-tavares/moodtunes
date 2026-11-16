package com.tavares.recommender.user;

import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class UserRepository {

    private final JdbcClient jdbc;

    public UserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Long> findId(String name) {
        return jdbc.sql("SELECT id FROM app_user WHERE name = :name").param("name", name).query(Long.class)
            .optional();
    }

    /** Finds the user, creating the row on first use. Safe if two requests race to create the same user. */
    public long idOrCreate(String name) {
        Optional<Long> existing = findId(name);
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            jdbc.sql("INSERT INTO app_user (name) VALUES (:name)").param("name", name).update();
        } catch (DuplicateKeyException raced) {
            // another request created it between our SELECT and INSERT: fine, read it back
        }
        return findId(name).orElseThrow();
    }

    public boolean musicEnabled(long userId) {
        return jdbc.sql("SELECT music_enabled FROM app_user WHERE id = :id").param("id", userId)
            .query(Boolean.class).optional().orElse(true);
    }

    public void setMusicEnabled(long userId, boolean enabled) {
        jdbc.sql("UPDATE app_user SET music_enabled = :enabled WHERE id = :id")
            .param("enabled", enabled).param("id", userId).update();
    }

    /**
     * Hands check-ins saved before logins existed (no owner) to {@code userId}. When the app was first deployed with
     * the legacy single code, that code's user was called "me": its check-ins are handed over too, so switching to
     * named users loses nothing.
     */
    @Transactional
    public int adoptLegacyData(long userId, boolean legacyUserStillConfigured) {
        int moved = jdbc.sql("UPDATE check_in SET user_id = :id WHERE user_id IS NULL").param("id", userId).update();
        if (!legacyUserStillConfigured) {
            Optional<Long> legacy = findId(UserDirectory.LEGACY_USER);
            if (legacy.isPresent() && legacy.get() != userId) {
                moved += jdbc.sql("UPDATE check_in SET user_id = :to WHERE user_id = :from")
                    .param("to", userId).param("from", legacy.get()).update();
                jdbc.sql("DELETE FROM app_user WHERE id = :id").param("id", legacy.get()).update();
            }
        }
        return moved;
    }
}
