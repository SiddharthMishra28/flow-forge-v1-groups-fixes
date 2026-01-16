package com.ubs.orkestra.controller;

import com.ubs.orkestra.dto.PipelineExecutionDto;
import com.ubs.orkestra.service.PipelineExecutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/flow-executions")
@Tag(name = "Pipeline Executions", description = "Pipeline Execution Monitoring API")
public class PipelineExecutionController {

    private static final Logger logger = LoggerFactory.getLogger(PipelineExecutionController.class);

    @Autowired
    private PipelineExecutionService pipelineExecutionService;

    @GetMapping("/{flowExecutionUUID}/pipelines")
    @Operation(summary = "Get all pipeline executions for a flow execution", 
               description = "Retrieve all pipeline executions associated with a specific flow execution. Supports pagination and sorting.")
    @ApiResponse(responseCode = "200", description = "Pipeline executions retrieved successfully")
    public ResponseEntity<?> getPipelineExecutionsByFlowExecution(
            @Parameter(description = "Flow execution UUID") @PathVariable UUID flowExecutionUUID,
            @Parameter(description = "Page number (0-based)") @RequestParam(required = false) Integer page,
            @Parameter(description = "Page size") @RequestParam(required = false) Integer size,
            @Parameter(description = "Sort by field (e.g., 'startTime', 'endTime', 'status', 'createdAt')") @RequestParam(required = false) String sortBy,
            @Parameter(description = "Sort direction (ASC or DESC)") @RequestParam(required = false, defaultValue = "ASC") String sortDirection) {
        
        logger.debug("Fetching pipeline executions for flow execution UUID: {} with page: {}, size: {}, sortBy: {}, sortDirection: {}", 
                    flowExecutionUUID, page, size, sortBy, sortDirection);
        
        // If pagination parameters are provided, use pagination
        if (page != null || size != null) {
            int pageNumber = page != null ? page : 0;
            int pageSize = size != null ? size : 20; // default page size
            
            Sort sort = Sort.unsorted();
            if (sortBy != null && !sortBy.trim().isEmpty()) {
                Sort.Direction direction = Sort.Direction.fromString(sortDirection);
                sort = Sort.by(direction, sortBy);
            }
            
            Pageable pageable = PageRequest.of(pageNumber, pageSize, sort);
            Page<PipelineExecutionDto> pipelineExecutionsPage = 
                    pipelineExecutionService.getPipelineExecutionsByFlowExecutionId(flowExecutionUUID, pageable);
            
            return ResponseEntity.ok(pipelineExecutionsPage);
        } else {
            // Return all data without pagination (backward compatibility)
            List<PipelineExecutionDto> pipelineExecutions = 
                    pipelineExecutionService.getPipelineExecutionsByFlowExecutionId(flowExecutionUUID);
            return ResponseEntity.ok(pipelineExecutions);
        }
    }

    @GetMapping("/{flowExecutionUUID}/pipelines/{pipelineExecutionId}")
    @Operation(summary = "Get specific pipeline execution details", 
               description = "Get detailed information about a specific pipeline execution")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pipeline execution found"),
            @ApiResponse(responseCode = "404", description = "Pipeline execution not found")
    })
    public ResponseEntity<PipelineExecutionDto> getPipelineExecutionById(
            @Parameter(description = "Flow execution UUID") @PathVariable UUID flowExecutionUUID,
            @Parameter(description = "Pipeline execution ID (database ID, not GitLab pipeline ID)") @PathVariable Long pipelineExecutionId) {
        logger.debug("Fetching pipeline execution with ID: {} for flow execution: {}", pipelineExecutionId, flowExecutionUUID);
        
        return pipelineExecutionService.getPipelineExecutionById(pipelineExecutionId)
                .filter(pipeline -> pipeline.getFlowExecutionId().equals(flowExecutionUUID))
                .map(pipeline -> ResponseEntity.ok(pipeline))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/flows/{flowId}/pipelines")
    @Operation(summary = "Get all pipeline executions for a flow", 
               description = "Retrieve all pipeline executions across all executions of a specific flow")
    @ApiResponse(responseCode = "200", description = "Pipeline executions retrieved successfully")
    public ResponseEntity<List<PipelineExecutionDto>> getPipelineExecutionsByFlow(
            @Parameter(description = "Flow ID") @PathVariable Long flowId) {
        logger.debug("Fetching pipeline executions for flow ID: {}", flowId);
        
        List<PipelineExecutionDto> pipelineExecutions = 
                pipelineExecutionService.getPipelineExecutionsByFlowId(flowId);
        return ResponseEntity.ok(pipelineExecutions);
    }

    @GetMapping("/flow-steps/{flowStepId}/pipelines")
    @Operation(summary = "Get all pipeline executions for a flow step", 
               description = "Retrieve all pipeline executions for a specific flow step across all flow executions")
    @ApiResponse(responseCode = "200", description = "Pipeline executions retrieved successfully")
    public ResponseEntity<List<PipelineExecutionDto>> getPipelineExecutionsByFlowStep(
            @Parameter(description = "Flow step ID") @PathVariable Long flowStepId) {
        logger.debug("Fetching pipeline executions for flow step ID: {}", flowStepId);
        
        List<PipelineExecutionDto> pipelineExecutions = 
                pipelineExecutionService.getPipelineExecutionsByFlowStepId(flowStepId);
        return ResponseEntity.ok(pipelineExecutions);
    }

    @GetMapping("/{flowExecutionUUID}/gitlab-pipelines/{gitlabPipelineId}")
    @Operation(summary = "Get pipeline execution by GitLab pipeline ID", 
               description = "Get pipeline execution details using the GitLab pipeline ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pipeline execution found"),
            @ApiResponse(responseCode = "404", description = "Pipeline execution not found")
    })
    public ResponseEntity<PipelineExecutionDto> getPipelineExecutionByGitLabId(
            @Parameter(description = "Flow execution UUID") @PathVariable UUID flowExecutionUUID,
            @Parameter(description = "GitLab pipeline ID") @PathVariable Long gitlabPipelineId) {
        logger.debug("Fetching pipeline execution with GitLab pipeline ID: {} for flow execution: {}", gitlabPipelineId, flowExecutionUUID);
        
        List<PipelineExecutionDto> pipelineExecutions = pipelineExecutionService.getPipelineExecutionsByFlowExecutionId(flowExecutionUUID);
        
        return pipelineExecutions.stream()
                .filter(pipeline -> gitlabPipelineId.equals(pipeline.getPipelineId()))
                .findFirst()
                .map(pipeline -> ResponseEntity.ok(pipeline))
                .orElse(ResponseEntity.notFound().build());
    }
}