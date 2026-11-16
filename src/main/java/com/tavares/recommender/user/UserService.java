package com.tavares.recommender.user;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Maps the signed-in name to a database id (with a small in-memory cache) and manages each person's settings. */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository repository;
    private final ConcurrentMap<String, Long> ids = new ConcurrentHashMap<>();

    /**
     * Creates the service.
     *
     * @param repository where users are stored
     */
    public UserService(UserRepository repository) {
        this.repository = repository;
    }

    /**
     * The database id for a signed-in name, creating the user row on first use.
     *
     * @param name the signed-in user's name
     * @return their id
     */
    public long idOf(String name) {
        return ids.computeIfAbsent(name, repository::idOrCreate);
    }

    /**
     * Tells whether a person wants music suggestions.
     *
     * @param userId the person
     * @return {@code true} if they are on
     */
    public boolean musicEnabled(long userId) {
        return repository.musicEnabled(userId);
    }

    /**
     * Turns a person's music suggestions on or off.
     *
     * @param userId  the person
     * @param enabled the new setting
     */
    public void setMusicEnabled(long userId, boolean enabled) {
        repository.setMusicEnabled(userId, enabled);
    }

    /**
     * Called once at startup: gives check-ins that have no owner (and any belonging to the legacy user "me") to the
     * directory's first person.
     *
     * @param directory the configured people
     */
    public void adoptLegacyData(UserDirectory directory) {
        long first = idOf(directory.firstName());
        ids.clear();   // a legacy "me" row may have been merged away
        int moved = repository.adoptLegacyData(first, directory.names().contains(UserDirectory.LEGACY_USER));
        if (moved > 0) {
            log.info("Assigned {} existing check-in(s) to the first user.", moved);
        }
    }
}
