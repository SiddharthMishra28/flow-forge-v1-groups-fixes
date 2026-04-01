package com.ubs.orkestra.controller;

import com.ubs.orkestra.dto.ApplicationDto;
import com.ubs.orkestra.dto.BranchDto;
import com.ubs.orkestra.dto.ValidationRequestDto;
import com.ubs.orkestra.dto.ValidationResponseDto;
import com.ubs.orkestra.service.ApplicationService;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/applications")
@Tag(name = "Applications", description = "GitLab Application Management API")
public class ApplicationController {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationController.class);

    @Autowired
    private ApplicationService applicationService;

    @PostMapping
    @Operation(summary = "Create a new application", description = "Create a new GitLab application configuration")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Application created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input data"),
            @ApiResponse(responseCode = "409", description = "Application with GitLab project ID already exists")
    })
    public ResponseEntity<ApplicationDto> createApplication(
            @Valid @RequestBody ApplicationDto applicationDto) {
        logger.info("Creating new application for GitLab project: {}", applicationDto.getGitlabProjectId());
        
        ApplicationDto createdApplication = applicationService.createApplication(applicationDto);
        return new ResponseEntity<>(createdApplication, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get application by ID", description = "Retrieve a specific application by its ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Application found"),
            @ApiResponse(responseCode = "404", description = "Application not found")
    })
    public ResponseEntity<ApplicationDto> getApplicationById(
            @Parameter(description = "Application ID") @PathVariable Long id) {
        logger.debug("Fetching application with ID: {}", id);
        
        return applicationService.getApplicationById(id)
                .map(application -> ResponseEntity.ok(application))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    @Operation(summary = "Get all applications", description = "Retrieve all GitLab applications. Supports pagination and sorting.")
    @ApiResponse(responseCode = "200", description = "Applications retrieved successfully")
    public ResponseEntity<?> getAllApplications(
            @Parameter(description = "Page number (0-based)") @RequestParam(required = false) Integer page,
            @Parameter(description = "Page size") @RequestParam(required = false) Integer size,
            @Parameter(description = "Sort by field (e.g., 'id', 'applicationName', 'createdAt', 'updatedAt')") @RequestParam(required = false) String sortBy,
            @Parameter(description = "Sort direction (ASC or DESC)") @RequestParam(required = false, defaultValue = "ASC") String sortDirection) {
        
        logger.debug("Fetching all applications with page: {}, size: {}, sortBy: {}, sortDirection: {}", 
                    page, size, sortBy, sortDirection);
        
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
            Page<ApplicationDto> applicationsPage = applicationService.getAllApplications(pageable);
            
            return ResponseEntity.ok(applicationsPage);
        } else {
            // Return all data without pagination (backward compatibility)
            List<ApplicationDto> applications = applicationService.getAllApplications();
            return ResponseEntity.ok(applications);
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update application", description = "Update an existing application")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Application updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input data"),
            @ApiResponse(responseCode = "404", description = "Application not found"),
            @ApiResponse(responseCode = "409", description = "GitLab project ID already exists")
    })
    public ResponseEntity<ApplicationDto> updateApplication(
            @Parameter(description = "Application ID") @PathVariable Long id,
            @Valid @RequestBody ApplicationDto applicationDto) {
        logger.info("Updating application with ID: {}", id);
        
        ApplicationDto updatedApplication = applicationService.updateApplication(id, applicationDto);
        return ResponseEntity.ok(updatedApplication);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete application", description = "Delete an application by its ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Application deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Application not found")
    })
    public ResponseEntity<Void> deleteApplication(
            @Parameter(description = "Application ID") @PathVariable Long id) {
        logger.info("Deleting application with ID: {}", id);
        
        try {
            applicationService.deleteApplication(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            logger.error("Failed to delete application: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/validate")
    @Operation(summary = "Validate GitLab connection", description = "Validate GitLab connectivity using access token and project ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "GitLab connection validated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input data"),
            @ApiResponse(responseCode = "401", description = "Invalid access token"),
            @ApiResponse(responseCode = "403", description = "Access forbidden - insufficient permissions"),
            @ApiResponse(responseCode = "404", description = "Project not found"),
            @ApiResponse(responseCode = "500", description = "GitLab connection failed")
    })
    public ResponseEntity<ValidationResponseDto> validateGitLabConnection(
            @Valid @RequestBody ValidationRequestDto validationRequest) {
        logger.info("Validating GitLab connection for project: {}", validationRequest.getProjectId());
        
        try {
            ValidationResponseDto response = applicationService.validateGitLabConnection(
                validationRequest.getAccessToken(), 
                validationRequest.getProjectId()
            );
            
            if (response.isValid()) {
                logger.info("GitLab connection validation successful for project: {}", validationRequest.getProjectId());
                return ResponseEntity.ok(response);
            } else {
                logger.warn("GitLab connection validation failed for project: {}", validationRequest.getProjectId());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }
        } catch (Exception e) {
            logger.error("GitLab connection validation error for project {}: {}", 
                        validationRequest.getProjectId(), e.getMessage());
            
            ValidationResponseDto errorResponse = new ValidationResponseDto(false, 
                "GitLab connection failed: " + e.getMessage());
            
            // Determine appropriate HTTP status based on error type
            if (e.getMessage().contains("401") || e.getMessage().contains("Unauthorized")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
            } else if (e.getMessage().contains("403") || e.getMessage().contains("Forbidden")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
            } else if (e.getMessage().contains("404") || e.getMessage().contains("Not Found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
            }
        }
    }

    @GetMapping("/{id}/branches")
    @Operation(summary = "Get application branches", description = "Retrieve all branches from the GitLab repository associated with the application")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Branches retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Application not found"),
            @ApiResponse(responseCode = "401", description = "Invalid access token"),
            @ApiResponse(responseCode = "403", description = "Access forbidden - insufficient permissions"),
            @ApiResponse(responseCode = "500", description = "GitLab connection failed")
    })
    public ResponseEntity<List<BranchDto>> getApplicationBranches(
            @Parameter(description = "Application ID") @PathVariable Long id) {
        logger.info("Fetching branches for application with ID: {}", id);
        
        try {
            List<BranchDto> branches = applicationService.getApplicationBranches(id);
            logger.info("Successfully retrieved {} branches for application ID: {}", branches.size(), id);
            return ResponseEntity.ok(branches);
        } catch (IllegalArgumentException e) {
            logger.error("Application not found: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Failed to fetch branches for application ID {}: {}", id, e.getMessage());
            
            // Determine appropriate HTTP status based on error type
            if (e.getMessage().contains("401") || e.getMessage().contains("Invalid access token")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            } else if (e.getMessage().contains("403") || e.getMessage().contains("Access forbidden")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            } else if (e.getMessage().contains("404") || e.getMessage().contains("not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        }
    }

    @PostMapping("/{id}/webhooks/register")
    @Operation(summary = "Register webhook for application", description = "Manually register a GitLab webhook for this application. Requires auto-registration to be enabled in configuration.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Webhook registered successfully"),
            @ApiResponse(responseCode = "404", description = "Application not found"),
            @ApiResponse(responseCode = "500", description = "Failed to register webhook")
    })
    public ResponseEntity<?> registerWebhook(
            @Parameter(description = "Application ID") @PathVariable Long id) {
        logger.info("Manually registering webhook for application with ID: {}", id);

        try {
            applicationService.registerWebhookForApplication(id);
            
            ApplicationDto application = applicationService.getApplicationById(id)
                .orElseThrow(() -> new IllegalArgumentException("Application not found with ID: " + id));

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Webhook registered successfully",
                "webhookId", application.getWebhookId()
            ));
        } catch (IllegalArgumentException e) {
            logger.error("Application not found: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Failed to register webhook for application ID {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}/webhooks")
    @Operation(summary = "Delete webhook for application", description = "Delete the GitLab webhook associated with this application")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Webhook deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Application not found"),
            @ApiResponse(responseCode = "500", description = "Failed to delete webhook")
    })
    public ResponseEntity<?> deleteWebhook(
            @Parameter(description = "Application ID") @PathVariable Long id) {
        logger.info("Deleting webhook for application with ID: {}", id);

        try {
            ApplicationDto application = applicationService.getApplicationById(id)
                .orElseThrow(() -> new IllegalArgumentException("Application not found with ID: " + id));

            if (application.getWebhookId() == null) {
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "No webhook registered for this application"
                ));
            }

            // The webhook will be deleted when the application is deleted
            // For manual deletion, users can use the GitLab API directly
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Webhook deletion requires GitLab API access. Use GitLab UI or API directly.",
                "webhookId", application.getWebhookId(),
                "gitlabWebhookUrl", String.format("%s/-/hooks", application.getProjectUrl())
            ));
        } catch (IllegalArgumentException e) {
            logger.error("Application not found: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Failed to delete webhook for application ID {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/{id}/webhooks/status")
    @Operation(summary = "Get webhook status for application", description = "Get the current webhook configuration status for this application")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Webhook status retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Application not found")
    })
    public ResponseEntity<?> getWebhookStatus(
            @Parameter(description = "Application ID") @PathVariable Long id) {
        logger.info("Getting webhook status for application with ID: {}", id);

        try {
            ApplicationDto application = applicationService.getApplicationById(id)
                .orElseThrow(() -> new IllegalArgumentException("Application not found with ID: " + id));

            Map<String, Object> response = new HashMap<>();
            response.put("applicationId", application.getId());
            response.put("gitlabProjectId", application.getGitlabProjectId());
            response.put("webhookRegistered", application.getWebhookId() != null);
            response.put("webhookId", application.getWebhookId());
            response.put("autoRegisterEnabled", true); // This would come from config

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.error("Application not found: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
}