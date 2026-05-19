package com.kingwiredemo.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;

/**
 * Explicit datasource configuration demonstrating two distinct data sources:
 *
 * 1. PRIMARY  — MySQL 8 (the central harmonized datastore)
 *    HikariCP tuned for a mixed OLTP + reporting workload:
 *    - maximumPoolSize=10: handles concurrent API requests without over-provisioning
 *    - minimumIdle=2: keeps warm connections available without holding unnecessary resources
 *    - connectionTimeout=30s: surface connection issues quickly rather than hanging
 *    - idleTimeout=10min: reclaim idle connections in low-traffic windows
 *    - maxLifetime=30min: rotate connections before MySQL's default wait_timeout (8h)
 *    - initializationFailTimeout=-1: do not crash HikariCP pool startup if MySQL
 *      is not yet ready — allow the pool to come up and wait for a live connection.
 *      Note: this only covers pool initialisation. Flyway's connection retries
 *      (connect-retries in application.yml) handle the period after pool startup.
 *
 * 2. LEGACY   — H2 embedded (simulates a replicated on-premise ERP database)
 *    Seeded at startup via DDL/DML scripts. In a real environment this would be
 *    an ODBC/JDBC connection to Epicor, SAP, or a mirrored MS SQL replica.
 *
 * Railway deployment note:
 *    Railway injects MYSQL_URL as a full jdbc: connection string. The datasource
 *    URL in application.yml prefers MYSQL_URL when present, so no code change is
 *    needed here — Spring resolves the URL before passing it to this config class.
 *    Ensure these variables are set in the Railway app service's variable panel:
 *
 *      MYSQL_URL      — full jdbc URL copied from Railway MySQL service variables
 *                       e.g. jdbc:mysql://monorail.proxy.rlwy.net:12345/railway?...
 *      MYSQL_USER     — from Railway MySQL service variables (typically "root")
 *      MYSQL_PASSWORD — from Railway MySQL service variables
 *
 *    If MYSQL_URL is set, the individual MYSQL_HOST / MYSQL_PORT / MYSQL_DB
 *    variables are ignored for the primary datasource URL but MYSQL_USER and
 *    MYSQL_PASSWORD are still required.
 */
@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    public DataSource primaryDataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password) {

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");

        // Pool sizing — tuned for single-node reporting API
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);

        // Timeout / lifecycle
        config.setConnectionTimeout(30_000);    // 30s — fail fast on pool exhaustion
        config.setIdleTimeout(600_000);          // 10m — reclaim idle connections
        config.setMaxLifetime(1_800_000);        // 30m — rotate before MySQL server timeout

        // Do not crash the pool during startup if MySQL is not yet reachable.
        // -1 means: wait indefinitely for a live connection before marking pool failed.
        // Flyway's connect-retries (application.yml) then handles the migration phase.
        config.setInitializationFailTimeout(-1);

        // Diagnostics
        config.setPoolName("KingWire-MySQL-Primary");
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        return new HikariDataSource(config);
    }

    /**
     * Simulates a legacy ERP replicated database (e.g. Epicor item master).
     * H2 is seeded from scripts on startup — no external dependency required.
     */
    @Bean(name = "legacyErpDataSource")
    public DataSource legacyErpDataSource() {
        return new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("legacy-erp")
                .addScript("classpath:db/legacy/V1__legacy_erp_schema.sql")
                .addScript("classpath:db/legacy/V2__legacy_erp_seed.sql")
                .build();
    }

    @Bean(name = "legacyJdbcTemplate")
    public JdbcTemplate legacyJdbcTemplate(
            @org.springframework.beans.factory.annotation.Qualifier("legacyErpDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }
}