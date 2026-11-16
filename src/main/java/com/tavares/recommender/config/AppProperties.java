package com.tavares.recommender.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Application-level settings (prefix {@code app}).
 *
 * @param accessToken legacy single shared code. Used only when {@code users} is blank: it then acts as the code of a
 *                    single user named "me". Blank (and no users) disables login entirely, for local use.
 * @param users       the people who may sign in, as {@code name:code,name:code} (for example
 *                    {@code elit:CODE1,ainek:CODE2}). The first one adopts any check-ins saved before logins existed.
 * @param rateLimit   request limits protecting the (paid) upstream AI API
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
    @DefaultValue("") String accessToken,
    @DefaultValue("") String users,
    @DefaultValue RateLimit rateLimit
) {

    public boolean accessTokenEnabled() {
        return accessToken != null && !accessToken.isBlank();
    }

    /**
     * @param perClientPerMinute        AI calls (analyze, recommend) per client; these cost money
     * @param globalPerMinute           AI calls across all clients: a hard ceiling on spend
     * @param authFailuresPerMinute     wrong access codes per client, against guessing
     * @param generalPerClientPerMinute everything else (check-ins, reports): cheap, so much more generous
     * @param generalGlobalPerMinute    same, across all clients
     */
    public record RateLimit(
        @DefaultValue("20") int perClientPerMinute,
        @DefaultValue("120") int globalPerMinute,
        @DefaultValue("10") int authFailuresPerMinute,
        @DefaultValue("120") int generalPerClientPerMinute,
        @DefaultValue("600") int generalGlobalPerMinute
    ) {
    }
}
