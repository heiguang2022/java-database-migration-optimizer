package com.heiguang.portfolio.migration.migration;

import static com.heiguang.portfolio.migration.migration.MigrationModels.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class MigrationService {
    private final JdbcTemplate source;
    private final JdbcTemplate target;
    private final TransactionTemplate transaction;

    public MigrationService(@Qualifier("sourceJdbcTemplate") JdbcTemplate source,
                            @Qualifier("targetJdbcTemplate") JdbcTemplate target,
                            TransactionTemplate transaction) {
        this.source = source;
        this.target = target;
        this.transaction = transaction;
    }

    public MigrationReport migrate(MigrationRequest request) {
        String runId = request.resumeRunId() == null || request.resumeRunId().isBlank() ? startRun(request.batchSize()) : resumeRun(request.resumeRunId(), request.batchSize());
        long processedAtStart = getReport(runId).processedRows();
        try {
            while (true) {
                MigrationReport state = getReport(runId);
                List<CleanCustomer> batch = readBatch(state.lastSourceId(), request.batchSize()).stream().map(this::clean).toList();
                if (batch.isEmpty()) break;
                // 同一事务内写入数据和检查点，避免数据已写入但断点未推进。
                transaction.executeWithoutResult(status -> {
                    batch.forEach(this::upsert);
                    long checkpoint = batch.get(batch.size() - 1).id();
                    target.update("UPDATE migration_runs SET last_source_id=?, processed_rows=processed_rows+? WHERE id=?", checkpoint, batch.size(), runId);
                });
                long processedThisAttempt = getReport(runId).processedRows() - processedAtStart;
                if (request.failAfterRows() != null && processedThisAttempt >= request.failAfterRows()) {
                    throw new IllegalStateException("Simulated interruption after " + processedThisAttempt + " rows");
                }
            }
            finishAndValidate(runId);
        } catch (Exception ex) {
            target.update("INSERT INTO migration_failures(id, run_id, source_id, error_message, created_at) VALUES (?,?,?,?,?)", UUID.randomUUID().toString(), runId, getReport(runId).lastSourceId(), trim(ex.getMessage(), 1000), Timestamp.from(Instant.now()));
            target.update("UPDATE migration_runs SET status='FAILED', failed_rows=failed_rows+1, error_message=?, completed_at=? WHERE id=?", trim(ex.getMessage(), 1000), Timestamp.from(Instant.now()), runId);
        }
        return getReport(runId);
    }

    public MigrationReport getReport(String runId) {
        List<MigrationReport> reports = target.query("SELECT * FROM migration_runs WHERE id=?", (rs, row) -> new MigrationReport(
                rs.getString("id"), rs.getString("status"), rs.getInt("batch_size"), rs.getLong("last_source_id"),
                rs.getLong("processed_rows"), rs.getLong("failed_rows"), nullableLong(rs, "source_count"), nullableLong(rs, "target_count"),
                rs.getString("source_checksum"), rs.getString("target_checksum"),
                rs.getString("source_checksum") != null && rs.getString("source_checksum").equals(rs.getString("target_checksum")),
                rs.getTimestamp("started_at").toInstant(), rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toInstant(), rs.getString("error_message")), runId);
        if (reports.isEmpty()) throw new MigrationNotFoundException(runId);
        return reports.get(0);
    }

    private String startRun(int batchSize) {
        String id = UUID.randomUUID().toString();
        target.update("INSERT INTO migration_runs(id,status,batch_size,last_source_id,processed_rows,failed_rows,started_at) VALUES (?,'RUNNING',?,0,0,0,?)", id, batchSize, Timestamp.from(Instant.now()));
        return id;
    }

    private String resumeRun(String id, int batchSize) {
        MigrationReport report = getReport(id);
        if (!"FAILED".equals(report.status())) throw new IllegalArgumentException("Only FAILED runs can be resumed");
        target.update("UPDATE migration_runs SET status='RUNNING', batch_size=?, error_message=NULL, completed_at=NULL WHERE id=?", batchSize, id);
        return id;
    }

    private List<LegacyCustomer> readBatch(long lastId, int batchSize) {
        return source.query("SELECT id,full_name,email,active_flag,updated_at FROM legacy_customers WHERE id>? ORDER BY id LIMIT ?",
                (rs, row) -> new LegacyCustomer(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getTimestamp(5).toInstant()), lastId, batchSize);
    }

    private CleanCustomer clean(LegacyCustomer row) {
        return new CleanCustomer(row.id(), row.fullName().trim().replaceAll("\\s+", " "), row.email().trim().toLowerCase(Locale.ROOT),
                "Y".equalsIgnoreCase(row.activeFlag()) ? "ACTIVE" : "INACTIVE", row.updatedAt());
    }

    private void upsert(CleanCustomer row) {
        int updated = target.update("UPDATE customers SET full_name=?,email=?,status=?,source_updated_at=?,migrated_at=? WHERE source_id=?",
                row.fullName(), row.email(), row.status(), Timestamp.from(row.updatedAt()), Timestamp.from(Instant.now()), row.id());
        if (updated == 0) target.update("INSERT INTO customers(source_id,full_name,email,status,source_updated_at,migrated_at) VALUES (?,?,?,?,?,?)",
                row.id(), row.fullName(), row.email(), row.status(), Timestamp.from(row.updatedAt()), Timestamp.from(Instant.now()));
    }

    private void finishAndValidate(String runId) {
        long sourceCount = source.queryForObject("SELECT COUNT(*) FROM legacy_customers", Long.class);
        long targetCount = target.queryForObject("SELECT COUNT(*) FROM customers", Long.class);
        String sourceHash = checksum(readAllSource().stream().map(this::clean).toList());
        String targetHash = checksum(target.query("SELECT source_id,full_name,email,status,source_updated_at FROM customers ORDER BY source_id",
                (rs, row) -> new CleanCustomer(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getTimestamp(5).toInstant())));
        String status = sourceCount == targetCount && sourceHash.equals(targetHash) ? "COMPLETED" : "VALIDATION_FAILED";
        target.update("UPDATE migration_runs SET status=?,source_count=?,target_count=?,source_checksum=?,target_checksum=?,completed_at=? WHERE id=?",
                status, sourceCount, targetCount, sourceHash, targetHash, Timestamp.from(Instant.now()), runId);
    }

    private List<LegacyCustomer> readAllSource() {
        return source.query("SELECT id,full_name,email,active_flag,updated_at FROM legacy_customers ORDER BY id",
                (rs, row) -> new LegacyCustomer(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getTimestamp(5).toInstant()));
    }

    private String checksum(List<CleanCustomer> rows) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (CleanCustomer row : rows) digest.update((row.id()+"|"+row.fullName()+"|"+row.email()+"|"+row.status()+"\n").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception ex) { throw new IllegalStateException("Unable to calculate checksum", ex); }
    }

    private static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column); return rs.wasNull() ? null : value;
    }
    private static String trim(String value, int max) { return value == null ? "Unknown error" : value.substring(0, Math.min(value.length(), max)); }

    public static class MigrationNotFoundException extends RuntimeException {
        public MigrationNotFoundException(String id) { super("Migration run not found: " + id); }
    }
}
