package com.ubs.orkestra.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a flow execution that is queued for execution when thread pool capacity becomes available.
 * This ensures flows survive server restarts and are executed in order when resources are available.
 */
@Entity
@Table(name = "queued_flow_executions", indexes = {
    @Index(name = "idx_queued_created_at", columnList = "created_at")
})
public class QueuedFlowExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "queued_flow_executions_seq")
    @SequenceGenerator(name = "queued_flow_executions_seq", sequenceName = "queued_flow_executions_seq", allocationSize = 1)
    private Long id;

    @NotNull
    @Column(name = "flow_execution_id", nullable = false, columnDefinition = "uuid")
    private UUID flowExecutionId;

    @NotNull
    @Column(name = "flow_id", nullable = false)
    private Long flowId;

    @Column(name = "flow_group_id")
    private Long flowGroupId;

    @Column(name = "iteration")
    private Integer iteration;

    @Column(name = "revolutions")
    private Integer revolutions;

    @Column(name = "category")
    private String category;

    @Column(name = "priority")
    private Integer priority = 0; // Higher priority executes first

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "retry_count")
    private Integer retryCount = 0;

    // Constructors
    public QueuedFlowExecution() {}

    public QueuedFlowExecution(UUID flowExecutionId, Long flowId, Long flowGroupId, 
                              Integer iteration, Integer revolutions, String category) {
        this.flowExecutionId = flowExecutionId;
        this.flowId = flowId;
        this.flowGroupId = flowGroupId;
        this.iteration = iteration;
        this.revolutions = revolutions;
        this.category = category;
        this.priority = 0;
        this.retryCount = 0;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UUID getFlowExecutionId() {
        return flowExecutionId;
    }

    public void setFlowExecutionId(UUID flowExecutionId) {
        this.flowExecutionId = flowExecutionId;
    }

    public Long getFlowId() {
        return flowId;
    }

    public void setFlowId(Long flowId) {
        this.flowId = flowId;
    }

    public Long getFlowGroupId() {
        return flowGroupId;
    }

    public void setFlowGroupId(Long flowGroupId) {
        this.flowGroupId = flowGroupId;
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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }
}
