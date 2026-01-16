package com.ubs.orkestra.service;

import com.ubs.orkestra.dto.analytics.*;
import com.ubs.orkestra.enums.ExecutionStatus;
import com.ubs.orkestra.repository.FlowExecutionRepository;
import com.ubs.orkestra.repository.PipelineExecutionRepository;
import com.ubs.orkestra.repository.FlowRepository;
import com.ubs.orkestra.repository.ApplicationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    private static final Logger logger = LoggerFactory.getLogger(AnalyticsService.class);

    @Autowired
    private FlowExecutionRepository flowExecutionRepository;

    @Autowired
    private PipelineExecutionRepository pipelineExecutionRepository;

    @Autowired
    private FlowRepository flowRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    public MetricsSummaryDto getMetricsSummary() {
        logger.debug("Calculating metrics summary");

        // Get flow execution counts
        Long totalFlowExecutions = flowExecutionRepository.count();
        Long flowPassed = flowExecutionRepository.countByStatus(ExecutionStatus.PASSED);
        Long flowFailed = flowExecutionRepository.countByStatus(ExecutionStatus.FAILED);
        Long flowCancelled = flowExecutionRepository.countByStatus(ExecutionStatus.CANCELLED);

        // Get pipeline execution counts
        Long totalPipelineExecutions = pipelineExecutionRepository.count();

        // Calculate rates
        Double successRate = totalFlowExecutions > 0 ? 
            (flowPassed.doubleValue() / totalFlowExecutions.doubleValue()) * 100 : 0.0;
        Double failureRate = totalFlowExecutions > 0 ? 
            (flowFailed.doubleValue() / totalFlowExecutions.doubleValue()) * 100 : 0.0;

        // Calculate average execution time
        Double avgExecutionTime = calculateAverageFlowExecutionTime();

        return new MetricsSummaryDto(
            totalFlowExecutions,
            totalPipelineExecutions,
            flowPassed,
            flowFailed,
            flowCancelled,
            successRate,
            failureRate,
            avgExecutionTime
        );
    }

    public List<ExecutionStatsDto> getExecutionStats(String groupBy) {
        logger.debug("Getting execution stats grouped by: {}", groupBy);

        switch (groupBy.toLowerCase()) {
            case "application":
                return getExecutionStatsByApplication();
            case "flow":
                return getExecutionStatsByFlow();
            case "pipeline":
                return getExecutionStatsByPipeline();
            default:
                throw new IllegalArgumentException("Invalid groupBy parameter. Use: application, flow, or pipeline");
        }
    }

    public List<DurationStatsDto> getDurationStats(String type) {
        logger.debug("Getting duration stats for type: {}", type);

        switch (type.toLowerCase()) {
            case "flow":
                return getDurationStatsByFlow();
            case "pipeline":
                return getDurationStatsByPipeline();
            default:
                throw new IllegalArgumentException("Invalid type parameter. Use: flow or pipeline");
        }
    }

    public List<TrendDataDto> getPassFailTrends(String period, int days) {
        logger.debug("Getting pass/fail trends for period: {} over {} days", period, days);

        try {
            LocalDateTime startDate = LocalDateTime.now().minusDays(days);
            List<Object[]> results = flowExecutionRepository.findPassFailTrendData(startDate);
            
            return results.stream()
                .map(row -> {
                    try {
                        LocalDate date = ((java.sql.Date) row[0]).toLocalDate();
                        Long passed = ((Number) row[1]).longValue();
                        Long failed = ((Number) row[2]).longValue();
                        Long cancelled = ((Number) row[3]).longValue();
                        Long total = ((Number) row[4]).longValue();
                        
                        return new TrendDataDto(
                            date,
                            formatPeriod(java.sql.Date.valueOf(date), period),
                            passed, failed, cancelled, total,
                            calculateSuccessRate(passed, total),
                            null
                        );
                    } catch (Exception e) {
                        logger.warn("Error processing trend data row: {}", e.getMessage());
                        return null;
                    }
                })
                .filter(dto -> dto != null)
                .collect(Collectors.toList());
        } catch (Exception e) {
            logger.warn("Could not get pass/fail trends: {}", e.getMessage());
            return List.of();
        }
    }

    public List<TrendDataDto> getDurationTrends(String period, int days) {
        logger.debug("Getting duration trends for period: {} over {} days", period, days);

        try {
            LocalDateTime startDate = LocalDateTime.now().minusDays(days);
            List<Object[]> results = flowExecutionRepository.findDurationTrendData(startDate);
            
            return results.stream()
                .map(row -> {
                    try {
                        LocalDate date = ((java.sql.Date) row[0]).toLocalDate();
                        Long total = ((Number) row[1]).longValue();
                        Double avgDuration = row[2] != null ? ((Number) row[2]).doubleValue() : 0.0;
                        
                        return new TrendDataDto(
                            date,
                            formatPeriod(java.sql.Date.valueOf(date), period),
                            null, null, null, // pass/fail counts not applicable
                            total,
                            null, // success rate not applicable
                            avgDuration
                        );
                    } catch (Exception e) {
                        logger.warn("Error processing duration trend data row: {}", e.getMessage());
                        return null;
                    }
                })
                .filter(dto -> dto != null)
                .collect(Collectors.toList());
        } catch (Exception e) {
            logger.warn("Could not get duration trends: {}", e.getMessage());
            return List.of();
        }
    }

    public List<FailureAnalysisDto> getTopFailures(String type, int limit) {
        logger.debug("Getting top {} failures for type: {}", limit, type);

        switch (type.toLowerCase()) {
            case "application":
                return getTopFailingApplications(limit);
            case "flow":
                return getTopFailingFlows(limit);
            case "branch":
                return getTopFailingBranches(limit);
            default:
                throw new IllegalArgumentException("Invalid type parameter. Use: application, flow, or branch");
        }
    }

    // Private helper methods
    private Double calculateAverageFlowExecutionTime() {
        try {
            List<Object[]> results = flowExecutionRepository.findAverageExecutionTime();
            if (results.isEmpty() || results.get(0)[0] == null) return 0.0;
            
            Object avgResult = results.get(0)[0];
            if (avgResult instanceof Number) {
                return ((Number) avgResult).doubleValue();
            }
            return 0.0;
        } catch (Exception e) {
            logger.warn("Could not calculate average execution time: {}", e.getMessage());
            return 0.0;
        }
    }

    private List<ExecutionStatsDto> getExecutionStatsByApplication() {
        List<Object[]> results = flowExecutionRepository.findExecutionStatsByApplication();
        
        return results.stream()
            .map(row -> new ExecutionStatsDto(
                (Long) row[0], // applicationId
                null, null,
                (String) row[1], // applicationName
                (Long) row[2], // total
                (Long) row[3], // passed
                (Long) row[4], // failed
                (Long) row[5], // cancelled
                calculateSuccessRate((Long) row[3], (Long) row[2])
            ))
            .collect(Collectors.toList());
    }

    private List<ExecutionStatsDto> getExecutionStatsByFlow() {
        List<Object[]> results = flowExecutionRepository.findExecutionStatsByFlow();
        
        return results.stream()
            .map(row -> new ExecutionStatsDto(
                null,
                (Long) row[0], // flowId
                null,
                "Flow " + row[0], // flowName
                (Long) row[1], // total
                (Long) row[2], // passed
                (Long) row[3], // failed
                (Long) row[4], // cancelled
                calculateSuccessRate((Long) row[2], (Long) row[1])
            ))
            .collect(Collectors.toList());
    }

    private List<ExecutionStatsDto> getExecutionStatsByPipeline() {
        List<Object[]> results = pipelineExecutionRepository.findExecutionStatsByPipeline();
        
        return results.stream()
            .map(row -> new ExecutionStatsDto(
                null, null,
                (Long) row[0], // pipelineId
                "Pipeline " + row[0],
                (Long) row[1], // total
                (Long) row[2], // passed
                (Long) row[3], // failed
                (Long) row[4], // cancelled
                calculateSuccessRate((Long) row[2], (Long) row[1])
            ))
            .collect(Collectors.toList());
    }

    private List<DurationStatsDto> getDurationStatsByFlow() {
        List<Object[]> results = flowExecutionRepository.findDurationStatsByFlow();
        
        return results.stream()
            .map(row -> new DurationStatsDto(
                (Long) row[0], // flowId
                null,
                "Flow " + row[0],
                (Double) row[1], // avg
                (Double) row[2], // min
                (Double) row[3], // max
                (Long) row[4]    // count
            ))
            .collect(Collectors.toList());
    }

    private List<DurationStatsDto> getDurationStatsByPipeline() {
        List<Object[]> results = pipelineExecutionRepository.findDurationStatsByPipeline();
        
        return results.stream()
            .map(row -> new DurationStatsDto(
                null,
                (Long) row[0], // pipelineId
                "Pipeline " + row[0],
                (Double) row[1], // avg
                (Double) row[2], // min
                (Double) row[3], // max
                (Long) row[4]    // count
            ))
            .collect(Collectors.toList());
    }

    private List<FailureAnalysisDto> getTopFailingApplications(int limit) {
        List<Object[]> results = flowExecutionRepository.findTopFailingApplications(limit);
        
        return results.stream()
            .map(row -> new FailureAnalysisDto(
                (Long) row[0], // applicationId
                null, null,
                (String) row[1], // applicationName
                "APPLICATION",
                (Long) row[2], // failureCount
                (Long) row[3], // totalExecutions
                calculateFailureRate((Long) row[2], (Long) row[3]),
                row[4] != null ? row[4].toString() : null // lastFailureDate
            ))
            .collect(Collectors.toList());
    }

    private List<FailureAnalysisDto> getTopFailingFlows(int limit) {
        List<Object[]> results = flowExecutionRepository.findTopFailingFlows(limit);
        
        return results.stream()
            .map(row -> new FailureAnalysisDto(
                null,
                (Long) row[0], // flowId
                null,
                "Flow " + row[0],
                "FLOW",
                (Long) row[1], // failureCount
                (Long) row[2], // totalExecutions
                calculateFailureRate((Long) row[1], (Long) row[2]),
                row[3] != null ? row[3].toString() : null // lastFailureDate
            ))
            .collect(Collectors.toList());
    }

    private List<FailureAnalysisDto> getTopFailingBranches(int limit) {
        List<Object[]> results = pipelineExecutionRepository.findTopFailingBranches(limit);
        
        return results.stream()
            .map(row -> new FailureAnalysisDto(
                null, null,
                (String) row[0], // branch
                (String) row[0], // branch as entityName
                "BRANCH",
                (Long) row[1], // failureCount
                (Long) row[2], // totalExecutions
                calculateFailureRate((Long) row[1], (Long) row[2]),
                row[3] != null ? row[3].toString() : null // lastFailureDate
            ))
            .collect(Collectors.toList());
    }

    private Double calculateSuccessRate(Long passed, Long total) {
        return total > 0 ? (passed.doubleValue() / total.doubleValue()) * 100 : 0.0;
    }

    private Double calculateFailureRate(Long failed, Long total) {
        return total > 0 ? (failed.doubleValue() / total.doubleValue()) * 100 : 0.0;
    }

    private String formatPeriod(java.sql.Date date, String period) {
        LocalDate localDate = date.toLocalDate();
        switch (period.toLowerCase()) {
            case "day":
                return localDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            case "week":
                return localDate.format(DateTimeFormatter.ofPattern("yyyy-'W'ww"));
            case "month":
                return localDate.format(DateTimeFormatter.ofPattern("yyyy-MM"));
            default:
                return localDate.toString();
        }
    }
}