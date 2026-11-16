package com.tavares.recommender;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.tavares.recommender.config.AppProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * MoodTunes: a small Spring Boot service that turns a few words about how someone feels into a short list of emotions,
 * keeps a private mood log per person, and suggests music that fits.
 *
 * <p>The browser app in {@code src/main/resources/static} is served by this same process. See the README for the
 * architecture, the configuration variables and how to deploy.
 */
@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class RecommenderApplication {
    /**
     * Starts the application.
     *
     * @param args command-line arguments, passed on to Spring Boot unchanged
     */
    public static void main(String[] args) {
        SpringApplication.run(RecommenderApplication.class, args);
    }
}
