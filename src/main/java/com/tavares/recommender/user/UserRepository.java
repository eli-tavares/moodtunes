package com.tavares.recommender.user;

import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Reads and writes the {@code app_user} table: who exists and their personal settings. */
@Repository
public class UserRepository {

    private final JdbcClient jdbc;

    /**
     * Creates the repository.
     *
     * @param jdbc the JDBC client for the application's database
     */
    public UserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Looks a person up by name.
     *
     * @param name the lower-case user name
     * @return their id, or empty if they have not been created yet
     */
    public Optional<Long> findId(String name) {
        return jdbc.sql("SELECT id FROM app_user WHERE name = :name").param("name", name).query(Long.class)
            .optional();
    }

    /**
     * Finds the user, creating the row on first use. Safe if two requests race to create the same user.
     *
     * @param name the lower-case user name
     * @return the user's id
     */
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

    /**
     * Reads the person's music switch.
     *
     * @param userId the person
     * @return {@code true} if music suggestions are on (also when the person is unknown)
     */
    public boolean musicEnabled(long userId) {
        return jdbc.sql("SELECT music_enabled FROM app_user WHERE id = :id").param("id", userId)
            .query(Boolean.class).optional().orElse(true);
    }

    /**
     * Saves the person's music switch.
     *
     * @param userId  the person
     * @param enabled {@code true} to turn music suggestions on
     */
    public void setMusicEnabled(long userId, boolean enabled) {
        jdbc.sql("UPDATE app_user SET music_enabled = :enabled WHERE id = :id")
            .param("enabled", enabled).param("id", userId).update();
    }

    /**
     * Hands check-ins saved before logins existed (no owner) to a user. When the app was first deployed with the legacy
     * single code, that code's user was called "me": its check-ins are handed over too, so switching to named users loses
     * nothing.
     *
     * @param userId                   who receives the check-ins
     * @param legacyUserStillConfigured whether the legacy user "me" is still a configured login; if so it is left alone
     * @return how many check-ins changed owner
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
