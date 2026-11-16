package com.tavares.recommender.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/** Kept out of the main class so web-layer test slices do not require a cache manager. */
@Configuration
@EnableCaching
public class CacheConfig {
}
