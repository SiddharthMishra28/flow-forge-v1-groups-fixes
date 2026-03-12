package com.ubs.orkestra.dto;

import java.util.List;
import java.util.UUID;

/**
 * DTO for patching an Artifact's runIds.
 * Supports adding and/or removing FlowExecution IDs.
 */
public class ArtifactPatchDto {

    private List<UUID> addRunIds;
    private List<UUID> removeRunIds;

    // Constructors
    public ArtifactPatchDto() {}

    public ArtifactPatchDto(List<UUID> addRunIds, List<UUID> removeRunIds) {
        this.addRunIds = addRunIds;
        this.removeRunIds = removeRunIds;
    }

    // Getters and Setters
    public List<UUID> getAddRunIds() {
        return addRunIds;
    }

    public void setAddRunIds(List<UUID> addRunIds) {
        this.addRunIds = addRunIds;
    }

    public List<UUID> getRemoveRunIds() {
        return removeRunIds;
    }

    public void setRemoveRunIds(List<UUID> removeRunIds) {
        this.removeRunIds = removeRunIds;
    }
}
