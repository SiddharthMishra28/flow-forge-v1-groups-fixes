package com.ubs.orkestra.dto;

import java.util.List;

public class FlowGroupPatchDto {

    private List<Long> addFlows;
    private List<Long> removeFlows;

    // Constructors
    public FlowGroupPatchDto() {}

    public FlowGroupPatchDto(List<Long> addFlows, List<Long> removeFlows) {
        this.addFlows = addFlows;
        this.removeFlows = removeFlows;
    }

    // Getters and Setters
    public List<Long> getAddFlows() {
        return addFlows;
    }

    public void setAddFlows(List<Long> addFlows) {
        this.addFlows = addFlows;
    }

    public List<Long> getRemoveFlows() {
        return removeFlows;
    }

    public void setRemoveFlows(List<Long> removeFlows) {
        this.removeFlows = removeFlows;
    }
}
