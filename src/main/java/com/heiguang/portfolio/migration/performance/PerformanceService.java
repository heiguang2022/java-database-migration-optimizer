package com.heiguang.portfolio.migration.performance;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class PerformanceService {
    private static final String INDEX = "idx_orders_customer_created";
    private final JdbcTemplate target;

    public PerformanceService(@Qualifier("targetJdbcTemplate") JdbcTemplate target) { this.target = target; }

    public BenchmarkReport benchmark(long customerId, int repetitions) {
        String query = "SELECT * FROM orders WHERE customer_id=? AND created_at>=? ORDER BY created_at DESC";
        Timestamp since = Timestamp.from(Instant.parse("2025-06-01T00:00:00Z").minus(1, ChronoUnit.DAYS));
        target.execute("DROP INDEX IF EXISTS " + INDEX);
        String beforePlan = explain(query, customerId, since);
        double beforeMs = averageMillis(query, customerId, since, repetitions);
        target.execute("CREATE INDEX IF NOT EXISTS " + INDEX + " ON orders(customer_id, created_at DESC)");
        String afterPlan = explain(query, customerId, since);
        double afterMs = averageMillis(query, customerId, since, repetitions);
        boolean indexDetected = afterPlan.toLowerCase().contains(INDEX);
        return new BenchmarkReport(customerId, repetitions, beforePlan, afterPlan, beforeMs, afterMs, indexDetected,
                "Timings are local observations, not a production performance guarantee.");
    }

    private String explain(String sql, Object... params) {
        List<String> rows = target.query("EXPLAIN " + sql, (rs, row) -> rs.getString(1), params);
        return String.join("\n", rows);
    }

    private double averageMillis(String sql, long customerId, Timestamp since, int repetitions) {
        long start = System.nanoTime();
        for (int i = 0; i < repetitions; i++) target.queryForList(sql, customerId, since);
        return Math.round((System.nanoTime() - start) / 1_000_000.0 / repetitions * 1000.0) / 1000.0;
    }

    public record BenchmarkReport(long customerId, int repetitions, String beforePlan, String afterPlan,
                                  double averageBeforeMs, double averageAfterMs, boolean indexDetected,
                                  String measurementNote) {}
}
