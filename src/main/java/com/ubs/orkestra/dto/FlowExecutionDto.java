package com.ubs.orkestra.dto;

import com.ubs.orkestra.enums.ExecutionStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FlowExecutionDto {

    private UUID id;
    private Long flowId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Map<String, String> runtimeVariables;
    private ExecutionStatus status;
    private LocalDateTime createdAt;
    private Boolean isReplay;
    private UUID originalFlowExecutionId;
    private String category;
    private Long flowGroupId;
    private String flowGroupName;
    private Integer iteration;
    private Integer revolutions;

    // Nested details for comprehensive response
    private FlowDto flow;
    private List<FlowStepDto> flowSteps;
    private List<ApplicationDto> applications;
    private List<PipelineExecutionDto> pipelineExecutions;

    // Constructors
    public FlowExecutionDto() {}

    public FlowExecutionDto(UUID id, Long flowId, ExecutionStatus status) {
        this.id = id;
        this.flowId = flowId;
        this.status = status;
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

    public FlowDto getFlow() {
        return flow;
    }

    public void setFlow(FlowDto flow) {
        this.flow = flow;
    }

    public List<FlowStepDto> getFlowSteps() {
        return flowSteps;
    }

    public void setFlowSteps(List<FlowStepDto> flowSteps) {
        this.flowSteps = flowSteps;
    }

    public List<ApplicationDto> getApplications() {
        return applications;
    }

    public void setApplications(List<ApplicationDto> applications) {
        this.applications = applications;
    }

    public List<PipelineExecutionDto> getPipelineExecutions() {
        return pipelineExecutions;
    }

    public void setPipelineExecutions(List<PipelineExecutionDto> pipelineExecutions) {
        this.pipelineExecutions = pipelineExecutions;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
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

    public String getFlowGroupName() {
        return flowGroupName;
    }

    public void setFlowGroupName(String flowGroupName) {
        this.flowGroupName = flowGroupName;
    }
}