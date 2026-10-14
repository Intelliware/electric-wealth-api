package com.electricwealth.api.config;

import java.nio.file.Files;
import java.nio.file.Path;

import com.zaxxer.hikari.HikariDataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Connects to the SQLite file built by the sibling electric-wealth-db repo. The schema belongs to that repo,
 * so this app never creates or migrates tables.
 */
@Configuration
public class DatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    HikariDataSource dataSource(AppProperties props, DataSourceProperties dataSourceProperties) {
        Path dbPath = Path.of(props.dbPath()).toAbsolutePath().normalize();
        if (!Files.exists(dbPath)) {
            throw new IllegalStateException("Database not found at " + dbPath + ".\n"
                    + "Build it first: cd ../electric-wealth-db && npm run db:reset\n"
                    + "(or set DB_PATH in .env to point at an existing database file)");
        }
        log.info("Database: {}", dbPath);
        dataSourceProperties.setUrl("jdbc:sqlite:" + dbPath + "?foreign_keys=true&busy_timeout=3000");
        return dataSourceProperties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }
}
