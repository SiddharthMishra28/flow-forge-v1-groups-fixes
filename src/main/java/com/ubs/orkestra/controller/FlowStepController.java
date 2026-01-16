package com.ubs.orkestra.controller;

import com.ubs.orkestra.dto.CombinedFlowStepDto;
import com.ubs.orkestra.dto.FlowStepCreateDto;
import com.ubs.orkestra.service.CombinedFlowStepService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/flow-steps")
@Tag(name = "Flow Steps", description = "Individual Flow Step Management API")
public class FlowStepController {

    private static final Logger logger = LoggerFactory.getLogger(FlowStepController.class);

    @Autowired
    private CombinedFlowStepService combinedFlowStepService;

    @PostMapping
    @Operation(summary = "Create a new flow step", description = "Create a new flow step configuration referencing existing test data by IDs")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Flow step created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input data"),
            @ApiResponse(responseCode = "404", description = "Referenced application or test data not found")
    })
    public ResponseEntity<CombinedFlowStepDto> createFlowStep(
            @Valid @RequestBody FlowStepCreateDto flowStepDto) {
        logger.info("Creating new flow step for application ID: {}", flowStepDto.getApplicationId());
        
        try {
            CombinedFlowStepDto createdFlowStep = combinedFlowStepService.createFlowStepFromCreateDto(flowStepDto);
            return new ResponseEntity<>(createdFlowStep, HttpStatus.CREATED);
        } catch (IllegalArgumentException e) {
            logger.error("Failed to create flow step: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get flow step by ID", description = "Retrieve a specific flow step by its ID with embedded test data")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Flow step found"),
            @ApiResponse(responseCode = "404", description = "Flow step not found")
    })
    public ResponseEntity<CombinedFlowStepDto> getFlowStepById(
            @Parameter(description = "Flow step ID") @PathVariable Long id) {
        logger.debug("Fetching flow step with ID: {}", id);
        
        return combinedFlowStepService.getFlowStepById(id)
                .map(flowStep -> ResponseEntity.ok(flowStep))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    @Operation(summary = "Get all flow steps", description = "Retrieve all flow steps with embedded test data. Supports pagination and sorting.")
    @ApiResponse(responseCode = "200", description = "Flow steps retrieved successfully")
    public ResponseEntity<?> getAllFlowSteps(
            @Parameter(description = "Filter by application ID") @RequestParam(required = false) Long applicationId,
            @Parameter(description = "Page number (0-based)") @RequestParam(required = false) Integer page,
            @Parameter(description = "Page size") @RequestParam(required = false) Integer size,
            @Parameter(description = "Sort by field (e.g., 'id', 'createdAt', 'updatedAt')") @RequestParam(required = false) String sortBy,
            @Parameter(description = "Sort direction (ASC or DESC)") @RequestParam(required = false, defaultValue = "ASC") String sortDirection) {
        
        logger.debug("Fetching flow steps with applicationId filter: {}, page: {}, size: {}, sortBy: {}, sortDirection: {}", 
                    applicationId, page, size, sortBy, sortDirection);
        
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
            
            Page<CombinedFlowStepDto> flowStepsPage;
            if (applicationId != null) {
                flowStepsPage = combinedFlowStepService.getFlowStepsByApplicationId(applicationId, pageable);
            } else {
                flowStepsPage = combinedFlowStepService.getAllFlowSteps(pageable);
            }
            
            return ResponseEntity.ok(flowStepsPage);
        } else {
            // Return all data without pagination (backward compatibility)
            List<CombinedFlowStepDto> flowSteps;
            if (applicationId != null) {
                flowSteps = combinedFlowStepService.getFlowStepsByApplicationId(applicationId);
            } else {
                flowSteps = combinedFlowStepService.getAllFlowSteps();
            }
            
            return ResponseEntity.ok(flowSteps);
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update flow step", description = "Update an existing flow step referencing existing test data by IDs")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Flow step updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input data"),
            @ApiResponse(responseCode = "404", description = "Flow step, referenced application, or test data not found")
    })
    public ResponseEntity<CombinedFlowStepDto> updateFlowStep(
            @Parameter(description = "Flow step ID") @PathVariable Long id,
            @Valid @RequestBody FlowStepCreateDto flowStepDto) {
        logger.info("Updating flow step with ID: {}", id);
        
        try {
            CombinedFlowStepDto updatedFlowStep = combinedFlowStepService.updateFlowStepFromCreateDto(id, flowStepDto);
            return ResponseEntity.ok(updatedFlowStep);
        } catch (IllegalArgumentException e) {
            logger.error("Failed to update flow step: {}", e.getMessage());
            if (e.getMessage().contains("not found")) {
                return ResponseEntity.notFound().build();
            } else {
                return ResponseEntity.badRequest().build();
            }
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete flow step", description = "Delete a flow step by its ID along with associated test data")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Flow step deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Flow step not found")
    })
    public ResponseEntity<Void> deleteFlowStep(
            @Parameter(description = "Flow step ID") @PathVariable Long id) {
        logger.info("Deleting flow step with ID: {}", id);
        
        try {
            combinedFlowStepService.deleteFlowStep(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            logger.error("Failed to delete flow step: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
}