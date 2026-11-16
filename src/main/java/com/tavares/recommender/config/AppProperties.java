package com.tavares.recommender.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Application-level settings (prefix {@code app}).
 *
 * @param accessToken optional shared secret required in the {@code X-Access-Token} header of every API call;
 *                    blank disables the check
 * @param rateLimit   request limits protecting the (paid) upstream AI API
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
    @DefaultValue("") String accessToken,
    @DefaultValue RateLimit rateLimit
) {

    public boolean accessTokenEnabled() {
        return accessToken != null && !accessToken.isBlank();
    }

    public record RateLimit(
        @DefaultValue("20") int perClientPerMinute,
        @DefaultValue("120") int globalPerMinute
    ) {
    }
}
