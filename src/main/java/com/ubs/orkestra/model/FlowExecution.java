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
@Table(name = "flow_executions")
public class FlowExecution {

    @Id
    @Column(name = "id", updatable = false, nullable = false, columnDefinition = "uuid")
    private UUID id;

    @NotNull
    @Column(name = "flow_id", nullable = false)
    private Long flowId;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;


    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "runtime_variables", columnDefinition = "json")
    private Map<String, String> runtimeVariables;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ExecutionStatus status;

    @Column(name = "is_replay")
    private Boolean isReplay = Boolean.FALSE;

    @Column(name = "original_flow_execution_id", columnDefinition = "uuid")
    private UUID originalFlowExecutionId;

    @Column(name = "category")
    private String category;

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.MERGE)
    @JoinColumn(name = "flow_group_id")
    private FlowGroup flowGroup;

    @Column(name = "iteration")
    private Integer iteration;

    @Column(name = "revolutions")
    private Integer revolutions;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Constructors
    public FlowExecution() {
        this.id = UUID.randomUUID();
    }

    public FlowExecution(Long flowId, Map<String, String> runtimeVariables) {
        this();
        this.flowId = flowId;
        this.runtimeVariables = runtimeVariables;
        this.status = ExecutionStatus.RUNNING;
        this.startTime = LocalDateTime.now();
        this.isReplay = Boolean.FALSE;
        this.originalFlowExecutionId = null;
        this.category = "uncategorized";
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Long getFlowId() {
        return flowId;
    }

    public void setFlowId(Long flowId) {
        this.flowId = flowId;
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


    public Map<String, String> getRuntimeVariables() {
        return runtimeVariables;
    }

    public void setRuntimeVariables(Map<String, String> runtimeVariables) {
        this.runtimeVariables = runtimeVariables;
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

    public UUID getOriginalFlowExecutionId() {
        return originalFlowExecutionId;
    }

    public void setOriginalFlowExecutionId(UUID originalFlowExecutionId) {
        this.originalFlowExecutionId = originalFlowExecutionId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public FlowGroup getFlowGroup() {
        return flowGroup;
    }

    public void setFlowGroup(FlowGroup flowGroup) {
        this.flowGroup = flowGroup;
    }

    public Integer getIteration() {
        return iteration;
    }

    public void setIteration(Integer iteration) {
        this.iteration = iteration;
    }

    public Integer getRevolutions() {
        return revolutions;
    }

    public void setRevolutions(Integer revolutions) {
        this.revolutions = revolutions;
    }
}