package com.heiguang.portfolio.migration.migration;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;

public final class MigrationModels {
    private MigrationModels() {}

    public record MigrationRequest(@Min(1) @Max(1000) int batchSize, String resumeRunId, @Min(1) Long failAfterRows) {
        public MigrationRequest { if (batchSize == 0) batchSize = 100; }
    }

    public record MigrationReport(String runId, String status, int batchSize, long lastSourceId,
                                  long processedRows, long failedRows, Long sourceCount, Long targetCount,
                                  String sourceChecksum, String targetChecksum, boolean dataMatches,
                                  Instant startedAt, Instant completedAt, String errorMessage) {}

    record LegacyCustomer(long id, String fullName, String email, String activeFlag, Instant updatedAt) {}
    record CleanCustomer(long id, String fullName, String email, String status, Instant updatedAt) {}
}
