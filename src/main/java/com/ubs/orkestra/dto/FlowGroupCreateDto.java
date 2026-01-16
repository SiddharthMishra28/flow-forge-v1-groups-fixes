package com.ubs.orkestra.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(example = """
        {
          "flowGroupName": "string",
          "flows": [
            0
          ]
        }
        """)
public class FlowGroupCreateDto {

    @NotBlank
    private String flowGroupName;

    @NotNull
    private List<Long> flows;

    // Constructors
    public FlowGroupCreateDto() {}

    public FlowGroupCreateDto(String flowGroupName, List<Long> flows) {
        this.flowGroupName = flowGroupName;
        this.flows = flows;
    }

    // Getters and Setters
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
}