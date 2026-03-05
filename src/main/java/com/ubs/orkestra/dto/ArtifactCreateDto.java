package com.ubs.orkestra.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public class ArtifactCreateDto {

    @NotBlank(message = "Artifact name must not be blank")
    private String artifactName;

    @NotEmpty(message = "runIds must not be empty")
    private List<UUID> runIds;

    // Constructors
    public ArtifactCreateDto() {}

    public ArtifactCreateDto(String artifactName, List<UUID> runIds) {
        this.artifactName = artifactName;
        this.runIds = runIds;
    }

    // Getters and Setters
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
}
