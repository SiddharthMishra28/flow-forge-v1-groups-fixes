package com.ubs.orkestra.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "flows")
public class Flow {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "flow_step_ids", columnDefinition = "json", nullable = false)
    private List<Long> flowStepIds;

    @NotNull
    @Column(name = "squash_test_case_id", nullable = false)
    private Long squashTestCaseId;

    @NotNull
    @Column(name = "squash_test_case", nullable = false)
    private String squashTestCase;


    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Constructors
    public Flow() {}

    public Flow(List<Long> flowStepIds, Long squashTestCaseId, String squashTestCase) {
        this.flowStepIds = flowStepIds;
        this.squashTestCaseId = squashTestCaseId;
        this.squashTestCase = squashTestCase;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public List<Long> getFlowStepIds() {
        return flowStepIds;
    }

    public void setFlowStepIds(List<Long> flowStepIds) {
        this.flowStepIds = flowStepIds;
    }

    public Long getSquashTestCaseId() {
        return squashTestCaseId;
    }

    public void setSquashTestCaseId(Long squashTestCaseId) {
        this.squashTestCaseId = squashTestCaseId;
    }

    public String getSquashTestCase() {
        return squashTestCase;
    }

    public void setSquashTestCase(String squashTestCase) {
        this.squashTestCase = squashTestCase;
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
}
