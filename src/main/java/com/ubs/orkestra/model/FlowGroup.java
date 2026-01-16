package com.ubs.orkestra.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "flow_groups")
public class FlowGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @NotBlank
    @Column(name = "flow_group_name", nullable = false)
    private String flowGroupName;

    @NotNull
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "flows", columnDefinition = "json", nullable = false)
    private List<Long> flows;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "current_iteration", nullable = false)
    private Integer currentIteration = 0;

    @Column(name = "revolutions", nullable = false)
    private Integer revolutions = 0;

    // Constructors
    public FlowGroup() {}

    public FlowGroup(String flowGroupName, List<Long> flows) {
        this.flowGroupName = flowGroupName;
        this.flows = flows;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFlowGroupName() {
        return flowGroupName;
    }

    public void setFlowGroupName(String flowGroupName) {
        this.flowGroupName = flowGroupName;
    }

    public List<Long> getFlows() {
        return flows;
    }

    public void setFlows(List<Long> flows) {
        this.flows = flows;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getCurrentIteration() {
        return currentIteration;
    }

    public void setCurrentIteration(Integer currentIteration) {
        this.currentIteration = currentIteration;
    }

    public Integer getRevolutions() {
        return revolutions;
    }

    public void setRevolutions(Integer revolutions) {
        this.revolutions = revolutions;
    }
}
