package com.tavares.recommender.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/** Turns on Spring's cache abstraction (used for the music suggestions). */
@Configuration
@EnableCaching
public class CacheConfig {
}
