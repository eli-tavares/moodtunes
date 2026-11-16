package com.tavares.recommender.user;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository repository;
    private final ConcurrentMap<String, Long> ids = new ConcurrentHashMap<>();

    public UserService(UserRepository repository) {
        this.repository = repository;
    }

    /** The database id for a signed-in user name, creating the user row on first use. */
    public long idOf(String name) {
        return ids.computeIfAbsent(name, repository::idOrCreate);
    }

    public boolean musicEnabled(long userId) {
        return repository.musicEnabled(userId);
    }

    public void setMusicEnabled(long userId, boolean enabled) {
        repository.setMusicEnabled(userId, enabled);
    }

    /** Called once at startup with the directory's first user. */
    public void adoptLegacyData(UserDirectory directory) {
        long first = idOf(directory.firstName());
        ids.clear();   // a legacy "me" row may have been merged away
        int moved = repository.adoptLegacyData(first, directory.names().contains(UserDirectory.LEGACY_USER));
        if (moved > 0) {
            log.info("Assigned {} existing check-in(s) to the first user.", moved);
        }
    }
}
