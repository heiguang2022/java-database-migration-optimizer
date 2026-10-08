package com.heiguang.portfolio.migration.web;

import com.heiguang.portfolio.migration.migration.MigrationModels.MigrationReport;
import com.heiguang.portfolio.migration.migration.MigrationModels.MigrationRequest;
import com.heiguang.portfolio.migration.migration.MigrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/migrations")
public class MigrationController {
    private final MigrationService service;
    public MigrationController(MigrationService service) { this.service = service; }

    @PostMapping("/customers") @ResponseStatus(HttpStatus.CREATED)
    public MigrationReport migrate(@Valid @RequestBody MigrationRequest request) { return service.migrate(request); }

    @GetMapping("/{runId}")
    public MigrationReport report(@PathVariable String runId) { return service.getReport(runId); }
}
