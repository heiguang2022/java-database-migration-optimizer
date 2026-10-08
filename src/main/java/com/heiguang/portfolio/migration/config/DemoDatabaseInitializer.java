package com.heiguang.portfolio.migration.config;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DemoDatabaseInitializer implements ApplicationRunner {
    private final JdbcTemplate source;
    private final JdbcTemplate target;
    private final int customerCount;
    private final int orderCount;

    public DemoDatabaseInitializer(@Qualifier("sourceJdbcTemplate") JdbcTemplate source,
                                   @Qualifier("targetJdbcTemplate") JdbcTemplate target,
                                   @Value("${app.demo.customers:250}") int customerCount,
                                   @Value("${app.demo.orders:5000}") int orderCount) {
        this.source = source;
        this.target = target;
        this.customerCount = customerCount;
        this.orderCount = orderCount;
    }

    @Override
    public void run(ApplicationArguments args) {
        createTables();
        seedCustomers();
        seedOrders();
    }

    private void createTables() {
        source.execute("CREATE TABLE IF NOT EXISTS legacy_customers (id BIGINT PRIMARY KEY, full_name VARCHAR(160) NOT NULL, email VARCHAR(255) NOT NULL, active_flag CHAR(1) NOT NULL, updated_at TIMESTAMP NOT NULL)");
        target.execute("CREATE TABLE IF NOT EXISTS customers (source_id BIGINT PRIMARY KEY, full_name VARCHAR(160) NOT NULL, email VARCHAR(255) NOT NULL, status VARCHAR(20) NOT NULL, source_updated_at TIMESTAMP NOT NULL, migrated_at TIMESTAMP NOT NULL)");
        target.execute("CREATE TABLE IF NOT EXISTS migration_runs (id VARCHAR(36) PRIMARY KEY, status VARCHAR(20) NOT NULL, batch_size INT NOT NULL, last_source_id BIGINT NOT NULL, processed_rows BIGINT NOT NULL, failed_rows BIGINT NOT NULL, source_count BIGINT, target_count BIGINT, source_checksum VARCHAR(64), target_checksum VARCHAR(64), started_at TIMESTAMP NOT NULL, completed_at TIMESTAMP, error_message VARCHAR(1000))");
        target.execute("CREATE TABLE IF NOT EXISTS migration_failures (id VARCHAR(36) PRIMARY KEY, run_id VARCHAR(36) NOT NULL, source_id BIGINT, error_message VARCHAR(1000) NOT NULL, created_at TIMESTAMP NOT NULL)");
        target.execute("CREATE TABLE IF NOT EXISTS orders (id BIGINT PRIMARY KEY, customer_id BIGINT NOT NULL, created_at TIMESTAMP NOT NULL, total DECIMAL(12,2) NOT NULL)");
    }

    private void seedCustomers() {
        Long count = source.queryForObject("SELECT COUNT(*) FROM legacy_customers", Long.class);
        if (count != null && count > 0) return;
        List<Object[]> rows = new ArrayList<>();
        Instant base = Instant.parse("2025-01-01T00:00:00Z");
        for (long id = 1; id <= customerCount; id++) {
            rows.add(new Object[]{id, "  Customer " + id + "  ", "USER" + id + "@EXAMPLE.COM ", id % 7 == 0 ? "N" : "Y", Timestamp.from(base.plus(id, ChronoUnit.HOURS))});
        }
        source.batchUpdate("INSERT INTO legacy_customers(id, full_name, email, active_flag, updated_at) VALUES (?,?,?,?,?)", rows);
    }

    private void seedOrders() {
        Long count = target.queryForObject("SELECT COUNT(*) FROM orders", Long.class);
        if (count != null && count > 0) return;
        List<Object[]> rows = new ArrayList<>();
        Instant base = Instant.parse("2025-06-01T00:00:00Z");
        for (long id = 1; id <= orderCount; id++) {
            rows.add(new Object[]{id, (id % Math.max(customerCount, 1)) + 1, Timestamp.from(base.plus(id, ChronoUnit.MINUTES)), BigDecimal.valueOf(10 + id % 400)});
        }
        target.batchUpdate("INSERT INTO orders(id, customer_id, created_at, total) VALUES (?,?,?,?)", rows);
    }
}
