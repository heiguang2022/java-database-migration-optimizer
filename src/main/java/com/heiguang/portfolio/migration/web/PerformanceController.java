package com.heiguang.portfolio.migration.web;

import com.heiguang.portfolio.migration.performance.PerformanceService;
import com.heiguang.portfolio.migration.performance.PerformanceService.BenchmarkReport;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/performance")
public class PerformanceController {
    private final PerformanceService service;
    public PerformanceController(PerformanceService service) { this.service = service; }

    @PostMapping("/orders/benchmark")
    public BenchmarkReport benchmark(@RequestParam(defaultValue = "42") @Min(1) long customerId,
                                     @RequestParam(defaultValue = "20") @Min(1) @Max(100) int repetitions) {
        return service.benchmark(customerId, repetitions);
    }
}
