package com.ubs.orkestra.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class ArtifactDto {

    private Long id;
    private String artifactName;
    private List<UUID> runIds;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Constructors
    public ArtifactDto() {}

    public ArtifactDto(Long id, String artifactName, List<UUID> runIds, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.artifactName = artifactName;
        this.runIds = runIds;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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
