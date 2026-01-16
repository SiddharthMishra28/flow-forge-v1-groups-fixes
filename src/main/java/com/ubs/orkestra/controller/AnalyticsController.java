package com.ubs.orkestra.controller;

import com.ubs.orkestra.dto.analytics.*;
import com.ubs.orkestra.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/metrics")
@Tag(name = "Analytics & Metrics", description = "Quality metrics and analytics operations")
public class AnalyticsController {

    @Autowired
    private AnalyticsService analyticsService;

    @GetMapping("/summary")
    @Operation(summary = "Get overall metrics summary", 
               description = "Returns overall pass/fail/cancelled counts, success rate, and average execution time")
    public ResponseEntity<MetricsSummaryDto> getMetricsSummary() {
        MetricsSummaryDto summary = analyticsService.getMetricsSummary();
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/executions")
    @Operation(summary = "Get execution statistics", 
               description = "Returns execution stats grouped by application, flow, or pipeline")
    public ResponseEntity<List<ExecutionStatsDto>> getExecutionStats(
            @Parameter(description = "Group by: application, flow, or pipeline", example = "flow")
            @RequestParam(defaultValue = "flow") String groupBy) {
        List<ExecutionStatsDto> stats = analyticsService.getExecutionStats(groupBy);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/duration")
    @Operation(summary = "Get duration statistics", 
               description = "Returns avg/min/max execution times per flow or pipeline")
    public ResponseEntity<List<DurationStatsDto>> getDurationStats(
            @Parameter(description = "Type: flow or pipeline", example = "flow")
            @RequestParam(defaultValue = "flow") String type) {
        List<DurationStatsDto> stats = analyticsService.getDurationStats(type);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/trends/pass-fail")
    @Operation(summary = "Get pass/fail trends", 
               description = "Returns pass/fail trend data by day, week, or month")
    public ResponseEntity<List<TrendDataDto>> getPassFailTrends(
            @Parameter(description = "Period: day, week, or month", example = "day")
            @RequestParam(defaultValue = "day") String period,
            @Parameter(description = "Number of days to look back", example = "30")
            @RequestParam(defaultValue = "30") int days) {
        List<TrendDataDto> trends = analyticsService.getPassFailTrends(period, days);
        return ResponseEntity.ok(trends);
    }

    @GetMapping("/trends/duration")
    @Operation(summary = "Get duration trends", 
               description = "Returns duration trend over time")
    public ResponseEntity<List<TrendDataDto>> getDurationTrends(
            @Parameter(description = "Period: day, week, or month", example = "day")
            @RequestParam(defaultValue = "day") String period,
            @Parameter(description = "Number of days to look back", example = "30")
            @RequestParam(defaultValue = "30") int days) {
        List<TrendDataDto> trends = analyticsService.getDurationTrends(period, days);
        return ResponseEntity.ok(trends);
    }

    @GetMapping("/trends/failures")
    @Operation(summary = "Get top failing entities", 
               description = "Returns top failing applications, flows, or branches over time")
    public ResponseEntity<List<FailureAnalysisDto>> getTopFailures(
            @Parameter(description = "Type: application, flow, or branch", example = "flow")
            @RequestParam(defaultValue = "flow") String type,
            @Parameter(description = "Maximum number of results to return", example = "10")
            @RequestParam(defaultValue = "10") int limit) {
        List<FailureAnalysisDto> failures = analyticsService.getTopFailures(type, limit);
        return ResponseEntity.ok(failures);
    }
}