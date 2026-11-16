package com.tavares.recommender.user;

import com.tavares.recommender.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires up who may sign in, and hands check-ins from before logins existed to the first user. */
@Configuration
public class UserConfig {

    private static final Logger log = LoggerFactory.getLogger(UserConfig.class);

    /**
     * Builds the list of people from configuration and logs who is configured (never the codes).
     *
     * @param properties the {@code app.*} settings
     * @return the directory
     * @throws IllegalArgumentException if {@code APP_USERS} is malformed (the message never contains a code)
     */
    @Bean
    public UserDirectory userDirectory(AppProperties properties) {
        UserDirectory directory = new UserDirectory(properties);
        if (properties.users() != null && !properties.users().isBlank() && properties.accessTokenEnabled()) {
            log.warn("APP_USERS is set, so the old APP_ACCESS_TOKEN is ignored. You can remove it.");
        }
        log.info("Login: {}", directory.authRequired()
            ? "access code required for " + directory.names()
            : "no access code configured (local use)");
        return directory;
    }

    /**
     * Runs once after startup: gives pre-login check-ins to the first user.
     *
     * @param users     the user service
     * @param directory the configured people
     * @return the startup task
     */
    @Bean
    public ApplicationRunner adoptExistingCheckIns(UserService users, UserDirectory directory) {
        return (ApplicationArguments args) -> users.adoptLegacyData(directory);
    }
}
