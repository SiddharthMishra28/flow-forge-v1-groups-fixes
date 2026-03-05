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
import java.util.UUID;

@Entity
@Table(name = "artifacts")
public class Artifact {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "artifacts_id_seq")
    @SequenceGenerator(name = "artifacts_id_seq", sequenceName = "artifacts_id_seq", allocationSize = 1)
    private Long id;

    @NotBlank
    @Column(name = "artifact_name", nullable = false)
    private String artifactName;

    @NotNull
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "run_ids", columnDefinition = "json", nullable = false)
    private List<UUID> runIds;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Constructors
    public Artifact() {}

    public Artifact(String artifactName, List<UUID> runIds) {
        this.artifactName = artifactName;
        this.runIds = runIds;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getArtifactName() {
        return artifactName;
    }

    public void setArtifactName(String artifactName) {
        this.artifactName = artifactName;
    }

    public List<UUID> getRunIds() {
        return runIds;
    }

    public void setRunIds(List<UUID> runIds) {
        this.runIds = runIds;
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
