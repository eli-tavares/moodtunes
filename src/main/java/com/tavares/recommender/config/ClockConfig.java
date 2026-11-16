package com.tavares.recommender.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    /** A bean so tests can substitute a fixed clock. Always UTC; callers apply the user's zone. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
