package com.ubs.orkestra.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO for sync operation status and results
 */
public class SyncStatusDto {
    
    private boolean inProgress;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long totalFlowExecutions;
    private Long totalPipelineExecutions;
    private Long syncedFlowExecutions;
    private Long syncedPipelineExecutions;
    private Long updatedPipelineExecutions;
    private Long failedPipelineExecutions;
    private Long skippedPipelineExecutions;
    private List<String> errors = new ArrayList<>();
    private String message;
    
    public SyncStatusDto() {
        this.errors = new ArrayList<>();
    }
    
    // Getters and setters
    
    public boolean isInProgress() {
        return inProgress;
    }
    
    public void setInProgress(boolean inProgress) {
        this.inProgress = inProgress;
    }
    
    public LocalDateTime getStartTime() {
        return startTime;
    }
    
    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }
    
    public LocalDateTime getEndTime() {
        return endTime;
    }
    
    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }
    
    public Long getTotalFlowExecutions() {
        return totalFlowExecutions;
    }
    
    public void setTotalFlowExecutions(Long totalFlowExecutions) {
        this.totalFlowExecutions = totalFlowExecutions;
    }
    
    public Long getTotalPipelineExecutions() {
        return totalPipelineExecutions;
    }
    
    public void setTotalPipelineExecutions(Long totalPipelineExecutions) {
        this.totalPipelineExecutions = totalPipelineExecutions;
    }
    
    public Long getSyncedFlowExecutions() {
        return syncedFlowExecutions;
    }
    
    public void setSyncedFlowExecutions(Long syncedFlowExecutions) {
        this.syncedFlowExecutions = syncedFlowExecutions;
    }
    
    public Long getSyncedPipelineExecutions() {
        return syncedPipelineExecutions;
    }
    
    public void setSyncedPipelineExecutions(Long syncedPipelineExecutions) {
        this.syncedPipelineExecutions = syncedPipelineExecutions;
    }
    
    public Long getUpdatedPipelineExecutions() {
        return updatedPipelineExecutions;
    }
    
    public void setUpdatedPipelineExecutions(Long updatedPipelineExecutions) {
        this.updatedPipelineExecutions = updatedPipelineExecutions;
    }
    
    public Long getFailedPipelineExecutions() {
        return failedPipelineExecutions;
    }
    
    public void setFailedPipelineExecutions(Long failedPipelineExecutions) {
        this.failedPipelineExecutions = failedPipelineExecutions;
    }
    
    public Long getSkippedPipelineExecutions() {
        return skippedPipelineExecutions;
    }
    
    public void setSkippedPipelineExecutions(Long skippedPipelineExecutions) {
        this.skippedPipelineExecutions = skippedPipelineExecutions;
    }
    
    public List<String> getErrors() {
        return errors;
    }
    
    public void setErrors(List<String> errors) {
        this.errors = errors;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public void addError(String error) {
        if (errors == null) {
            errors = new ArrayList<>();
        }
        errors.add(error);
    }
}
