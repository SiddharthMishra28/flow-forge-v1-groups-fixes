package com.ubs.orkestra.controller;

import com.ubs.orkestra.dto.FlowExecutionDto;
import com.ubs.orkestra.dto.FlowGroupCreateDto;
import com.ubs.orkestra.dto.FlowGroupDetailsDto;
import com.ubs.orkestra.dto.FlowGroupDto;
import com.ubs.orkestra.dto.FlowGroupPatchDto;
import com.ubs.orkestra.dto.FlowGroupUpdateDto;
import com.ubs.orkestra.service.FlowExecutionService;
import com.ubs.orkestra.service.FlowGroupService;
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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/flow-groups")
@Tag(name = "Flow Groups", description = "Flow Group Management API")
public class FlowGroupController {

    private static final Logger logger = LoggerFactory.getLogger(FlowGroupController.class);

    @Autowired
    private FlowGroupService flowGroupService;

    @Autowired
    private FlowExecutionService flowExecutionService;

    @PostMapping
    @Operation(summary = "Create a new flow group", description = "Create a new flow group with associated flows")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Flow group created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input data")
    })
    public ResponseEntity<FlowGroupDto> createFlowGroup(
            @Valid @RequestBody FlowGroupCreateDto flowGroupCreateDto) {
        logger.info("Creating new flow group: {}", flowGroupCreateDto.getFlowGroupName());

        try {
            FlowGroupDto created = flowGroupService.createFlowGroup(flowGroupCreateDto);
            return new ResponseEntity<>(created, HttpStatus.CREATED);
        } catch (Exception e) {
            logger.error("Failed to create flow group: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get flow group by ID", description = "Retrieve a specific flow group by its ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Flow group found"),
            @ApiResponse(responseCode = "404", description = "Flow group not found")
    })
    public ResponseEntity<FlowGroupDto> getFlowGroupById(
            @Parameter(description = "Flow group ID") @PathVariable Long id) {
        logger.debug("Fetching flow group with ID: {}", id);

        return flowGroupService.getFlowGroupById(id)
                .map(flowGroup -> ResponseEntity.ok(flowGroup))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    @Operation(summary = "Get all flow groups", description = "Retrieve all flow groups with pagination and sorting")
    @ApiResponse(responseCode = "200", description = "Flow groups retrieved successfully")
    public ResponseEntity<Page<FlowGroupDto>> getAllFlowGroups(
            @Parameter(description = "Page number (0-based)") @RequestParam(required = false) Integer page,
            @Parameter(description = "Page size") @RequestParam(required = false) Integer size,
            @Parameter(description = "Sort by field (e.g., 'id', 'flowGroupName', 'createdAt', 'updatedAt')") @RequestParam(required = false) String sortBy,
            @Parameter(description = "Sort direction (ASC or DESC)") @RequestParam(required = false) String sortDirection) {

        logger.debug("Fetching flow groups with page: {}, size: {}, sortBy: {}, sortDirection: {}",
                    page, size, sortBy, sortDirection);

        // If pagination parameters are provided, use pagination
        if (page != null || size != null) {
            int pageNumber = page != null ? page : 0;
            int pageSize = size != null ? size : 20; // default page size

            Sort sort;
            if (sortBy != null && !sortBy.trim().isEmpty()) {
                Sort.Direction direction = sortDirection != null ?
                    Sort.Direction.fromString(sortDirection) : Sort.Direction.DESC;
                sort = Sort.by(direction, sortBy);
            } else {
                sort = Sort.by(Sort.Direction.DESC, "updatedAt");
            }

            Pageable pageable = PageRequest.of(pageNumber, pageSize, sort);
            Page<FlowGroupDto> flowGroupsPage = flowGroupService.getAllFlowGroups(pageable);

            return ResponseEntity.ok(flowGroupsPage);
        } else {
            // Return all data without pagination (backward compatibility)
            Sort sort;
            if (sortBy != null && !sortBy.trim().isEmpty()) {
                Sort.Direction direction = sortDirection != null ?
                    Sort.Direction.fromString(sortDirection) : Sort.Direction.DESC;
                sort = Sort.by(direction, sortBy);
            } else {
                sort = Sort.by(Sort.Direction.DESC, "updatedAt");
            }

            Pageable pageable = PageRequest.of(0, Integer.MAX_VALUE, sort);
            Page<FlowGroupDto> flowGroupsPage = flowGroupService.getAllFlowGroups(pageable);

            return ResponseEntity.ok(flowGroupsPage);
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update flow group", description = "Update an existing flow group")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Flow group updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input data"),
            @ApiResponse(responseCode = "404", description = "Flow group not found")
    })
    public ResponseEntity<FlowGroupDto> updateFlowGroup(
            @Parameter(description = "Flow group ID") @PathVariable Long id,
            @Valid @RequestBody FlowGroupUpdateDto flowGroupUpdateDto) {
        logger.info("Updating flow group with ID: {}", id);

        try {
            FlowGroupDto updated = flowGroupService.updateFlowGroup(id, flowGroupUpdateDto);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            logger.error("Failed to update flow group: {}", e.getMessage());
            if (e.getMessage().contains("not found")) {
                return ResponseEntity.notFound().build();
            } else {
                return ResponseEntity.badRequest().build();
            }
        }
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Patch flow group flows", description = "Add or remove flows from an existing flow group")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Flow group patched successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input data"),
            @ApiResponse(responseCode = "404", description = "Flow group not found")
    })
    public ResponseEntity<FlowGroupDto> patchFlowGroup(
            @Parameter(description = "Flow group ID") @PathVariable Long id,
            @Valid @RequestBody FlowGroupPatchDto flowGroupPatchDto) {
        logger.info("Patching flow group with ID: {}", id);

        try {
            FlowGroupDto updated = flowGroupService.patchFlowGroup(id,
                    flowGroupPatchDto.getAddFlows(), flowGroupPatchDto.getRemoveFlows());
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            logger.error("Failed to patch flow group: {}", e.getMessage());
            if (e.getMessage().contains("not found")) {
                return ResponseEntity.notFound().build();
            } else {
                return ResponseEntity.badRequest().build();
            }
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete flow group", description = "Delete a flow group by its ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Flow group deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Flow group not found")
    })
    public ResponseEntity<Void> deleteFlowGroup(
            @Parameter(description = "Flow group ID") @PathVariable Long id) {
        logger.info("Deleting flow group with ID: {}", id);

        try {
            flowGroupService.deleteFlowGroup(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            logger.error("Failed to delete flow group: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{flowGroupId}/execute")
    @Operation(summary = "Execute a flow group", description = "Execute all flows in the specified flow group")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Flow group execution started successfully"),
            @ApiResponse(responseCode = "404", description = "Flow group not found"),
            @ApiResponse(responseCode = "400", description = "Invalid flow group or no flows to execute"),
            @ApiResponse(responseCode = "503", description = "Thread pool at capacity - some flows rejected")
    })
    public ResponseEntity<?> executeFlowGroup(
            @Parameter(description = "Flow group ID to execute") @PathVariable Long flowGroupId) {
        logger.info("Executing flow group with ID: {}", flowGroupId);

        try {
            Map<String, Object> result = flowGroupService.executeFlowGroup(flowGroupId);
            return new ResponseEntity<>(result, HttpStatus.ACCEPTED);
        } catch (IllegalArgumentException e) {
            logger.error("Failed to execute flow group: {}", e.getMessage());
            if (e.getMessage().contains("not found")) {
                return ResponseEntity.notFound().build();
            } else {
                return ResponseEntity.badRequest().build();
            }
        } catch (Exception e) {
            logger.error("Unexpected error executing flow group: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{flowGroupId}/executions")
    @Operation(summary = "Get flow executions for a flow group", description = "Retrieve flow executions associated with a specific flow group, with optional date range filtering")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Flow executions retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Flow group not found")
    })
    public ResponseEntity<?> getFlowGroupExecutions(
            @Parameter(description = "Flow group ID") @PathVariable Long flowGroupId,
            @Parameter(description = "From date (ISO format)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @Parameter(description = "To date (ISO format)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @Parameter(description = "Page number (0-based)") @RequestParam(required = false) Integer page,
            @Parameter(description = "Page size") @RequestParam(required = false) Integer size,
            @Parameter(description = "Sort by field") @RequestParam(required = false) String sortBy,
            @Parameter(description = "Sort direction (ASC or DESC)") @RequestParam(required = false, defaultValue = "DESC") String sortDirection) {

        logger.debug("Getting executions for flow group ID: {} with date range {} to {}", flowGroupId, fromDate, toDate);

        try {
            // Check if flow group exists
            flowGroupService.getFlowGroupById(flowGroupId)
                    .orElseThrow(() -> new IllegalArgumentException("FlowGroup not found with ID: " + flowGroupId));

            int pageNumber = page != null ? page : 0;
            int pageSize = size != null ? size : 20;

            Sort sort;
            if (sortBy != null && !sortBy.trim().isEmpty()) {
                Sort.Direction direction = sortDirection != null ?
                    Sort.Direction.fromString(sortDirection) : Sort.Direction.DESC;
                sort = Sort.by(direction, sortBy);
            } else {
                sort = Sort.by(Sort.Direction.DESC, "createdAt");
            }

            Pageable pageable = PageRequest.of(pageNumber, pageSize, sort);
            Page<FlowExecutionDto> executionsPage = flowExecutionService.searchFlowExecutionsAdvanced(
                null, null, null, flowGroupId, null, fromDate, toDate, pageable);

            return ResponseEntity.ok(executionsPage);
        } catch (IllegalArgumentException e) {
            logger.error("Flow group not found: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Error getting flow group executions: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/details")
    @Operation(summary = "Get flow group details with associated flows", description = "Retrieve flow groups with their associated flows aggregated by flow group name, with pagination, filters and query parameters")
    @ApiResponse(responseCode = "200", description = "Flow group details retrieved successfully")
    public ResponseEntity<FlowGroupDetailsDto> getFlowGroupDetails(
            @Parameter(description = "Filter by flow group name (partial match, case-insensitive)") @RequestParam(required = false) String flowGroupName,
            @Parameter(description = "Page number (0-based)") @RequestParam(required = false) Integer page,
            @Parameter(description = "Page size") @RequestParam(required = false) Integer size,
            @Parameter(description = "Sort by field (e.g., 'flowGroupName', 'createdAt', 'updatedAt')") @RequestParam(required = false) String sortBy,
            @Parameter(description = "Sort direction (ASC or DESC)") @RequestParam(required = false, defaultValue = "ASC") String sortDirection) {
        logger.debug("Getting flow group details with filters: flowGroupName={}, page={}, size={}, sortBy={}, sortDirection={}",
                    flowGroupName, page, size, sortBy, sortDirection);

        try {
            FlowGroupDetailsDto details = flowGroupService.getFlowGroupDetails(flowGroupName, page, size, sortBy, sortDirection);
            return ResponseEntity.ok(details);
        } catch (Exception e) {
            logger.error("Error getting flow group details: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
