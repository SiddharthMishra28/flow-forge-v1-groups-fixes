package com.ubs.orkestra.dto.analytics;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "Trend data point for analytics")
public class TrendDataDto {

    @Schema(description = "Date of the data point")
    private LocalDate date;

    @Schema(description = "Period label (e.g., '2024-01', 'Week 1', etc.)")
    private String period;

    @Schema(description = "Number of passed executions")
    private Long passedCount;

    @Schema(description = "Number of failed executions")
    private Long failedCount;

    @Schema(description = "Number of cancelled executions")
    private Long cancelledCount;

    @Schema(description = "Total executions")
    private Long totalCount;

    @Schema(description = "Success rate percentage (0-100)")
    private Double successRate;

    @Schema(description = "Average duration in minutes")
    private Double averageDurationMinutes;

    // Constructors
    public TrendDataDto() {}

    public TrendDataDto(LocalDate date, String period, Long passedCount, Long failedCount, 
                       Long cancelledCount, Long totalCount, Double successRate, Double averageDurationMinutes) {
        this.date = date;
        this.period = period;
        this.passedCount = passedCount;
        this.failedCount = failedCount;
        this.cancelledCount = cancelledCount;
        this.totalCount = totalCount;
        this.successRate = successRate;
        this.averageDurationMinutes = averageDurationMinutes;
    }

    // Getters and Setters
    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = period;
    }

    public Long getPassedCount() {
        return passedCount;
    }

    public void setPassedCount(Long passedCount) {
        this.passedCount = passedCount;
    }

    public Long getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(Long failedCount) {
        this.failedCount = failedCount;
    }

    public Long getCancelledCount() {
        return cancelledCount;
    }

    public void setCancelledCount(Long cancelledCount) {
        this.cancelledCount = cancelledCount;
    }

    public Long getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(Long totalCount) {
        this.totalCount = totalCount;
    }

    public Double getSuccessRate() {
        return successRate;
    }

    public void setSuccessRate(Double successRate) {
        this.successRate = successRate;
    }

    public Double getAverageDurationMinutes() {
        return averageDurationMinutes;
    }

    public void setAverageDurationMinutes(Double averageDurationMinutes) {
        this.averageDurationMinutes = averageDurationMinutes;
    }
}