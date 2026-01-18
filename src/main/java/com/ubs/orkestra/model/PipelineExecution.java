package com.ubs.orkestra.model;

import com.ubs.orkestra.enums.ExecutionStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "pipeline_executions")
public class PipelineExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "pipeline_executions_seq")
    @SequenceGenerator(name = "pipeline_executions_seq", sequenceName = "pipeline_executions_seq", allocationSize = 1)
    private long id;

    @NotNull
    @Column(name = "flow_id", nullable = false)
    private Long flowId;

    @NotNull
    @Column(name = "flow_execution_id", nullable = false)
    private UUID flowExecutionId;

    @NotNull
    @Column(name = "flow_step_id", nullable = false)
    private Long flowStepId;

    @Column(name = "pipeline_id")
    private Long pipelineId;

    @Column(name = "pipeline_url")
    private String pipelineUrl;

    @Column(name = "job_id")
    private Long jobId;

    @Column(name = "job_url")
    private String jobUrl;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "configured_test_data", columnDefinition = "json")
    private Map<String, String> configuredTestData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "runtime_test_data", columnDefinition = "json")
    private Map<String, String> runtimeTestData;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ExecutionStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "is_replay", nullable = false)
    private Boolean isReplay = false;

    @Column(name = "resume_time")
    private LocalDateTime resumeTime;

    // Constructors
    public PipelineExecution() {}

    public PipelineExecution(Long flowId, UUID flowExecutionId, Long flowStepId, 
                           Map<String, String> configuredTestData, Map<String, String> runtimeTestData) {
        this.flowId = flowId;
        this.flowExecutionId = flowExecutionId;
        this.flowStepId = flowStepId;
        this.configuredTestData = configuredTestData;
        this.runtimeTestData = runtimeTestData;
        this.status = ExecutionStatus.RUNNING;
        this.startTime = LocalDateTime.now();
    }

    // Getters and Setters
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Long getFlowId() {
        return flowId;
    }

    public void setFlowId(Long flowId) {
        this.flowId = flowId;
    }

    public UUID getFlowExecutionId() {
        return flowExecutionId;
    }

    public void setFlowExecutionId(UUID flowExecutionId) {
        this.flowExecutionId = flowExecutionId;
    }

    public Long getFlowStepId() {
        return flowStepId;
    }

    public void setFlowStepId(Long flowStepId) {
        this.flowStepId = flowStepId;
    }

    public Long getPipelineId() {
        return pipelineId;
    }

    public void setPipelineId(Long pipelineId) {
        this.pipelineId = pipelineId;
    }

    public String getPipelineUrl() {
        return pipelineUrl;
    }

    public void setPipelineUrl(String pipelineUrl) {
        this.pipelineUrl = pipelineUrl;
    }

    public Long getJobId() {
        return jobId;
    }

    public void setJobId(Long jobId) {
        this.jobId = jobId;
    }

    public String getJobUrl() {
        return jobUrl;
    }

    public void setJobUrl(String jobUrl) {
        this.jobUrl = jobUrl;
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

    public Map<String, String> getConfiguredTestData() {
        return configuredTestData;
    }

    public void setConfiguredTestData(Map<String, String> configuredTestData) {
        this.configuredTestData = configuredTestData;
    }

    public Map<String, String> getRuntimeTestData() {
        return runtimeTestData;
    }

    public void setRuntimeTestData(Map<String, String> runtimeTestData) {
        this.runtimeTestData = runtimeTestData;
    }

    public ExecutionStatus getStatus() {
        return status;
    }

    public void setStatus(ExecutionStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Boolean getIsReplay() {
        return isReplay;
    }

    public void setIsReplay(Boolean isReplay) {
        this.isReplay = isReplay;
    }

    public LocalDateTime getResumeTime() {
        return resumeTime;
    }

    public void setResumeTime(LocalDateTime resumeTime) {
        this.resumeTime = resumeTime;
    }
}
