package com.tavares.recommender.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Provides the application {@link Clock}, so time can be fixed in tests. */
@Configuration
public class ClockConfig {

    /**
     * A bean so tests can substitute a fixed clock. Always UTC; callers apply the person's time zone.
     *
     * @return the system clock in UTC
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
