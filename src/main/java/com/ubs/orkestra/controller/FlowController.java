package com.ubs.orkestra.controller;

import com.ubs.orkestra.dto.CombinedFlowDto;
import com.ubs.orkestra.dto.FlowCreateDto;
import com.ubs.orkestra.service.CombinedFlowService;
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

@RestController
@RequestMapping("/api/flows")
@Tag(name = "Flows", description = "Flow Management API")
public class FlowController {

    private static final Logger logger = LoggerFactory.getLogger(FlowController.class);

    @Autowired
    private CombinedFlowService combinedFlowService;

    @PostMapping
    @Operation(summary = "Create a new flow", description = "Create a new test automation flow with flow steps referencing existing test data by IDs")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Flow created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input data or referenced applications/test data not found")
    })
    public ResponseEntity<CombinedFlowDto> createFlow(
            @Valid @RequestBody FlowCreateDto flowCreateDto) {
        logger.info("Creating new flow with {} steps", flowCreateDto.getFlowSteps().size());
        
        try {
            CombinedFlowDto createdFlow = combinedFlowService.createFlowFromCreateDto(flowCreateDto);
            return new ResponseEntity<>(createdFlow, HttpStatus.CREATED);
        } catch (IllegalArgumentException e) {
            logger.error("Failed to create flow: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get flow by ID", description = "Retrieve a specific flow by its ID with embedded flow steps and test data")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Flow found"),
            @ApiResponse(responseCode = "404", description = "Flow not found")
    })
    public ResponseEntity<CombinedFlowDto> getFlowById(
            @Parameter(description = "Flow ID") @PathVariable Long id) {
        logger.debug("Fetching combined flow with ID: {}", id);
        
        return combinedFlowService.getCombinedFlowById(id)
                .map(flow -> ResponseEntity.ok(flow))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    @Operation(summary = "Get all flows", description = "Retrieve all flows with embedded flow steps and test data. Supports pagination and sorting with default sorting by updatedAt in descending order.")
    @ApiResponse(responseCode = "200", description = "Flows retrieved successfully")
    public ResponseEntity<?> getAllFlows(
            @Parameter(description = "Filter by Squash test case ID") @RequestParam(required = false) Long squashTestCaseId,
            @Parameter(description = "Page number (0-based)") @RequestParam(required = false) Integer page,
            @Parameter(description = "Page size") @RequestParam(required = false) Integer size,
            @Parameter(description = "Sort by field (e.g., 'id', 'createdAt', 'updatedAt')") @RequestParam(required = false) String sortBy,
            @Parameter(description = "Sort direction (ASC or DESC)") @RequestParam(required = false) String sortDirection) {
        
        logger.debug("Fetching combined flows with squashTestCaseId filter: {}, page: {}, size: {}, sortBy: {}, sortDirection: {}", 
                    squashTestCaseId, page, size, sortBy, sortDirection);
        
        // If pagination parameters are provided, use pagination
        if (page != null || size != null) {
            int pageNumber = page != null ? page : 0;
            int pageSize = size != null ? size : 20; // default page size
            
            Sort sort;
            if (sortBy != null && !sortBy.trim().isEmpty()) {
                // Use provided sort field and direction
                Sort.Direction direction = sortDirection != null ? 
                    Sort.Direction.fromString(sortDirection) : Sort.Direction.DESC;
                sort = Sort.by(direction, sortBy);
            } else {
                // Default sort: updatedAt DESC
                sort = Sort.by(Sort.Direction.DESC, "updatedAt");
            }
            
            Pageable pageable = PageRequest.of(pageNumber, pageSize, sort);
            
            Page<CombinedFlowDto> flowsPage;
            if (squashTestCaseId != null) {
                flowsPage = combinedFlowService.getCombinedFlowsBySquashTestCaseId(squashTestCaseId, pageable);
            } else {
                flowsPage = combinedFlowService.getAllCombinedFlows(pageable);
            }
            
            return ResponseEntity.ok(flowsPage);
        } else {
            // Return all data without pagination (backward compatibility)
            // Apply default sorting (updatedAt DESC) even without pagination
            Sort sort;
            if (sortBy != null && !sortBy.trim().isEmpty()) {
                // Use provided sort field and direction
                Sort.Direction direction = sortDirection != null ? 
                    Sort.Direction.fromString(sortDirection) : Sort.Direction.DESC;
                sort = Sort.by(direction, sortBy);
            } else {
                // Default sort: updatedAt DESC
                sort = Sort.by(Sort.Direction.DESC, "updatedAt");
            }
            
            Pageable pageable = PageRequest.of(0, Integer.MAX_VALUE, sort);
            Page<CombinedFlowDto> flowsPage;
            if (squashTestCaseId != null) {
                flowsPage = combinedFlowService.getCombinedFlowsBySquashTestCaseId(squashTestCaseId, pageable);
            } else {
                flowsPage = combinedFlowService.getAllCombinedFlows(pageable);
            }
            
            return ResponseEntity.ok(flowsPage.getContent());
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update flow", description = "Update an existing flow with flow steps referencing existing test data by IDs")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Flow updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input data or referenced applications/test data not found"),
            @ApiResponse(responseCode = "404", description = "Flow not found")
    })
    public ResponseEntity<CombinedFlowDto> updateFlow(
            @Parameter(description = "Flow ID") @PathVariable Long id,
            @Valid @RequestBody FlowCreateDto flowCreateDto) {
        logger.info("Updating flow with ID: {}", id);
        
        try {
            CombinedFlowDto updatedFlow = combinedFlowService.updateFlowFromCreateDto(id, flowCreateDto);
            return ResponseEntity.ok(updatedFlow);
        } catch (IllegalArgumentException e) {
            logger.error("Failed to update flow: {}", e.getMessage());
            if (e.getMessage().contains("not found with ID")) {
                return ResponseEntity.notFound().build();
            } else {
                return ResponseEntity.badRequest().build();
            }
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete flow", description = "Delete a flow by its ID along with all associated flow steps and test data")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Flow deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Flow not found")
    })
    public ResponseEntity<Void> deleteFlow(
            @Parameter(description = "Flow ID") @PathVariable Long id) {
        logger.info("Deleting combined flow with ID: {}", id);
        
        try {
            combinedFlowService.deleteCombinedFlow(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            logger.error("Failed to delete flow: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
}