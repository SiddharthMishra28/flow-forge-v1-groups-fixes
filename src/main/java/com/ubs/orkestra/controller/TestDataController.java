package com.ubs.orkestra.controller;

import com.ubs.orkestra.dto.TestDataDto;
import com.ubs.orkestra.service.TestDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
@RequestMapping("/api/test-data")
@Tag(name = "Test Data", description = "Test Data management operations")
public class TestDataController {

    @Autowired
    private TestDataService testDataService;

    @PostMapping
    @Operation(summary = "Create new test data", description = "Creates a new test data entry with key-value pairs")
    public ResponseEntity<TestDataDto> createTestData(@Valid @RequestBody TestDataDto testDataDto) {
        TestDataDto createdTestData = testDataService.createTestData(testDataDto);
        return new ResponseEntity<>(createdTestData, HttpStatus.CREATED);
    }

    @GetMapping("/{dataId}")
    @Operation(summary = "Get test data by ID", description = "Retrieves test data by its unique identifier")
    public ResponseEntity<TestDataDto> getTestDataById(@PathVariable Long dataId) {
        TestDataDto testData = testDataService.getTestDataById(dataId);
        return ResponseEntity.ok(testData);
    }

    @GetMapping
    @Operation(summary = "Get all test data", description = "Retrieves all test data entries. Supports pagination, sorting, and filtering by application ID.")
    public ResponseEntity<?> getAllTestData(
            @Parameter(description = "Application ID to filter test data") @RequestParam(required = false) Long applicationId,
            @Parameter(description = "Page number (0-based)") @RequestParam(required = false) Integer page,
            @Parameter(description = "Page size") @RequestParam(required = false) Integer size,
            @Parameter(description = "Sort by field (e.g., 'dataId', 'applicationName', 'category', 'createdAt', 'updatedAt')") @RequestParam(required = false) String sortBy,
            @Parameter(description = "Sort direction (ASC or DESC)") @RequestParam(required = false, defaultValue = "ASC") String sortDirection) {
        
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
            Page<TestDataDto> testDataPage = testDataService.getAllTestData(pageable, applicationId);
            
            return ResponseEntity.ok(testDataPage);
        } else {
            // Return all data without pagination (backward compatibility)
            List<TestDataDto> testDataList = testDataService.getAllTestData(applicationId);
            return ResponseEntity.ok(testDataList);
        }
    }

    @PutMapping("/{dataId}")
    @Operation(summary = "Update test data", description = "Updates an existing test data entry")
    public ResponseEntity<TestDataDto> updateTestData(@PathVariable Long dataId, 
                                                     @Valid @RequestBody TestDataDto testDataDto) {
        TestDataDto updatedTestData = testDataService.updateTestData(dataId, testDataDto);
        return ResponseEntity.ok(updatedTestData);
    }

    @DeleteMapping("/{dataId}")
    @Operation(summary = "Delete test data", description = "Deletes a test data entry by its ID. Cannot delete if referenced by FlowSteps.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Test data deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Test data not found"),
            @ApiResponse(responseCode = "409", description = "Cannot delete - TestData is referenced by one or more FlowSteps")
    })
    public ResponseEntity<Void> deleteTestData(@PathVariable Long dataId) {
        testDataService.deleteTestData(dataId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{applicationId}/test-data")
    @Operation(summary = "Get test data by application ID", description = "Retrieves all test data entries for a specific application ID")
    public ResponseEntity<List<TestDataDto>> getTestDataByApplicationId(
            @Parameter(description = "Application ID") @PathVariable Long applicationId) {
        List<TestDataDto> testDataList = testDataService.getTestDataByApplicationId(applicationId);
        return ResponseEntity.ok(testDataList);
    }

    @GetMapping("/search")
    @Operation(summary = "Search test data", description = "Search and filter test data records by ApplicationName, Category, and Description. Supports pagination and sorting.")
    public ResponseEntity<?> searchTestData(
            @Parameter(description = "Filter by application name (partial match, case-insensitive)") @RequestParam(required = false) String applicationName,
            @Parameter(description = "Filter by category (partial match, case-insensitive)") @RequestParam(required = false) String category,
            @Parameter(description = "Filter by description (partial match, case-insensitive)") @RequestParam(required = false) String description,
            @Parameter(description = "Page number (0-based)") @RequestParam(required = false) Integer page,
            @Parameter(description = "Page size") @RequestParam(required = false) Integer size,
            @Parameter(description = "Sort by field (e.g., 'dataId', 'applicationName', 'category', 'createdAt', 'updatedAt')") @RequestParam(required = false) String sortBy,
            @Parameter(description = "Sort direction (ASC or DESC)") @RequestParam(required = false, defaultValue = "ASC") String sortDirection) {

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
            Page<TestDataDto> testDataPage = testDataService.searchTestData(applicationName, category, description, pageable);

            return ResponseEntity.ok(testDataPage);
        } else {
            // Return all data without pagination (backward compatibility)
            List<TestDataDto> testDataList = testDataService.searchTestData(applicationName, category, description);
            return ResponseEntity.ok(testDataList);
        }
    }
}