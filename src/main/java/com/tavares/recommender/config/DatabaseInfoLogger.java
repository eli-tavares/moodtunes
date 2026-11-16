package com.tavares.recommender.config;

import java.sql.Connection;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Says which database is in use, so a deploy that silently kept the local file database is easy to spot. */
@Component
public class DatabaseInfoLogger implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInfoLogger.class);

    private final DataSource dataSource;

    /**
     * Creates the logger.
     *
     * @param dataSource the application's data source
     */
    public DatabaseInfoLogger(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Logs which database product is in use, with a warning when it is the throw-away local H2 file database.
     *
     * @param args the application arguments (unused)
     * @throws Exception if a connection to the database cannot be opened
     */
    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName();
            if (product.toLowerCase().contains("h2")) {
                log.warn("Mood history is stored in a local H2 file database. That is fine for testing, but it is "
                    + "lost when a container is redeployed. Set SPRING_DATASOURCE_URL to a PostgreSQL database "
                    + "for production.");
            } else {
                log.info("Mood history is stored in {}.", product);
            }
        }
    }
}
